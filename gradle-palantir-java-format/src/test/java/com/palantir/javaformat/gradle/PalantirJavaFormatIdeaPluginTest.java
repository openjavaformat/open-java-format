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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.palantir.javaformat.gradle.testing.GradleTestProject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PalantirJavaFormatIdeaPluginTest {

    private static final String NATIVE_IMAGE_FILE = new File("build/nativeImage.path").getAbsolutePath();

    private static final String NATIVE_CONFIG =
            "palantirJavaFormatNative files(file(\"" + NATIVE_IMAGE_FILE + "\").text)";

    private static final ObjectMapper XML_MAPPER = new XmlMapper();

    @TempDir
    private Path projectDir;

    @ParameterizedTest(name = "extraGradleProperties={0}")
    @ValueSource(strings = {"", "palantir.native.formatter=true"})
    void idea_configures_xml_files(String extraGradleProperties) throws IOException {
        GradleTestProject project = new GradleTestProject(projectDir)
                .plugins("com.palantir.java-format-idea", "idea")
                .gradleProperties(extraGradleProperties)
                .buildGradle(
                        """
                        dependencies {
                            palantirJavaFormat project.files() // no need to store the real thing in here
                            %s
                        }
                        """,
                        extraGradleProperties.isBlank() ? "" : NATIVE_CONFIG);

        project.succeeds("idea");

        assertThat(project.file(".idea/palantir-java-format.xml")).exists();

        Project xmlContent =
                XML_MAPPER.readValue(project.file(".idea/palantir-java-format.xml").toFile(), Project.class);

        assertThat(xmlContent.components()).anyMatch(c -> "PalantirJavaFormatSettings".equals(c.name()));

        List<Option> allOptions = xmlContent.components().stream()
                .flatMap(c -> Optional.ofNullable(c.options()).stream().flatMap(List::stream))
                .toList();

        assertThat(allOptions).anyMatch(o -> "implementationClassPath".equals(o.name()));

        if (extraGradleProperties.contains("palantir.native.formatter=true")) {
            assertThat(allOptions).anyMatch(o -> "nativeImageClassPath".equals(o.name()));
        }

        assertThat(project.file(".idea/workspace.xml")).exists();

        Project workspaceContent = XML_MAPPER.readValue(project.file(".idea/workspace.xml").toFile(), Project.class);

        assertThat(workspaceContent.components()).anyMatch(c -> "FormatOnSaveOptions".equals(c.name()));
        assertThat(workspaceContent.components()).anyMatch(c -> "OptimizeOnSaveOptions".equals(c.name()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Project(
            @JacksonXmlProperty(localName = "component") @JacksonXmlElementWrapper(useWrapping = false)
                    List<Component> components) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Component(
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(localName = "option") @JacksonXmlElementWrapper(useWrapping = false)
                    List<Option> options) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Option(@JacksonXmlProperty(isAttribute = true) String name) {}
}
