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
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.lang.JavaVersion;
import com.palantir.javaformat.intellij.FormatterJdk.IdeRuntime;
import com.palantir.javaformat.intellij.FormatterJdk.Named;
import com.palantir.javaformat.intellij.FormatterJdk.ProjectSdk;
import com.palantir.javaformat.intellij.FormatterJdk.Resolved;
import com.palantir.javaformat.intellij.FormatterJdk.Unusable;
import com.palantir.javaformat.intellij.FormatterJdk.Usable;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** The JDKs the formatter's own JVM can run on: the IDE's runtime, the project SDK and the JDKs the IDE knows. */
final class FormatterJdks {

    /** The formatter is compiled for Java 21. */
    static final int MINIMUM_JAVA = 21;

    private FormatterJdks() {}

    /** The runtime the IDE runs on. Every IDE this plugin supports runs on Java 21 or later. */
    static Usable ideRuntime() {
        return new Usable(
                javaIn(Path.of(System.getProperty("java.home"))),
                Runtime.version().feature());
    }

    /** What the settings page lists: the IDE's runtime, the project SDK, and the JDKs by name. */
    static List<FormatterJdk> choices() {
        return Stream.<FormatterJdk>concat(
                        Stream.of(new IdeRuntime(), new ProjectSdk()),
                        ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).stream()
                                .map(Sdk::getName)
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .map(Named::new))
                .toList();
    }

    /** A line of that list. detail: what the name stands for, shown beside it in grey. */
    record Label(String name, String detail) {}

    static Label label(Project project, FormatterJdk jdk) {
        return switch (jdk) {
            case IdeRuntime ideRuntime -> new Label("IDE runtime", "Java " + System.getProperty("java.version"));
            case ProjectSdk projectSdk -> {
                var sdk = projectSdk(project);
                yield new Label(
                        "Project SDK",
                        sdk == null
                                ? "none"
                                : Stream.of(sdk.getName(), java(sdk))
                                        .filter(part -> !part.isEmpty())
                                        .collect(Collectors.joining(", ")));
            }
            case Named named -> {
                var sdk = ProjectJdkTable.getInstance().findJdk(named.name());
                yield new Label(named.name(), sdk == null ? "not found" : java(sdk));
            }
        };
    }

    /**
     * The java a JDK stands for. It is looked up for every file formatted in a JVM of its own, so that a project SDK
     * that changes, or a JDK that is removed, shows at once.
     */
    static Resolved resolve(Project project, FormatterJdk jdk) {
        return switch (jdk) {
            case IdeRuntime ideRuntime -> ideRuntime();
            case ProjectSdk projectSdk -> {
                var sdk = projectSdk(project);
                yield sdk == null ? new Unusable("the project has no SDK") : resolve(sdk);
            }
            case Named named -> {
                var sdk = ProjectJdkTable.getInstance().findJdk(named.name());
                yield sdk == null ? new Unusable("there is no JDK named " + named.name()) : resolve(sdk);
            }
        };
    }

    private static Resolved resolve(Sdk sdk) {
        // A project SDK can be one for another language.
        if (!(sdk.getSdkType() instanceof JavaSdk)) {
            return new Unusable(sdk.getName() + " is not a JDK");
        }
        var javaVersion = javaVersion(sdk);
        if (javaVersion.isEmpty()) {
            return new Unusable("the Java version of " + sdk.getName() + " is not known");
        }
        if (javaVersion.getAsInt() < MINIMUM_JAVA) {
            return new Unusable(sdk.getName() + " is Java " + javaVersion.getAsInt() + ", and the formatter needs Java "
                    + MINIMUM_JAVA + " or later");
        }
        var java = executable(sdk);
        // Not on this machine, for one: a JDK in WSL or in a container has no java that the IDE can start.
        if (java == null || !Files.isExecutable(java)) {
            return new Unusable(sdk.getName() + " has no java to run in " + sdk.getHomePath());
        }
        return new Usable(java, javaVersion.getAsInt());
    }

    private static @Nullable Sdk projectSdk(Project project) {
        return ProjectRootManager.getInstance(project).getProjectSdk();
    }

    private static OptionalInt javaVersion(Sdk sdk) {
        var versionString = sdk.getVersionString();
        var version = versionString == null ? null : JavaVersion.tryParse(versionString);
        return version == null ? OptionalInt.empty() : OptionalInt.of(version.feature);
    }

    /** "Java 21", or nothing for a JDK whose version is not known. */
    private static String java(Sdk sdk) {
        var javaVersion = javaVersion(sdk);
        return javaVersion.isPresent() ? "Java " + javaVersion.getAsInt() : "";
    }

    private static @Nullable Path executable(Sdk sdk) {
        var home = sdk.getHomePath();
        if (home == null) {
            return null;
        }
        try {
            return javaIn(Path.of(home));
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static Path javaIn(Path home) {
        return home.resolve("bin").resolve(SystemInfo.isWindows ? "java.exe" : "java");
    }
}
