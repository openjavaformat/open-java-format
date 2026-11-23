/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Splitter;
import com.palantir.javaformat.bootstrap.BootstrappingFormatterService;
import com.palantir.javaformat.bootstrap.NativeImageFormatterService;
import com.palantir.javaformat.java.FormatterService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs formatDiff through git with each formatter, the native binary among them, so it runs only in CI's native jobs.
 * {@link FormatChangedLinesTest} formats a diff written out by hand, in every build.
 */
final class FormatDiffTest {

    private static final File CLASSPATH_FILE = new File("build/impl.classpath");
    private static final File NATIVE_IMAGE_FILE = new File("build/nativeImage.path");

    @TempDir
    Path repo;

    @ParameterizedTest
    @MethodSource("getFormatters")
    void reformat_a_subpath_of_a_git_directory_for_only_changed_lines(FormatterService formatterService)
            throws IOException, InterruptedException {
        initRepo();

        Path subdir = repo.resolve("subdir");
        Files.createDirectories(subdir);

        Path reformatMe = subdir.resolve("ReformatMe.java");
        Files.write(reformatMe, List.of("                                 class ReformatMe {}"), UTF_8);

        Path dontTouchMe = repo.resolve("DontTouchMe.java");
        Files.write(dontTouchMe, List.of("                                 class DontTouchMe {}"), UTF_8);

        runCommandInRepo("git", "add", "-N", ".");

        FormatDiff.formatDiff(subdir, formatterService);

        assertThat(reformatMe).hasContent("class ReformatMe {}");
        assertThat(dontTouchMe).hasContent("                                 class DontTouchMe {}");
    }

    private void initRepo() throws IOException, InterruptedException {
        runCommandInRepo("git", "init");
        runCommandInRepo("git", "config", "user.name", "Test User");
        runCommandInRepo("git", "config", "user.email", "test-user@palantir.com");
        runCommandInRepo("git", "config", "commit.gpgsign", "false");
        runCommandInRepo("git", "commit", "--allow-empty", "-m", "Init");
    }

    private void runCommandInRepo(String... args) throws IOException, InterruptedException {
        Process process =
                new ProcessBuilder().command(args).directory(repo.toFile()).start();

        checkState(process.waitFor(10, TimeUnit.SECONDS), "git diff took too long to terminate");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        process.getErrorStream().transferTo(baos);
        String stderr = baos.toString(UTF_8);

        checkState(process.exitValue() == 0, "Expected return code of 0: %s", stderr);
    }

    private static Stream<FormatterService> getFormatters() throws IOException {
        return Stream.of(
                // In this JVM, as formatDiff runs it without the native binary.
                FormatterServiceLoader.load(
                        getClasspath().stream().map(Path::toFile).toList()),
                new BootstrappingFormatterService(
                        javaBinPath(), Runtime.version().feature(), getClasspath()),
                new NativeImageFormatterService(
                        Path.of(Files.readString(NATIVE_IMAGE_FILE.toPath()).trim())));
    }

    private static List<Path> getClasspath() throws IOException {
        return Splitter.on(File.pathSeparatorChar)
                .trimResults()
                .omitEmptyStrings()
                .splitToStream(Files.readString(CLASSPATH_FILE.toPath()))
                .map(Path::of)
                .toList();
    }

    private static Path javaBinPath() {
        String javaHome = checkNotNull(System.getProperty("java.home"), "java.home property not set");
        return Path.of(javaHome).resolve("bin").resolve("java" + (isWindows() ? ".exe" : ""));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
