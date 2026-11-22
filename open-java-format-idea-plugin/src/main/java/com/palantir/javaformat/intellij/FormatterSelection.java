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

import com.intellij.openapi.project.Project;
import com.palantir.javaformat.intellij.FormatterJdk.Resolved;
import com.palantir.javaformat.intellij.FormatterJdk.Unusable;
import com.palantir.javaformat.intellij.FormatterJdk.Usable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Which formatter a project's settings run. FormatterProvider starts it, and the settings page shows it as the active
 * formatter, so the page cannot claim one formatter while another runs.
 */
sealed interface FormatterSelection {

    /**
     * What the selection depends on besides the settings: the IDE and the machine. Each is looked at only when the
     * formatter it is for would run, since this runs for every file formatted: the embedded binary lists a directory,
     * and a JDK is looked up among the IDE's.
     */
    interface Environment {
        /** Why the formatter cannot run in the IDE's own JVM, if it cannot. */
        Optional<String> inProcessUnavailable();

        Resolved resolve(FormatterJdk jdk);

        Usable ideRuntime();

        /** The native binary the plugin downloaded. */
        Optional<Path> embeddedNativeImage();
    }

    /**
     * The settings name a way to run the formatter, and each way has what it needs or it does not: the IDE's JVM
     * access to javac, the JVM a JDK, the native formatter a binary, where a path of the user's own wins over the
     * embedded one. Without it the formatter runs in a JVM of its own, which always can: on the IDE's runtime if the
     * JDK is the one missing.
     */
    static FormatterSelection of(PalantirJavaFormatSettings settings, Environment environment) {
        return switch (settings.getFormatterMode()) {
            case IN_PROCESS ->
                environment
                        .inProcessUnavailable()
                        .<FormatterSelection>map(
                                reason -> separateJvm(settings, environment, new Fallback.InProcessUnavailable(reason)))
                        .orElseGet(InProcess::new);
            case JVM -> separateJvm(settings, environment);
            case NATIVE ->
                settings.getNativeImagePath()
                        .<FormatterSelection>map(path -> new NativeImage(path, false))
                        .or(() -> environment.embeddedNativeImage().map(path -> new NativeImage(path, true)))
                        .orElseGet(() -> separateJvm(settings, environment, new Fallback.NativeImageMissing()));
        };
    }

    private static SeparateJvm separateJvm(
            PalantirJavaFormatSettings settings, Environment environment, Fallback... fromAnotherMode) {
        var fallbacks = new ArrayList<>(List.of(fromAnotherMode));
        var jvm = switch (environment.resolve(settings.getFormatterJdk())) {
            case Usable usable -> usable;
            case Unusable unusable -> {
                fallbacks.add(new Fallback.JdkUnusable(unusable.reason()));
                yield environment.ideRuntime();
            }
        };
        return new SeparateJvm(jvm.java(), jvm.javaVersion(), List.copyOf(fallbacks));
    }

    /** The IDE and a project of it. */
    static Environment in(
            Project project,
            Supplier<Optional<String>> inProcessUnavailable,
            Supplier<Optional<Path>> embeddedNativeImage) {
        return new Environment() {
            @Override
            public Optional<String> inProcessUnavailable() {
                return inProcessUnavailable.get();
            }

            @Override
            public Resolved resolve(FormatterJdk jdk) {
                return FormatterJdks.resolve(project, jdk);
            }

            @Override
            public Usable ideRuntime() {
                return FormatterJdks.ideRuntime();
            }

            @Override
            public Optional<Path> embeddedNativeImage() {
                return embeddedNativeImage.get();
            }
        };
    }

    /** The formatter loaded into the IDE's own JVM: nothing to start, and the IDE's javac parses the file. */
    record InProcess() implements FormatterSelection {}

    /**
     * A JVM started for each call. fallbacks: what the settings asked for and cannot have, when this is not it: none
     * when they ask for this JVM.
     */
    record SeparateJvm(Path java, int javaVersion, List<Fallback> fallbacks) implements FormatterSelection {}

    record NativeImage(Path path, boolean embedded) implements FormatterSelection {}

    /** Why something else runs than what the settings name. */
    sealed interface Fallback {
        /** reason: as {@link InProcessFormatters#unavailable} gives it. */
        record InProcessUnavailable(String reason) implements Fallback {}

        /** The native formatter is on, but there is no binary to run: the embedded one may not be downloaded yet. */
        record NativeImageMissing() implements Fallback {}

        /** The JDK the settings name cannot run the formatter, so the IDE's runtime does. */
        record JdkUnusable(String reason) implements Fallback {}
    }
}
