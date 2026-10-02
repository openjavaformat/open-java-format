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

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOutput;

/**
 * A platform in the names open-java-format-native publishes its images under: the classifier
 * {@code nativeImage-<os>_<arch>}. buildSrc's open-java-format.native-platform names the images the build
 * produces the same way, and NativePlatformTest pins the names of the published ones.
 */
record NativePlatform(OperatingSystem operatingSystem, Architecture architecture) {

    enum OperatingSystem {
        MACOS,
        LINUX_GLIBC,
        LINUX_MUSL,
        WINDOWS
    }

    enum Architecture {
        X86,
        X86_64,
        AARCH64
    }

    /**
     * The platforms a native image is published for, and therefore the only ones where it can be resolved.
     * macOS has an image for both architectures: the x86-64 one used to be missing because nobody built it,
     * and .github/workflows/ci.yml now does. Windows has one for x86-64 only, and musl none: no job produces
     * them.
     */
    private static final Set<NativePlatform> PUBLISHED = Set.of(
            new NativePlatform(OperatingSystem.LINUX_GLIBC, Architecture.X86_64),
            new NativePlatform(OperatingSystem.LINUX_GLIBC, Architecture.AARCH64),
            new NativePlatform(OperatingSystem.MACOS, Architecture.X86_64),
            new NativePlatform(OperatingSystem.MACOS, Architecture.AARCH64),
            new NativePlatform(OperatingSystem.WINDOWS, Architecture.X86_64));

    /** The platform of the JVM that runs Gradle. */
    static Provider<NativePlatform> current(ProviderFactory providers) {
        ExecOutput ldd = providers.exec(spec -> {
            spec.commandLine("ldd", "--version");
            // musl's ldd prints its version to stderr and exits with 1.
            spec.setIgnoreExitValue(true);
        });
        Supplier<String> lddVersion = () -> ldd.getStandardOutput().getAsText().get()
                + ldd.getStandardError().getAsText().get();
        return providers
                .systemProperty("os.name")
                .zip(providers.systemProperty("os.arch"), (osName, osArch) -> of(osName, osArch, lddVersion));
    }

    /** The platform for the given os.name and os.arch; lddVersion is asked only on Linux. */
    static NativePlatform of(String osName, String osArch, Supplier<String> lddVersion) {
        return new NativePlatform(operatingSystem(osName, lddVersion), architecture(osArch));
    }

    boolean isPublished() {
        return PUBLISHED.contains(this);
    }

    String classifier() {
        return "nativeImage-" + uiName(operatingSystem) + "_" + uiName(architecture);
    }

    /** native-image names the Windows binary .exe, and the image is published with that extension. */
    String extension() {
        return operatingSystem == OperatingSystem.WINDOWS ? "exe" : "bin";
    }

    private static OperatingSystem operatingSystem(String osName, Supplier<String> lddVersion) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("mac")) {
            return OperatingSystem.MACOS;
        }
        if (name.startsWith("windows")) {
            return OperatingSystem.WINDOWS;
        }
        if (name.startsWith("linux")) {
            // A glibc binary does not run on musl, and ldd is what tells the two apart.
            String libc = lddVersion.get().toLowerCase(Locale.ROOT);
            if (libc.contains("glibc") || libc.contains("gnu libc")) {
                return OperatingSystem.LINUX_GLIBC;
            }
            if (libc.contains("musl")) {
                return OperatingSystem.LINUX_MUSL;
            }
            throw new UnsupportedOperationException("Cannot tell glibc from musl: ldd --version printed " + libc);
        }
        throw new UnsupportedOperationException("No native image name for the operating system " + osName);
    }

    private static Architecture architecture(String osArch) {
        return switch (osArch.toLowerCase(Locale.ROOT)) {
            case "x86_64", "x64", "amd64" -> Architecture.X86_64;
            case "arm", "arm64", "aarch64" -> Architecture.AARCH64;
            case "x86", "i686" -> Architecture.X86;
            default -> throw new UnsupportedOperationException("No native image name for the architecture " + osArch);
        };
    }

    private static String uiName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
