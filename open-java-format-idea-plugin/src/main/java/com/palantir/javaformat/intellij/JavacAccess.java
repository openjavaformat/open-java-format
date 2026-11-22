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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Whether the formatter can read javac's internals from the IDE's own JVM, and the way to let it.
 *
 * <p>The formatter parses with javac's own classes, which module jdk.compiler exports to nobody. A JVM started for
 * the formatter gets {@code --add-exports} flags; the IDE's JVM is already running, and its users should not have to
 * edit its VM options. But the IDE starts with {@code --add-opens=java.base/java.lang=ALL-UNNAMED}, and with java.lang
 * open a plugin can call {@code Module.implAddExports}, which is what {@code --add-exports} calls at startup: here
 * for the formatter's class loader alone, so that nothing else in the IDE reaches javac through it.
 *
 * <p>{@code implAddExports} is not public API. An IDE that stops opening java.lang, or a JDK without the method, makes
 * {@link #unavailable} say so, and the formatter then runs in a JVM of its own.
 */
final class JavacAccess {

    /** The javac packages the formatter reads: the ones its own JVM is started with the exports of. */
    static final List<String> PACKAGES = List.of(
            "com.sun.tools.javac.api",
            "com.sun.tools.javac.file",
            "com.sun.tools.javac.parser",
            "com.sun.tools.javac.tree",
            "com.sun.tools.javac.util");

    private static final String NO_JDK_COMPILER = "the IDE's runtime has no jdk.compiler module";
    private static final String JAVA_LANG_NOT_OPEN =
            "this IDE does not open java.lang to plugins, so javac cannot be reached";

    private JavacAccess() {}

    /** The JVM would not export javac to the formatter. Its message is a clause, as {@link #unavailable} gives. */
    static final class Refused extends RuntimeException {
        Refused(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Why the formatter cannot reach javac in this JVM, if it cannot: a clause to put after "Not available:". It
     * depends on how the JVM was started alone, so it is decided once, though asked for every file formatted.
     */
    static Optional<String> unavailable() {
        return AsStarted.UNAVAILABLE;
    }

    /**
     * Exports javac's packages to the formatter's class loader, unless the JVM was started with them exported.
     *
     * @throws Refused when the JVM does not let it
     */
    static void exportTo(ClassLoader formatter) {
        var formatterModule = formatter.getUnnamedModule();
        var jdkCompiler = jdkCompiler().orElseThrow(() -> new Refused(NO_JDK_COMPILER, null));
        var notExported = PACKAGES.stream()
                .filter(javacPackage -> !jdkCompiler.isExported(javacPackage, formatterModule))
                .toList();
        if (notExported.isEmpty()) {
            return;
        }
        var implAddExports = implAddExports().orElseThrow(() -> new Refused(JAVA_LANG_NOT_OPEN, null));
        try {
            for (var javacPackage : notExported) {
                implAddExports.invoke(jdkCompiler, javacPackage, formatterModule);
            }
        } catch (ReflectiveOperationException e) {
            // What the method threw says more than that calling it failed.
            var cause = e.getCause() != null ? e.getCause() : e;
            throw new Refused("javac could not be exported to the formatter: " + cause, cause);
        }
    }

    private static Optional<Module> jdkCompiler() {
        return ModuleLayer.boot().findModule("jdk.compiler");
    }

    private static Optional<Method> implAddExports() {
        try {
            var method = Module.class.getDeclaredMethod("implAddExports", String.class, Module.class);
            return method.trySetAccessible() ? Optional.of(method) : Optional.empty();
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    /** What the way the JVM was started allows. A class of its own, to be worked out on first use and once. */
    private static final class AsStarted {
        static final Optional<String> UNAVAILABLE = check();

        private static Optional<String> check() {
            var jdkCompiler = jdkCompiler();
            if (jdkCompiler.isEmpty()) {
                return Optional.of(NO_JDK_COMPILER);
            }
            // --add-exports …=ALL-UNNAMED in the IDE's VM options exports to every class loader, this one included.
            var anyClassLoader = JavacAccess.class.getModule();
            if (PACKAGES.stream()
                    .allMatch(javacPackage -> jdkCompiler.get().isExported(javacPackage, anyClassLoader))) {
                return Optional.empty();
            }
            return implAddExports().isPresent() ? Optional.empty() : Optional.of(JAVA_LANG_NOT_OPEN);
        }
    }
}
