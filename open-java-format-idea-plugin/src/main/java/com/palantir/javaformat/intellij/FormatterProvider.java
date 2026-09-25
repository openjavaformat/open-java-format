/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.javaformat.intellij;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.formatting.service.FormattingService;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.palantir.javaformat.bootstrap.BootstrappingFormatterService;
import com.palantir.javaformat.bootstrap.NativeImageFormatterService;
import com.palantir.javaformat.java.FormatterService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FormatterProvider {
    private static final Logger log = LoggerFactory.getLogger(FormatterProvider.class);

    // Cache to avoid resolving the formatter every time we want to format from IntelliJ
    private final LoadingCache<FormatterCacheKey, Optional<FormatterService>> implementationCache =
            Caffeine.newBuilder().maximumSize(1).build(FormatterProvider::createFormatter);

    /**
     * The descriptor of this plugin: where its version and the directory of the bundled formatter come from. The
     * platform hands it to the formatting service when it creates that service from plugin.xml (PluginAware), and the
     * extension point finds the service by class. Every way of looking a plugin up by id or by class became
     * {@code @ApiStatus.Internal} in 2026.2; PluginAware and the extension point are public API in every supported IDE.
     */
    static PluginDescriptor getPluginDescriptor() {
        return FormattingService.EP_NAME
                .findExtensionOrFail(PalantirJavaFormatFormattingService.class)
                .getPluginDescriptor();
    }

    Optional<FormatterService> get(Project project, PalantirJavaFormatSettings settings) {
        return implementationCache.get(new FormatterCacheKey(
                project,
                settings.getImplementationClassPath(),
                settings.getNativeImageClassPath(),
                settings.injectedVersionIsOutdated()));
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    private static Optional<FormatterService> createFormatter(FormatterCacheKey cacheKey) {
        if (cacheKey.nativeImageClassPath.isPresent()) {
            log.info("Using the native formatter with classpath: {}", cacheKey.nativeImageClassPath.get());
            return Optional.of(new NativeImageFormatterService(Path.of(cacheKey.nativeImageClassPath.get())));
        }

        // The formatter runs in a JVM of its own, started with the "--add-exports" it needs to reach javac.
        // That JVM is the IDE's own runtime, never the project SDK: every IDE this plugin supports runs on Java 21
        // or later, which the formatter needs, while a project SDK can be any version.
        int jdkMajorVersion = Runtime.version().feature();
        Path jdkPath = Path.of(System.getProperty("java.home"), "bin", SystemInfo.isWindows ? "java.exe" : "java");
        List<Path> implementationClasspath =
                getImplementationUrls(cacheKey.implementationClassPath, cacheKey.useBundledImplementation);
        log.info("Using bootstrapping formatter with jdk version {} and path: {}", jdkMajorVersion, jdkPath);
        return Optional.of(new BootstrappingFormatterService(jdkPath, jdkMajorVersion, implementationClasspath));
    }

    private static List<Path> getProvidedImplementationUrls(List<URI> implementationClasspath) {
        return implementationClasspath.stream().map(Path::of).collect(Collectors.toList());
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    private static List<Path> getBundledImplementationUrls() {
        // Load from the jars bundled with the plugin.
        PluginDescriptor ourPlugin = getPluginDescriptor();
        Path implDir = ourPlugin.getPluginPath().resolve("impl");
        log.debug("Using open-java-format implementation bundled with plugin: {}", implDir);
        return listDirAsUrlsUnchecked(implDir);
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    private static List<Path> getImplementationUrls(
            Optional<List<URI>> implementationClassPath, boolean useBundledImplementation) {
        if (useBundledImplementation) {
            log.debug("Using open-java-format implementation bundled with plugin");
            return getBundledImplementationUrls();
        }
        return implementationClassPath
                .map(classpath -> {
                    log.debug("Using open-java-format implementation defined by URIs: {}", classpath);
                    return getProvidedImplementationUrls(classpath);
                })
                .orElseGet(() -> {
                    log.debug("Using open-java-format implementation bundled with plugin");
                    return getBundledImplementationUrls();
                });
    }

    private static List<Path> listDirAsUrlsUnchecked(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't list dir: " + dir, e);
        }
    }

    private static final class FormatterCacheKey {
        private final Project project;
        private final Optional<List<URI>> implementationClassPath;
        private final Optional<URI> nativeImageClassPath;
        private final boolean useBundledImplementation;

        FormatterCacheKey(
                Project project,
                Optional<List<URI>> implementationClassPath,
                Optional<URI> nativeImageClassPath,
                boolean useBundledImplementation) {
            this.project = project;
            this.implementationClassPath = implementationClassPath;
            this.nativeImageClassPath = nativeImageClassPath;
            this.useBundledImplementation = useBundledImplementation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FormatterCacheKey that = (FormatterCacheKey) o;
            return useBundledImplementation == that.useBundledImplementation
                    && Objects.equals(project, that.project)
                    && Objects.equals(implementationClassPath, that.implementationClassPath)
                    && Objects.equals(nativeImageClassPath, that.nativeImageClassPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(project, implementationClassPath, nativeImageClassPath, useBundledImplementation);
        }
    }
}
