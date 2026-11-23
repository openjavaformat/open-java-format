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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

final class XmlUtilsTest {

    @TempDir
    private Path dir;

    @Test
    void writes_a_new_file_as_intellij_does() throws IOException {
        var file = dir.resolve("externalDependencies.xml");

        XmlUtils.updateIdeaXmlFile(
                file.toFile(),
                root -> ConfigureJavaFormatterXml.configureExternalDependencies(root, "open-java-format", "2.57.0"));

        assertThat(Files.readString(file, UTF_8)).isEqualTo("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="ExternalDependencies">
                <plugin id="open-java-format" min-version="2.57.0" />
              </component>
            </project>""");
    }

    static Stream<Arguments> filesWithNothingToChange() {
        return Stream.of(
                argumentSet("as IntelliJ writes it", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project version="4">
                      <component name="ExternalDependencies">
                        <plugin min-version="5.0" id="CheckStyle-IDEA" />
                        <plugin id="open-java-format" min-version="2.98.0" />
                      </component>
                    </project>"""),
                argumentSet("as the plugin wrote it before", """
                    <project version="4">
                      <component name="ExternalDependencies">
                        <plugin min-version="5.0" id="CheckStyle-IDEA"/>
                        <plugin id="open-java-format" min-version="2.98.0"/>
                      </component>
                    </project>
                    """),
                argumentSet("by hand, with four spaces, CRLF, a comment and single quotes", """
                    <project version='4'>\r
                        <!-- Required plugins -->\r
                        <component name='ExternalDependencies'>\r
                            <plugin min-version='5.0' id='CheckStyle-IDEA'></plugin>\r
                            <plugin id='open-java-format' min-version='2.98.0'/>\r
                        </component>\r
                    </project>\r
                    """));
    }

    // The plugin lists itself with a lower min-version than the file does, which stays.
    @ParameterizedTest
    @MethodSource("filesWithNothingToChange")
    void leaves_a_file_with_nothing_to_change_byte_for_byte(String xml) throws IOException {
        var file = dir.resolve("externalDependencies.xml");
        Files.writeString(file, xml, UTF_8);

        XmlUtils.updateIdeaXmlFile(
                file.toFile(),
                root -> ConfigureJavaFormatterXml.configureExternalDependencies(root, "open-java-format", "2.57.0"));

        assertThat(Files.readString(file, UTF_8)).isEqualTo(xml);
    }

    // The plugin replaces the classpath on every sync: the same jars are nothing to change.
    @Test
    void leaves_the_same_classpath_byte_for_byte() throws IOException {
        var file = dir.resolve("open-java-format.xml");
        var xml = """
            <project version="4">
              <component name="PalantirJavaFormatSettings">
                <option name="enabled" value="true"/>
                <option name="implementationClassPath">
                  <list>
                    <option value="file:/home/dev/.gradle/open-java-format/formatter/open-java-format.jar"/>
                  </list>
                </option>
              </component>
            </project>
            """;
        Files.writeString(file, xml, UTF_8);

        XmlUtils.updateIdeaXmlFile(
                file.toFile(),
                root -> ConfigureJavaFormatterXml.configureJavaFormat(
                        root,
                        List.of(URI.create("file:/home/dev/.gradle/open-java-format/formatter/open-java-format.jar"))));

        assertThat(Files.readString(file, UTF_8)).isEqualTo(xml);
    }

    // The rest of a file IntelliJ wrote comes out as it went in, and in UTF-8.
    @Test
    void adds_to_a_file_intellij_wrote_and_changes_nothing_else() throws IOException {
        var file = dir.resolve("workspace.xml");
        Files.writeString(file, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="ChangeListManager">
                <list default="true" id="0ea2a88f-60dd-47ff-85b3-3e4e986aa463" name="Changes" comment="" />
                <option name="LAST_RESOLUTION" value="IGNORE" />
              </component>
              <component name="FormatOnSaveOptions">
                <option name="myRunOnSave" value="true" />
                <option name="myAllFileTypesSelected" value="false" />
                <option name="mySelectedFileTypes">
                  <set>
                    <option value="JAVA" />
                  </set>
                </option>
              </component>
              <component name="ProjectColorInfo"><![CDATA[{
              "associatedIndex": 3,
              "fromUser": false
            }]]></component>
              <component name="PropertiesComponent">{
              &quot;keyToString&quot;: {
                &quot;last_opened_file_path&quot;: &quot;/home/dev/Überarbeitung &amp; &lt;Test&gt;&quot;
              }
            }</component>
              <component name="VcsManagerConfiguration">
                <option name="LAST_COMMIT_MESSAGE" value="Überarbeitet&#10;&#10;mit &quot;Zitat&quot;&#9;&amp; &lt;Tag&gt;" />
              </component>
            </project>""", UTF_8);

        XmlUtils.updateIdeaXmlFile(file.toFile(), ConfigureJavaFormatterXml::configureWorkspaceXml);

        assertThat(Files.readString(file, UTF_8)).isEqualTo("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="ChangeListManager">
                <list default="true" id="0ea2a88f-60dd-47ff-85b3-3e4e986aa463" name="Changes" comment="" />
                <option name="LAST_RESOLUTION" value="IGNORE" />
              </component>
              <component name="FormatOnSaveOptions">
                <option name="myRunOnSave" value="true" />
                <option name="myAllFileTypesSelected" value="false" />
                <option name="mySelectedFileTypes">
                  <set>
                    <option value="JAVA" />
                  </set>
                </option>
              </component>
              <component name="ProjectColorInfo"><![CDATA[{
              "associatedIndex": 3,
              "fromUser": false
            }]]></component>
              <component name="PropertiesComponent">{
              &quot;keyToString&quot;: {
                &quot;last_opened_file_path&quot;: &quot;/home/dev/Überarbeitung &amp; &lt;Test&gt;&quot;
              }
            }</component>
              <component name="VcsManagerConfiguration">
                <option name="LAST_COMMIT_MESSAGE" value="Überarbeitet&#10;&#10;mit &quot;Zitat&quot;&#9;&amp; &lt;Tag&gt;" />
              </component>
              <component name="OptimizeOnSaveOptions">
                <option name="myRunOnSave" value="true" />
                <option name="myAllFileTypesSelected" value="false" />
                <option name="mySelectedFileTypes">
                  <set>
                    <option value="JAVA" />
                  </set>
                </option>
              </component>
            </project>""");
    }

    @Test
    void keeps_the_order_of_the_attributes_and_puts_those_added_after_them() throws IOException, SAXException {
        var document = XmlUtils.parse("""
            <project version="4">
              <plugin min-version="5.0" id="CheckStyle-IDEA" />
            </project>""".getBytes(UTF_8));
        var plugin = (Element) document.getElementsByTagName("plugin").item(0);

        plugin.setAttribute("max-version", "6.0");
        plugin.setAttribute("enabled", "true");

        assertThat(XmlUtils.print(plugin))
                .isEqualTo(
                        "<plugin min-version=\"5.0\" id=\"CheckStyle-IDEA\" enabled=\"true\" max-version=\"6.0\" />");
    }

    @Test
    void refuses_a_dtd() throws IOException {
        var file = dir.resolve("workspace.xml");
        Files.writeString(file, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE project [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <project version="4">&secret;</project>""", UTF_8);

        assertThatThrownBy(() ->
                        XmlUtils.updateIdeaXmlFile(file.toFile(), ConfigureJavaFormatterXml::configureWorkspaceXml))
                .hasMessageStartingWith("Couldn't parse existing configuration file: ")
                .hasCauseInstanceOf(SAXParseException.class);
    }
}
