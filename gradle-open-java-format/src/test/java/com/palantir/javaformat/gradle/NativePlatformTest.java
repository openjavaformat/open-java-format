/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.javaformat.gradle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.javaformat.gradle.NativePlatform.Architecture;
import com.palantir.javaformat.gradle.NativePlatform.OperatingSystem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class NativePlatformTest {

    private static final String GLIBC = "ldd (Ubuntu GLIBC 2.39-0ubuntu8) 2.39";

    // The classifiers open-java-format-native has on Maven Central, and the names buildSrc's
    // open-java-format.native-platform gives the images it builds.
    @CsvSource(
            delimiter = '|',
            value = {
                "Linux      | aarch64 | ldd (GNU libc) 2.41                   | nativeImage-linux-glibc_aarch64 | bin",
                "Linux      | amd64   | ldd (Ubuntu GLIBC 2.39-0ubuntu8) 2.39 | nativeImage-linux-glibc_x86-64  | bin",
                "Mac OS X   | aarch64 |                                       | nativeImage-macos_aarch64       | bin",
                "Mac OS X   | x86_64  |                                       | nativeImage-macos_x86-64        | bin",
                "Windows 11 | amd64   |                                       | nativeImage-windows_x86-64      | exe"
            })
    @ParameterizedTest
    void names_each_published_image(
            String osName, String osArch, String lddVersion, String classifier, String extension) {
        NativePlatform platform = NativePlatform.of(osName, osArch, lddOnLinuxOnly(lddVersion));

        assertThat(platform.isPublished()).isTrue();
        assertThat(platform.classifier()).isEqualTo(classifier);
        assertThat(platform.extension()).isEqualTo(extension);
    }

    @CsvSource(
            delimiter = '|',
            value = {
                "Linux      | i686    | ldd (Ubuntu GLIBC 2.39-0ubuntu8) 2.39 | nativeImage-linux-glibc_x86",
                "Linux      | x86_64  | musl libc (x86_64)                    | nativeImage-linux-musl_x86-64",
                "Windows 11 | aarch64 |                                       | nativeImage-windows_aarch64"
            })
    @ParameterizedTest
    void knows_there_is_no_image_for_other_platforms(
            String osName, String osArch, String lddVersion, String classifier) {
        NativePlatform platform = NativePlatform.of(osName, osArch, lddOnLinuxOnly(lddVersion));

        assertThat(platform.isPublished()).isFalse();
        assertThat(platform.classifier()).isEqualTo(classifier);
    }

    @Test
    void tells_glibc_from_musl_with_ldd() {
        assertThat(NativePlatform.of("Linux", "amd64", () -> GLIBC).operatingSystem())
                .isEqualTo(OperatingSystem.LINUX_GLIBC);
        assertThat(NativePlatform.of("Linux", "amd64", () -> "ldd (GNU libc) 2.41")
                        .operatingSystem())
                .isEqualTo(OperatingSystem.LINUX_GLIBC);
        // musl's ldd prints its version to stderr, after an empty stdout.
        assertThat(NativePlatform.of("Linux", "amd64", () -> "musl libc (x86_64)\nVersion 1.2.5\n")
                        .operatingSystem())
                .isEqualTo(OperatingSystem.LINUX_MUSL);
    }

    @CsvSource({
        "aarch64, AARCH64",
        "AMD64,   X86_64",
        "amd64,   X86_64",
        "arm,     AARCH64",
        "arm64,   AARCH64",
        "i686,    X86",
        "x64,     X86_64",
        "x86,     X86",
        "x86_64,  X86_64"
    })
    @ParameterizedTest
    void reads_every_spelling_of_os_arch(String osArch, Architecture architecture) {
        assertThat(NativePlatform.of("Mac OS X", osArch, NativePlatformTest::noLdd)
                        .architecture())
                .isEqualTo(architecture);
    }

    @Test
    void refuses_what_it_cannot_name() {
        assertThatThrownBy(() -> NativePlatform.of("FreeBSD", "amd64", NativePlatformTest::noLdd))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("FreeBSD");
        assertThatThrownBy(() -> NativePlatform.of("Linux", "riscv64", () -> GLIBC))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("riscv64");
        assertThatThrownBy(() -> NativePlatform.of("Linux", "amd64", () -> "ldd: unrecognized option '--version'"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Cannot tell glibc from musl");
    }

    // On Linux this runs the real ldd through Gradle's ProviderFactory, which CI's ubuntu jobs exercise.
    @Test
    void reads_the_platform_of_the_jvm_running_gradle(@TempDir Path projectDir) {
        Project project =
                ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        assertThat(NativePlatform.current(project.getProviders()).get())
                .isEqualTo(NativePlatform.of(
                        System.getProperty("os.name"), System.getProperty("os.arch"), NativePlatformTest::runLdd));
    }

    /** The table's ldd output, or for an empty cell, an ldd that must not run: the platform is not Linux. */
    private static Supplier<String> lddOnLinuxOnly(String lddVersion) {
        return lddVersion == null ? NativePlatformTest::noLdd : () -> lddVersion;
    }

    private static String noLdd() {
        throw new AssertionError("ldd must only run on Linux");
    }

    private static String runLdd() {
        try {
            Process process = new ProcessBuilder("ldd", "--version")
                    .redirectErrorStream(true)
                    .start();
            try (InputStream output = process.getInputStream()) {
                return new String(output.readAllBytes(), UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
