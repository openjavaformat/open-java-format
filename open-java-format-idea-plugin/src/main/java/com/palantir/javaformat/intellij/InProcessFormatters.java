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

import com.palantir.javaformat.java.FormatterService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * The formatters loaded into a JVM, which in the IDE is the IDE's own: each from its jars, by a
 * {@link FormatterClassLoader}.
 *
 * <p>A formatter stays loaded for as long as its jars stay the same, for every project that formats with them: the
 * first file pays for loading it and for the JIT compiler, and the files after it for neither.
 */
final class InProcessFormatters {

    /** The formatters of the IDE. */
    static final InProcessFormatters IDE = new InProcessFormatters();

    // The formatter versions of the projects open at once. One that falls out is left to the garbage collector rather
    // than closed: a file can still be formatting in it.
    private static final int KEPT = 4;

    private final Map<List<Jar>, FormatterService> loaded = new LinkedHashMap<>(KEPT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<Jar>, FormatterService> eldest) {
            return size() > KEPT;
        }
    };

    // Why this JVM gave up on running the formatter, which it does for as long as it runs. A formatter that does not
    // load or link here would fail on every file, and a JVM of its own formats them all.
    private final AtomicReference<@Nullable String> failure = new AtomicReference<>();

    /**
     * Why the formatter cannot run in this JVM, if it cannot: javac is out of reach, or a formatter failed to load or
     * to link here. A clause to put after "Not available:".
     */
    Optional<String> unavailable() {
        var failed = failure.get();
        return failed != null ? Optional.of(failed) : JavacAccess.unavailable();
    }

    /**
     * The formatter in these jars: the one loaded before if they have not changed since, a new one otherwise. Cheap
     * enough to ask for every file, which is how a jar rebuilt in place is noticed.
     *
     * @throws IllegalStateException when the formatter cannot be loaded; {@link #unavailable} says so from then on
     */
    synchronized FormatterService load(List<Path> jars) {
        var key = jars.stream().map(Jar::of).toList();
        var formatter = loaded.get(key);
        if (formatter == null) {
            formatter = loadUncached(jars);
            loaded.put(key, formatter);
        }
        return formatter;
    }

    /** A formatter that was loaded met a class it cannot link with: its own missing, or the plugin's of another kind. */
    void failedToLink(LinkageError error) {
        failure.compareAndSet(null, "the formatter does not link inside the IDE (" + error + ")");
    }

    private FormatterService loadUncached(List<Path> jars) {
        var classLoader = new FormatterClassLoader(
                jars.stream().map(InProcessFormatters::toUrl).toArray(URL[]::new),
                FormatterService.class.getClassLoader());
        try {
            JavacAccess.exportTo(classLoader);
            var formatters = ServiceLoader.load(FormatterService.class, classLoader).stream()
                    .toList();
            if (formatters.size() != 1) {
                throw new IllegalStateException("Expected one formatter, found " + formatters.size() + " in " + jars);
            }
            return formatters.get(0).get();
        } catch (RuntimeException | ServiceConfigurationError | LinkageError e) {
            failure.compareAndSet(
                    null,
                    e instanceof JavacAccess.Refused
                            ? e.getMessage()
                            : "the formatter does not load inside the IDE (" + e + ")");
            try {
                // Nothing runs in it yet, and a jar left open is one that Windows lets nobody replace.
                classLoader.close();
            } catch (IOException closing) {
                e.addSuppressed(closing);
            }
            throw new IllegalStateException("Cannot load the formatter from " + jars, e);
        }
    }

    private static URL toUrl(Path jar) {
        try {
            return jar.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException("Cannot turn " + jar + " into a URL", e);
        }
    }

    /** A jar as it was when the formatter was loaded from it: rebuilt in place, it changes size or time. */
    private record Jar(Path path, long length, long lastModified) {
        static Jar of(Path path) {
            var file = path.toFile();
            return new Jar(path, file.length(), file.lastModified());
        }
    }
}
