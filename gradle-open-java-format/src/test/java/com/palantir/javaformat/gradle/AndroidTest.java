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

package com.palantir.javaformat.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.gradle.testing.GradleTestProject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The plugins in Android projects, which apply java-base but not java (#284): the Spotless step formats the Java files
 * of the Android source sets, and formatDiff is there. The builds have no Android SDK and need none.
 */
@ParameterizedClass(name = "configuration cache {0}")
@ValueSource(booleans = {true, false})
final class AndroidTest {

    private static final String UNFORMATTED = "class Main {\n        int x;\n}\n";
    private static final String FORMATTED = "class Main {\n    int x;\n}\n";

    private static final String MAIN_JAVA = "src/main/java/test/Main.java";

    /** A file in each kind of source set: main, unit tests, instrumented tests, a build type. */
    private static final List<String> SOURCE_SET_FILES = List.of(
            MAIN_JAVA,
            "src/test/java/test/MainTest.java",
            "src/androidTest/java/test/MainAndroidTest.java",
            "src/debug/java/test/Debug.java");

    private static final String ANDROID = """
        android {
            namespace = 'test'
            compileSdk = 36
        }
        """;

    @Parameter
    private boolean configurationCache;

    @TempDir
    private Path projectDir;

    @ParameterizedTest
    @ValueSource(strings = {"com.android.application", "com.android.library"})
    void spotlessApply_formats_the_java_files_of_every_android_source_set(String androidPlugin) {
        var project = project(androidPlugin);
        SOURCE_SET_FILES.forEach(file -> project.writeFile(file, UNFORMATTED));

        project.succeeds("spotlessApply");

        assertFormatted(project, SOURCE_SET_FILES);
    }

    @Test
    void spotlessCheck_fails_on_an_unformatted_file() {
        var project = project("com.android.library").writeFile(MAIN_JAVA, UNFORMATTED);

        BuildResult result = project.fails("spotlessCheck");

        // A formatter that fails reports a lint error instead, which fails the check just as well.
        assertThat(result.getOutput())
                .contains("format violations")
                .contains("Main.java")
                .doesNotContain("lint error");
    }

    @Test
    void spotlessApply_leaves_a_generated_source_directory_alone() {
        var generated = "build/generated/custom/test/Generated.java";
        var project = project("com.android.library")
                .buildGradle("android.sourceSets.main.java.srcDir('build/generated/custom')")
                .writeFile(generated, UNFORMATTED)
                .writeFile(MAIN_JAVA, UNFORMATTED);

        project.succeeds("spotlessApply");

        assertThat(project.readFile(generated)).isEqualTo(UNFORMATTED);
        assertThat(project.readFile(MAIN_JAVA)).isEqualTo(FORMATTED);
    }

    @Test
    void a_target_in_the_build_script_replaces_the_android_source_sets() {
        var testJava = "src/test/java/test/MainTest.java";
        var project = project("com.android.library")
                .buildGradle("""
                    spotless {
                        java {
                            target 'src/main/java/**/*.java'
                        }
                    }
                    """)
                .writeFile(MAIN_JAVA, UNFORMATTED)
                .writeFile(testJava, UNFORMATTED);

        project.succeeds("spotlessApply");

        assertThat(project.readFile(MAIN_JAVA)).isEqualTo(FORMATTED);
        assertThat(project.readFile(testJava)).isEqualTo(UNFORMATTED);
    }

    @Test
    void spotlessApply_formats_a_file_added_after_the_configuration_was_cached() {
        var added = "src/main/java/test/Added.java";
        var project = project("com.android.library").writeFile(MAIN_JAVA, UNFORMATTED);
        project.succeeds("spotlessApply");
        project.writeFile(added, UNFORMATTED);

        BuildResult result = project.succeeds("spotlessApply");

        assertThat(project.readFile(added)).isEqualTo(FORMATTED);
        if (configurationCache) {
            // A target that walked the source directories at configuration time would make every new file a reason to
            // configure again (#193).
            assertThat(result.getOutput()).contains("Configuration cache entry reused.");
        }
    }

    @Test
    void formatDiff_formats_the_changed_lines_without_spotless() throws IOException, InterruptedException {
        var project = new GradleTestProject(projectDir)
                .configurationCache(configurationCache)
                .withAndroidGradlePlugin()
                .withoutSpotlessOnClasspath()
                .plugins("com.android.library", "dev.openjavaformat.java-format")
                .withJavacInternalExports()
                .buildGradle(ANDROID)
                .writeFile(MAIN_JAVA, "class Main {}\n");
        commitAll(project);
        project.writeFile(MAIN_JAVA, UNFORMATTED);

        project.succeeds("formatDiff");

        assertThat(project.readFile(MAIN_JAVA)).isEqualTo(FORMATTED);
    }

    @Test
    void spotlessApply_formats_android_and_java_subprojects_in_a_parallel_build() {
        // As in the report of #284: the plugins declared in the root project and applied by each subproject.
        var project = new GradleTestProject(projectDir)
                .configurationCache(configurationCache)
                .withAndroidGradlePlugin()
                .pluginsNotApplied(
                        "com.android.application",
                        "com.android.library",
                        "dev.openjavaformat.java-format",
                        "com.diffplug.spotless")
                .subprojects("app", "androidlibrary", "javalibrary")
                .withJavacInternalExports();
        subproject(project, "app", "com.android.application", ANDROID);
        subproject(project, "androidlibrary", "com.android.library", ANDROID);
        subproject(project, "javalibrary", "java-library", "");

        project.succeeds("spotlessApply", "--parallel");

        assertFormatted(
                project, List.of("app/" + MAIN_JAVA, "androidlibrary/" + MAIN_JAVA, "javalibrary/" + MAIN_JAVA));
    }

    private GradleTestProject project(String androidPlugin) {
        return new GradleTestProject(projectDir)
                .configurationCache(configurationCache)
                .withAndroidGradlePlugin()
                .plugins(androidPlugin, "dev.openjavaformat.java-format", "com.diffplug.spotless")
                .withJavacInternalExports()
                .buildGradle(ANDROID);
    }

    private static void subproject(GradleTestProject project, String name, String plugin, String script) {
        project.writeFile(name + "/build.gradle", """
            plugins {
                id '%s'
                id 'dev.openjavaformat.java-format'
                id 'com.diffplug.spotless'
            }

            %s""".formatted(plugin, script))
                .writeFile(name + "/" + MAIN_JAVA, UNFORMATTED);
    }

    private static void assertFormatted(GradleTestProject project, List<String> files) {
        for (var file : files) {
            assertThat(project.readFile(file)).as(file).isEqualTo(FORMATTED);
        }
    }

    /** Makes the project a throwaway repository with everything committed, for formatDiff to diff against. */
    private static void commitAll(GradleTestProject project) throws IOException, InterruptedException {
        git(project, "init");
        git(project, "config", "user.name", "Foo");
        git(project, "config", "user.email", "foo@bar.com");
        // The repository this runs in may sign commits; a throwaway one has no key to sign with.
        git(project, "config", "commit.gpgsign", "false");
        git(project, "add", ".");
        git(project, "commit", "-m", "Commit");
    }

    private static void git(GradleTestProject project, String... args) throws IOException, InterruptedException {
        var command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        var exitCode = new ProcessBuilder(command)
                .directory(project.path().toFile())
                .start()
                .waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("git " + String.join(" ", args) + " failed with exit code " + exitCode);
        }
    }
}
