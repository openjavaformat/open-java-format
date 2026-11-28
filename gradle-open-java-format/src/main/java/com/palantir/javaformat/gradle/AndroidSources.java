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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.jspecify.annotations.Nullable;

/**
 * The Java files of an Android project: those in the {@code java.srcDirs} of every source set of its {@code android}
 * extension, main, test and androidTest, and those of the build types and flavors.
 *
 * <p>Read by reflection. The plugin has no dependency on the Android Gradle plugin: it never brings a version of its
 * own into a build that has one, and it does not need the classes of the one the build has, which the class loader of
 * a project that applies the plugin may not see. The three getters it calls are those of the Android DSL.
 */
final class AndroidSources {

    /** The Android plugins whose {@code android} extension has {@code sourceSets}. None of them applies java. */
    static final List<String> PLUGIN_IDS = List.of(
            "com.android.application", "com.android.library", "com.android.dynamic-feature", "com.android.test");

    private AndroidSources() {}

    /**
     * The Java files of the project's Android source sets. A callable: the source sets of the build types and flavors
     * are added once the build script declares them, after the plugin is applied.
     */
    static FileCollection javaFiles(Project project) {
        var android = project.getExtensions().getByName("android");
        var objects = project.getObjects();
        var projectPath = project.getPath();
        return objects.fileCollection().from((Callable<List<FileTree>>) () ->
                javaDirectories(android, projectPath).stream()
                        .map(dir -> objects.fileTree().from(dir).matching(files -> files.include("**/*.java")))
                        .toList());
    }

    /** The {@code java.srcDirs} of every source set of {@code android}, the project's Android extension. */
    static List<File> javaDirectories(Object android, String projectPath) {
        var directories = new ArrayList<File>();
        for (var sourceSet : get(android, "getSourceSets", Iterable.class, projectPath)) {
            var java = get(sourceSet, "getJava", Object.class, projectPath);
            for (var directory : get(java, "getSrcDirs", Collection.class, projectPath)) {
                if (!(directory instanceof File file)) {
                    throw unreadable(projectPath, "getSrcDirs() holds " + describe(directory) + ", not a File", null);
                }
                directories.add(file);
            }
        }
        return directories;
    }

    private static <T> T get(Object object, String getter, Class<T> type, String projectPath) {
        @Nullable Object value;
        try {
            value = object.getClass().getMethod(getter).invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw unreadable(projectPath, object.getClass().getName() + " has no public " + getter + "()", e);
        } catch (InvocationTargetException e) {
            throw unreadable(projectPath, getter + "() failed", e.getCause());
        }
        if (!type.isInstance(value)) {
            throw unreadable(
                    projectPath, getter + "() returned " + describe(value) + ", not a " + type.getName(), null);
        }
        return type.cast(value);
    }

    private static String describe(@Nullable Object value) {
        return value == null ? "null" : "a " + value.getClass().getName();
    }

    private static GradleException unreadable(String projectPath, String detail, @Nullable Throwable cause) {
        return new GradleException(
                "open-java-format could not read android.sourceSets[*].java.srcDirs of project " + projectPath
                        + ", the directories it formats: " + detail + ". This version of the Android Gradle plugin"
                        + " may need a newer open-java-format; please report it at"
                        + " https://github.com/openjavaformat/open-java-format/issues",
                cause);
    }
}
