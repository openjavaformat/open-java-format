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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.palantir.javaformat.intellij.PalantirJavaFormatSettings.StoredSettings;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PalantirJavaFormatCodeStyleSettingsModifierTest {
    private final PalantirJavaFormatCodeStyleSettingsModifier modifier =
            new PalantirJavaFormatCodeStyleSettingsModifier();

    private JavaCodeInsightTestFixture fixture;
    private PalantirJavaFormatSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createLightFixtureBuilder(
                        new DefaultLightProjectDescriptor(), getClass().getName());
        // Files in the light project's source root, so that the imports between them resolve.
        fixture = JavaTestFixtureFactory.getFixtureFactory()
                .createCodeInsightFixture(projectBuilder.getFixture(), new LightTempDirTestFixtureImpl(true));
        fixture.setUp();
        settings = PalantirJavaFormatSettings.getInstance(fixture.getProject());
        var resetState = new StoredSettings();
        resetState.setEnabled("true");
        settings.loadState(resetState);
    }

    @AfterEach
    void tearDown() throws Exception {
        fixture.tearDown();
    }

    @Test
    void java_files_get_the_import_settings_that_open_java_format_writes_by() {
        var transientSettings = transientSettings(fixture.configureByText("A.java", "class A {}"));

        assertThat(modifier.modifySettings(transientSettings, fixture.getFile()))
                .isTrue();

        var javaSettings = transientSettings.getCustomSettings(JavaCodeStyleSettings.class);
        assertThat(javaSettings.USE_SINGLE_CLASS_IMPORTS).isTrue();
        assertThat(javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND).isEqualTo(999);
        assertThat(javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND).isEqualTo(999);
        assertThat(javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.getEntries()).isEmpty();
        assertThat(javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY).isTrue();
        assertThat(javaSettings.IMPORT_LAYOUT_TABLE.getEntries())
                .containsExactly(
                        PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY,
                        PackageEntry.BLANK_LINE_ENTRY,
                        PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
        // The project's own settings stay as they were.
        assertThat(JavaCodeStyleSettings.getInstance(fixture.getProject()).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND)
                .isEqualTo(5);
    }

    @Test
    void the_settings_stay_as_they_are_with_the_plugin_disabled() {
        settings.setEnabled(false);
        var transientSettings = transientSettings(fixture.configureByText("A.java", "class A {}"));

        assertThat(modifier.modifySettings(transientSettings, fixture.getFile()))
                .isFalse();
        assertThat(modifier.mayOverrideSettingsOf(fixture.getProject())).isFalse();
    }

    @Test
    void files_of_other_languages_keep_their_settings() {
        var transientSettings = transientSettings(fixture.configureByText("a.txt", "text"));

        assertThat(modifier.modifySettings(transientSettings, fixture.getFile()))
                .isFalse();
        assertThat(modifier.mayOverrideSettingsOf(fixture.getProject())).isTrue();
    }

    @Test
    void the_disable_link_of_the_code_style_page_switches_the_plugin_off() {
        requireNonNull(modifier.getDisablingFunction(fixture.getProject()))
                .accept(CodeStyle.getSettings(fixture.getProject()));

        assertThat(settings.isEnabled()).isFalse();
    }

    @Test
    void an_import_the_ide_adds_is_a_single_class_import_however_many_come_from_its_package() {
        assertThat(CodeStyleSettingsModifier.EP_NAME.getExtensionList())
                .hasAtLeastOneElementOfType(PalantirJavaFormatCodeStyleSettingsModifier.class);

        assertThat(addFifthImportFromOnePackage()).isEqualTo("""
                import p.A;
                import p.B;
                import p.C;
                import p.D;
                import p.E;

                class Main {}
                """);
    }

    @Test
    void with_the_plugin_disabled_the_ide_imports_a_fifth_class_from_a_package_on_demand() {
        settings.setEnabled(false);

        assertThat(addFifthImportFromOnePackage()).isEqualTo("""
                import p.*;

                class Main {}
                """);
    }

    /** Adds an import as auto-import, Alt+Enter and completion do, through the IDE's ImportHelper. */
    private String addFifthImportFromOnePackage() {
        // The test fixture gives every file the same temporary settings, which no modifier sees.
        CodeStyle.dropTemporarySettings(fixture.getProject());
        for (var name : List.of("A", "B", "C", "D")) {
            fixture.addClass("package p; public class " + name + " {}");
        }
        var fifth = fixture.addClass("package p; public class E {}");
        var file = (PsiJavaFile) fixture.configureByText("Main.java", """
                import p.A;
                import p.B;
                import p.C;
                import p.D;

                class Main {}
                """);

        WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
            JavaCodeStyleManager.getInstance(fixture.getProject()).addImport(file, fifth);
        });
        return file.getText();
    }

    private TransientCodeStyleSettings transientSettings(PsiFile file) {
        CodeStyleSettings projectSettings = CodeStyle.getSettings(fixture.getProject());
        return new TransientCodeStyleSettings(file.getVirtualFile(), fixture.getProject(), projectSettings);
    }
}
