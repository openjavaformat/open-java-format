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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;

/**
 * The Java files of a project that has Java code: one with the java plugin, or with an Android plugin, which applies
 * java-base but not java and leaves the java extension's source sets empty (#284).
 *
 * <p>Public, as a parameter of {@link SpotlessInterop}'s constructor, which Gradle calls from a class it generates.
 */
public final class JavaSources {

    private final FileCollection files;
    private final boolean spotlessTarget;

    private JavaSources(FileCollection files, boolean spotlessTarget) {
        this.files = files;
        this.spotlessTarget = spotlessTarget;
    }

    /**
     * Runs the action once, with the project's Java sources, when the project gets the java plugin or one of
     * {@link AndroidSources#PLUGIN_IDS}, in whatever order the build applies them.
     */
    static void whenPresent(Project project, Action<JavaSources> action) {
        var done = new AtomicBoolean();
        project.getPluginManager().withPlugin("java", _javaPlugin -> {
            if (done.compareAndSet(false, true)) {
                action.execute(jvm(project));
            }
        });
        for (var id : AndroidSources.PLUGIN_IDS) {
            project.getPluginManager().withPlugin(id, _androidPlugin -> {
                if (done.compareAndSet(false, true)) {
                    action.execute(new JavaSources(AndroidSources.javaFiles(project), true));
                }
            });
        }
    }

    /**
     * The Java files of the java extension's source sets, which Spotless formats by default. A callable, so that source
     * sets added after the plugin count too.
     */
    private static JavaSources jvm(Project project) {
        var sourceSets =
                project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        return new JavaSources(
                project.getObjects().fileCollection().from((Callable<List<FileCollection>>) () -> sourceSets.stream()
                        .<FileCollection>map(sourceSet -> sourceSet.getAllJava())
                        .toList()),
                false);
    }

    /** The project's Java files. */
    FileCollection files() {
        return files;
    }

    /**
     * Whether Spotless needs {@link #files} as its target: its own default is the java extension's source sets, which
     * holds nothing in an Android project.
     */
    boolean spotlessTarget() {
        return spotlessTarget;
    }
}
