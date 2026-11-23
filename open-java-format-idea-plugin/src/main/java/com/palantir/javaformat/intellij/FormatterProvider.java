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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.palantir.javaformat.bootstrap.BootstrappingFormatterService;
import com.palantir.javaformat.bootstrap.NativeImageFormatterService;
import com.palantir.javaformat.java.FormatterService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

final class FormatterProvider {
    private final Logger log = Logger.getInstance(FormatterProvider.class);

    // Cache to avoid resolving the formatter every time we want to format from IntelliJ
    private final LoadingCache<FormatterCacheKey, Optional<FormatterService>> implementationCache =
            Caffeine.newBuilder().maximumSize(1).build(this::createFormatter);

    private final Supplier<Optional<Path>> embeddedNativeImage;
    private final Supplier<Optional<String>> inProcessUnavailable;

    /**
     * embeddedNativeImage: the downloaded native binary, asked again on every format since a download can add it.
     * inProcessUnavailable: why the formatter cannot run in the IDE's own JVM, if it cannot.
     */
    FormatterProvider(Supplier<Optional<Path>> embeddedNativeImage, Supplier<Optional<String>> inProcessUnavailable) {
        this.embeddedNativeImage = embeddedNativeImage;
        this.inProcessUnavailable = inProcessUnavailable;
    }

    Optional<FormatterService> get(Project project, PalantirJavaFormatSettings settings) {
        // The selection, not the settings, is in the key: the formatter has to change when the binary appears, or
        // the project SDK changes.
        return implementationCache.get(new FormatterCacheKey(
                project,
                settings.getImplementationClassPath(),
                settings.injectedVersionIsOutdated(),
                FormatterSelection.of(
                        settings, FormatterSelection.in(project, inProcessUnavailable, embeddedNativeImage))));
    }

    private Optional<FormatterService> createFormatter(FormatterCacheKey cacheKey) {
        return Optional.of(
                switch (cacheKey.selection()) {
                    case FormatterSelection.NativeImage nativeImage -> {
                        log.info("open-java-format: using native binary, "
                                + (nativeImage.embedded() ? "embedded: " : "custom: ") + nativeImage.path());
                        yield new NativeImageFormatterService(nativeImage.path());
                    }
                    case FormatterSelection.InProcess inProcess -> inProcess(cacheKey);
                    case FormatterSelection.SeparateJvm separateJvm -> {
                        separateJvm.fallbacks().forEach(fallback -> log.warn(fallbackMessage(fallback)));
                        yield separateJvm(separateJvm.java(), separateJvm.javaVersion(), cacheKey);
                    }
                });
    }

    private FormatterService inProcess(FormatterCacheKey cacheKey) {
        log.info("open-java-format: using in-process formatter (Java " + System.getProperty("java.version") + ") with "
                + classpathName(cacheKey) + " classpath");
        // Loaded with the first file, in the background, and not here: this can be the UI thread. What cannot be
        // formatted inside the IDE goes to the JVM that always runs.
        var ideRuntime = FormatterJdks.ideRuntime();
        var classpath = implementationClasspath(cacheKey);
        return new InProcessFormatterService(
                InProcessFormatters.IDE,
                classpath,
                new BootstrappingFormatterService(ideRuntime.java(), ideRuntime.javaVersion(), classpath),
                this::couldNotFormatInProcess);
    }

    private void couldNotFormatInProcess(Throwable failure) {
        var message = "open-java-format: cannot format inside the IDE, formatting in a JVM of its own";
        if (InProcessFormatters.IDE.unavailable().isPresent()) {
            // The formatter does not load or link in this IDE, which it should: an error, so that it goes to the
            // plugin's error reports. Once, since from now on every file goes to a JVM of its own.
            log.error(message, failure);
        } else {
            log.warn(message, failure);
        }
    }

    // The JVM gets the "--add-exports" it needs to reach javac. By default it is the IDE's own runtime rather than
    // the project SDK: every IDE this plugin supports runs on Java 21 or later, which the formatter needs, while a
    // project SDK can be any version.
    private FormatterService separateJvm(Path java, int javaVersion, FormatterCacheKey cacheKey) {
        log.info("open-java-format: using Java-based formatter on " + java + " (Java " + javaVersion + ") with "
                + classpathName(cacheKey) + " classpath");
        return new BootstrappingFormatterService(java, javaVersion, implementationClasspath(cacheKey));
    }

    private static String fallbackMessage(FormatterSelection.Fallback fallback) {
        return "open-java-format: "
                + switch (fallback) {
                    case FormatterSelection.Fallback.InProcessUnavailable unavailable ->
                        "cannot format inside the IDE (" + unavailable.reason() + "), formatting in a JVM of its own";
                    case FormatterSelection.Fallback.NativeImageMissing missing ->
                        "the native binary is switched on but not downloaded, formatting with the Java-based"
                                + " formatter";
                    case FormatterSelection.Fallback.JdkUnusable unusable ->
                        "cannot run the formatter on the JDK of the settings (" + unusable.reason()
                                + "), formatting on the IDE's runtime";
                };
    }

    private List<Path> implementationClasspath(FormatterCacheKey cacheKey) {
        return getImplementationUrls(cacheKey.implementationClassPath(), cacheKey.useBundledImplementation());
    }

    private static String classpathName(FormatterCacheKey cacheKey) {
        return cacheKey.useBundledImplementation()
                        || cacheKey.implementationClassPath().isEmpty()
                ? "bundled"
                : "build";
    }

    private static List<Path> getProvidedImplementationUrls(List<URI> implementationClasspath) {
        return implementationClasspath.stream().map(Path::of).toList();
    }

    /** The jars of the formatter bundled with the plugin, in the directory impl next to the plugin's own lib. */
    static List<Path> getBundledImplementationUrls() {
        return listDirAsUrlsUnchecked(bundledImplementationDir());
    }

    private static Path bundledImplementationDir() {
        return ofNullable(FormattingService.EP_NAME.findExtension(PalantirJavaFormatFormattingService.class))
                .flatMap(PalantirJavaFormatFormattingService::getPluginDescriptor)
                .map(PluginDescriptor::getPluginPath)
                .orElseThrow(() -> new NoSuchElementException("The platform has not set the plugin descriptor"))
                .resolve("impl");
    }

    private List<Path> getImplementationUrls(
            Optional<List<URI>> implementationClassPath, boolean useBundledImplementation) {
        if (useBundledImplementation || implementationClassPath.isEmpty()) {
            var implDir = bundledImplementationDir();
            log.debug("Using open-java-format implementation bundled with plugin: " + implDir);
            return listDirAsUrlsUnchecked(implDir);
        }
        var classpath = implementationClassPath.orElseThrow();
        log.debug("Using open-java-format implementation defined by URIs: " + classpath);
        return getProvidedImplementationUrls(classpath);
    }

    private static List<Path> listDirAsUrlsUnchecked(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't list dir: " + dir, e);
        }
    }

    private record FormatterCacheKey(
            Project project,
            Optional<List<URI>> implementationClassPath,
            boolean useBundledImplementation,
            FormatterSelection selection) {}
}
