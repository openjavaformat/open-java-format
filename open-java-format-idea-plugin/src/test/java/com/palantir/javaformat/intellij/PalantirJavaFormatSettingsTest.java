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

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.palantir.javaformat.intellij.PalantirJavaFormatSettings.StoredSettings;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

/** The settings as .idea/open-java-format.xml holds them, which files written by older plugins have to keep loading. */
final class PalantirJavaFormatSettingsTest {

    @Test
    void loadsAFileThatStillHasTheCodeStyle() throws Exception {
        // The plugin used to offer a choice of one style and store it.
        var state = deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="enabled" value="true" />
              <option name="style" value="OJF" />
            </component>
            """);

        assertThat(state.getEnabled()).isEqualTo("true");
    }

    @Test
    void doesNotWriteACodeStyle() {
        var xml = JDOMUtil.write(XmlSerializer.serialize(new StoredSettings()));

        assertThat(xml).doesNotContain("style");
    }

    @Test
    void keepsTheNativeBinarySettings() throws Exception {
        var settings = new PalantirJavaFormatSettings();
        settings.setFormatterMode(FormatterMode.NATIVE);
        settings.setNativeImagePath(Optional.of(Path.of("/opt/open-java-format")));

        var reloaded = reload(settings);

        assertThat(reloaded.getFormatterMode()).isEqualTo(FormatterMode.NATIVE);
        assertThat(reloaded.getNativeImagePath()).hasValue(Path.of("/opt/open-java-format"));
    }

    @Test
    void switchesAFileFromBeforeTheModesToTheNativeBinaryItHadOn() throws Exception {
        var settings = new PalantirJavaFormatSettings();
        settings.loadState(deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="enabled" value="true" />
              <option name="useNativeImage" value="true" />
            </component>
            """));

        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.NATIVE);
        // The mode says it from here on. useNativeImage is empty, and the IDE leaves an empty setting out of the file.
        assertThat(settings.getState().formatterMode).isEqualTo("native");
        assertThat(settings.getState().useNativeImage).isNull();
        assertThat(reload(settings).getFormatterMode()).isEqualTo(FormatterMode.NATIVE);
    }

    @Test
    void forgetsUseNativeImageOnceItIsRead() throws Exception {
        // It decides the mode when the file is read, and no more after that: another mode chosen since stays chosen.
        var settings = new PalantirJavaFormatSettings();
        settings.loadState(deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="useNativeImage" value="true" />
            </component>
            """));

        settings.setFormatterMode(FormatterMode.JVM);

        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.JVM);
        assertThat(reload(settings).getFormatterMode()).isEqualTo(FormatterMode.JVM);
    }

    @Test
    void switchesToTheNativeBinaryWhateverModeStandsBesideUseNativeImage() throws Exception {
        // The plugin never writes the two together, so a file with both was written to by a plugin from before the
        // modes, which knows only useNativeImage.
        var settings = new PalantirJavaFormatSettings();
        settings.loadState(deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="formatterMode" value="jvm" />
              <option name="useNativeImage" value="true" />
            </component>
            """));

        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.NATIVE);
    }

    @Test
    void movesAProjectThatFormattedInAJvmOfItsOwnIntoTheIde() throws Exception {
        // Before there were modes, a project without the native binary started a JVM for every file.
        var withoutTheNativeBinary = new PalantirJavaFormatSettings();
        withoutTheNativeBinary.loadState(deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="enabled" value="true" />
            </component>
            """));
        var withTheNativeBinaryOff = new PalantirJavaFormatSettings();
        withTheNativeBinaryOff.loadState(deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="enabled" value="true" />
              <option name="useNativeImage" value="false" />
            </component>
            """));

        assertThat(withoutTheNativeBinary.getFormatterMode()).isEqualTo(FormatterMode.IN_PROCESS);
        assertThat(withTheNativeBinaryOff.getFormatterMode()).isEqualTo(FormatterMode.IN_PROCESS);
        assertThat(withTheNativeBinaryOff.getState().useNativeImage).isNull();
        assertThat(new PalantirJavaFormatSettings().getFormatterMode()).isEqualTo(FormatterMode.IN_PROCESS);
    }

    @Test
    void namesTheModeInOneSetting() {
        // In words of the file's own, which stay when a constant is renamed.
        var settings = new PalantirJavaFormatSettings();

        settings.setFormatterMode(FormatterMode.IN_PROCESS);
        assertThat(settings.getState().formatterMode).isEqualTo("in-process");
        settings.setFormatterMode(FormatterMode.JVM);
        assertThat(settings.getState().formatterMode).isEqualTo("jvm");
        settings.setFormatterMode(FormatterMode.NATIVE);
        assertThat(settings.getState().formatterMode).isEqualTo("native");

        // And in no other: useNativeImage is only ever read.
        assertThat(settings.getState().useNativeImage).isNull();
    }

    @Test
    void keepsTheModeAndTheJdk() throws Exception {
        var settings = new PalantirJavaFormatSettings();
        settings.setFormatterMode(FormatterMode.JVM);
        settings.setJvmJdk(new FormatterJdk.Named("temurin-25"));

        var reloaded = reload(settings);

        assertThat(reloaded.getFormatterMode()).isEqualTo(FormatterMode.JVM);
        assertThat(reloaded.getJvmJdk()).isEqualTo(new FormatterJdk.Named("temurin-25"));
    }

    @Test
    void namesTheSettingsOfAModeAfterIt() {
        // A mode added later, such as a daemon with a JDK of its own, then has settings no other mode reads.
        var settings = new PalantirJavaFormatSettings();
        settings.setJvmJdk(new FormatterJdk.Named("temurin-25"));
        settings.setNativeImagePath(Optional.of(Path.of("/opt/open-java-format")));

        assertThat(JDOMUtil.write(XmlSerializer.serialize(settings.getState())))
                .contains(
                        "<option name=\"jvmJdk\" value=\"temurin-25\" />",
                        "<option name=\"nativeImagePath\" value=\"/opt/open-java-format\" />");
    }

    @Test
    void keepsTheProjectSdkAsTheJdk() throws Exception {
        var settings = new PalantirJavaFormatSettings();
        settings.setJvmJdk(new FormatterJdk.ProjectSdk());

        assertThat(reload(settings).getJvmJdk()).isEqualTo(new FormatterJdk.ProjectSdk());
    }

    @Test
    void runsTheJvmOnTheIdesRuntimeUnlessAJdkIsStored() throws Exception {
        var settings = new PalantirJavaFormatSettings();
        assertThat(settings.getJvmJdk()).isEqualTo(new FormatterJdk.IdeRuntime());

        settings.setJvmJdk(new FormatterJdk.Named("temurin-25"));
        settings.setJvmJdk(new FormatterJdk.IdeRuntime());

        // It has no name to store: the file says nothing, as a file from before the choice of a JDK does.
        assertThat(settings.getState().jvmJdk).isNull();
        assertThat(reload(settings).getJvmJdk()).isEqualTo(new FormatterJdk.IdeRuntime());
    }

    @Test
    void takesAModeItDoesNotKnowForNone() throws Exception {
        // A later plugin can have modes that this one has not.
        var settings = new PalantirJavaFormatSettings();
        settings.loadState(deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="formatterMode" value="daemon" />
            </component>
            """));

        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.IN_PROCESS);
    }

    @Test
    void keepsTheImplementationClassPath() throws Exception {
        var state = new StoredSettings();
        state.setImplementationClassPath(List.of("file:/opt/formatter.jar", "file:/opt/guava.jar"));

        var reloaded = new PalantirJavaFormatSettings();
        reloaded.loadState(deserialize(JDOMUtil.write(XmlSerializer.serialize(state))));

        assertThat(reloaded.getImplementationClassPath())
                .hasValue(List.of(URI.create("file:/opt/formatter.jar"), URI.create("file:/opt/guava.jar")));
    }

    @Test
    void ignoresTheNativeImageTheGradlePluginUsedToWrite() throws Exception {
        // Gradle plugins up to 21.0.1 wrote this for openjavaformat.native.formatter=true. The native binary is a
        // setting of the IDE now, and a build no longer switches it on.
        var settings = new PalantirJavaFormatSettings();
        settings.loadState(deserialize("""
            <component name="PalantirJavaFormatSettings">
              <option name="enabled" value="true" />
              <option name="nativeImageClassPath" value="file:/home/me/.gradle/open-java-format-caches/native" />
            </component>
            """));

        assertThat(settings.getFormatterMode()).isNotEqualTo(FormatterMode.NATIVE);
        assertThat(settings.getNativeImagePath()).isEmpty();
    }

    @Test
    void takesABlankPathForNone() {
        var state = new StoredSettings();
        state.nativeImagePath = " ";
        var settings = new PalantirJavaFormatSettings();
        settings.loadState(state);

        assertThat(settings.getNativeImagePath()).isEmpty();
    }

    /** The settings as a project reads them back from the file they were written to. */
    private static PalantirJavaFormatSettings reload(PalantirJavaFormatSettings settings) throws Exception {
        var reloaded = new PalantirJavaFormatSettings();
        reloaded.loadState(deserialize(JDOMUtil.write(XmlSerializer.serialize(settings.getState()))));
        return reloaded;
    }

    private static StoredSettings deserialize(@Language("xml") String xml) throws Exception {
        return XmlSerializer.deserialize(JDOMUtil.load(xml), StoredSettings.class);
    }
}
