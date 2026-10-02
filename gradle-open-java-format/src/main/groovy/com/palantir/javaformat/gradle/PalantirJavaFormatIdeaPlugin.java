/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.StartParameter;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskProvider;

public abstract class PalantirJavaFormatIdeaPlugin implements Plugin<Project> {

    @Nested
    protected abstract NativeImageSupport getNativeImageSupport();

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    private static final String MIN_IDEA_PLUGIN_VERSION = "2.57.0";

    @Override
    public void apply(Project rootProject) {
        Preconditions.checkState(
                rootProject == rootProject.getRootProject(),
                "May only apply dev.openjavaformat.java-format-idea to the root project");

        rootProject.getPlugins().apply(PalantirJavaFormatProviderPlugin.class);
        rootProject.getPluginManager().withPlugin("idea", ideaPlugin -> {
            TaskProvider<UpdatePalantirJavaFormatIdeaXmlFile> updateOpenJavaFormatXml = rootProject
                    .getTasks()
                    .register("updateOpenJavaFormatXml", UpdatePalantirJavaFormatIdeaXmlFile.class, task -> {
                        task.getXmlOutputFile().set(rootProject.file(".idea/open-java-format.xml"));
                        task.getImplementationConfig()
                                .from(rootProject
                                        .getConfigurations()
                                        .getByName(PalantirJavaFormatProviderPlugin.CONFIGURATION_NAME));
                        maybeGetNativeImplConfiguration().ifPresent(config -> {
                            task.getNativeImageConfig().from(config);
                            task.getNativeImageOutputFile()
                                    .fileProvider(rootProject.provider(() -> rootProject
                                            .getGradle()
                                            .getGradleUserHomeDir()
                                            .toPath()
                                            .resolve("open-java-format-caches/")
                                            .resolve(Paths.get(task.getNativeImageConfig()
                                                            .getSingleFile()
                                                            .toURI())
                                                    .getFileName()
                                                    .toString())
                                            .toFile()));
                        });
                    });

            TaskProvider<UpdateWorkspaceXmlFile> updateWorkspaceXml = rootProject
                    .getTasks()
                    .register("updateWorkspaceXml", UpdateWorkspaceXmlFile.class, task -> {
                        task.getOutputFile().set(rootProject.file(".idea/workspace.xml"));
                    });

            runWithEveryBuild(rootProject, updateOpenJavaFormatXml, updateWorkspaceXml);
        });

        // IntelliJ's Gradle sync sets idea.active. The project then lists the open-java-format IDEA plugin as
        // required, and IntelliJ offers to install it.
        if (rootProject
                .getProviders()
                .systemProperty("idea.active")
                .map(Boolean::parseBoolean)
                .getOrElse(false)) {
            TaskProvider<UpdateExternalDependenciesXmlFile> updateExternalDependenciesXml = rootProject
                    .getTasks()
                    .register("updateExternalDependenciesXml", UpdateExternalDependenciesXmlFile.class, task -> {
                        task.getPluginId().set("open-java-format");
                        task.getMinVersion().set(MIN_IDEA_PLUGIN_VERSION);
                        task.getOutputFile().set(rootProject.file(".idea/externalDependencies.xml"));
                    });
            runWithEveryBuild(rootProject, updateExternalDependenciesXml);
        }
    }

    // Adds the tasks to the Gradle start parameters, so they run with whatever the build was asked to do.
    private static void runWithEveryBuild(Project rootProject, TaskProvider<?>... tasks) {
        StartParameter startParameter = rootProject.getGradle().getStartParameter();
        startParameter.setTaskNames(ImmutableList.<String>builder()
                .addAll(startParameter.getTaskNames())
                .addAll(Stream.of(tasks).map(task -> ":" + task.getName()).toList())
                .build());
    }

    private Optional<Configuration> maybeGetNativeImplConfiguration() {
        return getNativeImageSupport().isNativeImageConfigured()
                ? Optional.of(getConfigurations().getByName(NativeImageFormatProviderPlugin.NATIVE_CONFIGURATION_NAME))
                : Optional.empty();
    }
}
