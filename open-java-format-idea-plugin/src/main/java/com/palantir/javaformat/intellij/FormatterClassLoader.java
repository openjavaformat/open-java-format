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

import com.palantir.javaformat.java.FormatterDiagnostic;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.FormatterService;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.palantir.javaformat.java.Replacement;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * Loads the formatter from its jars into the IDE's JVM, apart from the IDE.
 *
 * <p>Its parent is the JDK's platform class loader, which finds the JDK, javac included, and nothing of the IDE: the
 * formatter's jars are everything it runs with in a JVM of its own, and the IDE has other versions of the same
 * libraries. Only the types the plugin and the formatter exchange come from the plugin: the service interface with
 * the types it names, and Guava, whose {@code ImmutableList} and {@code Range} are in its signatures. The copies of
 * those in the formatter's jars are never loaded.
 */
final class FormatterClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    /** The classes of open-java-format-spi. They share a package with the formatter's own, so they go by name. */
    private static final Set<String> SPI = Set.of(
            FormatterDiagnostic.class.getName(),
            FormatterException.class.getName(),
            FormatterService.class.getName(),
            JavaFormatterOptions.class.getName(),
            Replacement.class.getName());

    private final ClassLoader plugin;

    FormatterClassLoader(URL[] formatterJars, ClassLoader plugin) {
        super("open-java-format", formatterJars, ClassLoader.getPlatformClassLoader());
        this.plugin = plugin;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return isShared(name) ? plugin.loadClass(name) : super.loadClass(name, resolve);
    }

    static boolean isShared(String className) {
        if (className.startsWith("com.google.common.")) {
            return true;
        }
        var nested = className.indexOf('$');
        return SPI.contains(nested < 0 ? className : className.substring(0, nested));
    }
}
