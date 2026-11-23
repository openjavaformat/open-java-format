/*
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
import com.palantir.javaformat.java.FormatterService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * The versions of the formatters on class paths, such as the one a build writes to {@code .idea/open-java-format.xml}:
 * what the manifest of the jar that provides the formatter says.
 *
 * <p>Every file formatted asks, and the jars are read once for as long as they stay the same: a jar rebuilt in place
 * is noticed, as {@link InProcessFormatters} notices it, without opening the jars for every file.
 */
final class FormatterVersions {

    /** The versions of the IDE's projects. */
    static final FormatterVersions IDE = new FormatterVersions();

    // The class paths of the projects open at once, as InProcessFormatters keeps their formatters.
    private static final int KEPT = 4;

    private final LoadingCache<List<Jar>, Optional<String>> versions = Caffeine.newBuilder()
            .maximumSize(KEPT)
            .build(jars -> read(jars.stream().map(Jar::path).toList()));

    /**
     * The Implementation-Version of the first of these jars that provides the formatter, if its manifest has one.
     *
     * @throws IllegalStateException when none of them provides the formatter
     * @throws UncheckedIOException when one before it cannot be read
     */
    Optional<String> of(List<Path> classPath) {
        return versions.get(classPath.stream().map(Jar::of).toList());
    }

    private static Optional<String> read(List<Path> classPath) {
        for (var path : classPath) {
            // Closed at once: on Windows an open jar is one that nobody can replace or delete, a Gradle build included.
            try (var jar = new JarFile(path.toFile())) {
                // Identify the implementation jar by the service it produces.
                if (jar.getEntry("META-INF/services/" + FormatterService.class.getName()) != null) {
                    return Optional.ofNullable(jar.getManifest())
                            .map(manifest ->
                                    manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + path, e);
            }
        }
        throw new IllegalStateException("No jar provides the formatter in " + classPath);
    }
}
