/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Lists an IDEA plugin in .idea/externalDependencies.xml, IntelliJ's "Required plugins". */
@DisableCachingByDefault(
        because = "Edits the developer's local .idea/externalDependencies.xml in place; nothing to cache")
public abstract class UpdateExternalDependenciesXmlFile extends DefaultTask {

    @Input
    public abstract Property<String> getPluginId();

    @Input
    public abstract Property<String> getMinVersion();

    @Optional
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public final void updateXml() {
        XmlUtils.updateIdeaXmlFile(
                getOutputFile().getAsFile().get(),
                node -> ConfigureJavaFormatterXml.configureExternalDependencies(
                        node, getPluginId().get(), getMinVersion().get()));
    }
}
