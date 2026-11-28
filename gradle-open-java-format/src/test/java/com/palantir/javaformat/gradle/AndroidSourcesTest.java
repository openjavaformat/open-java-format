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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Reading the Android extension by reflection, on stand-ins for the Android DSL. {@link AndroidTest} reads the real one
 * of the oldest Android Gradle plugin we support; these are the extensions it does not expect.
 */
final class AndroidSourcesTest {

    private static final File MAIN = new File("/project/src/main/java");
    private static final File MAIN_KOTLIN = new File("/project/src/main/kotlin");
    private static final File TEST = new File("/project/src/test/java");

    @Test
    void reads_the_java_directories_of_every_source_set() {
        var android = new Android(List.of(new SourceSet(Set.of(MAIN, MAIN_KOTLIN)), new SourceSet(Set.of(TEST))));

        assertThat(AndroidSources.javaDirectories(android, ":app")).containsExactlyInAnyOrder(MAIN, MAIN_KOTLIN, TEST);
    }

    @Test
    void names_the_getter_an_extension_does_not_have() {
        assertThatThrownBy(() -> AndroidSources.javaDirectories(new Object(), ":app"))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("android.sourceSets[*].java.srcDirs of project :app")
                .hasMessageContaining("java.lang.Object has no public getSourceSets()");
    }

    @Test
    void names_what_a_getter_returned_instead_of_files() {
        var android = new Android(List.of(new SourceSet(Set.of("src/main/java"))));

        assertThatThrownBy(() -> AndroidSources.javaDirectories(android, ":app"))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("getSrcDirs() holds a java.lang.String, not a File");
    }

    @Test
    void names_a_getter_that_returned_null() {
        var android = new Android(null);

        assertThatThrownBy(() -> AndroidSources.javaDirectories(android, ":app"))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("getSourceSets() returned null, not a java.lang.Iterable");
    }

    /** Public, with public getters, as the classes Gradle generates for the Android DSL are. */
    public static final class Android {
        private final @Nullable List<SourceSet> sourceSets;

        Android(@Nullable List<SourceSet> sourceSets) {
            this.sourceSets = sourceSets;
        }

        public @Nullable List<SourceSet> getSourceSets() {
            return sourceSets;
        }
    }

    public static final class SourceSet {
        private final Directories java;

        SourceSet(Set<?> srcDirs) {
            this.java = new Directories(srcDirs);
        }

        public Directories getJava() {
            return java;
        }
    }

    public static final class Directories {
        private final Set<?> srcDirs;

        Directories(Set<?> srcDirs) {
            this.srcDirs = srcDirs;
        }

        public Set<?> getSrcDirs() {
            return srcDirs;
        }
    }
}
