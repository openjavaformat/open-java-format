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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Gives the Java files the plugin formats the import settings that open-java-format writes by. The IDE inserts imports
 * through auto-import, Alt+Enter and completion by these settings, and no import optimizer sees them: with the IDE's
 * defaults, five classes from one package become {@code import java.util.*;}, which open-java-format keeps.
 *
 * <p>The platform applies only the first modifier that changes a file's settings, and EditorConfig's is registered
 * first: in a project whose {@code .editorconfig} sets code style properties for Java files, these settings do not
 * apply, and its {@code ij_java_*} properties can set them there instead.
 */
final class PalantirJavaFormatCodeStyleSettingsModifier implements CodeStyleSettingsModifier {
    // More names than a file imports from one package, so that an import on demand is never written.
    private static final int NEVER_IMPORT_ON_DEMAND = 999;

    @Override
    public boolean modifySettings(TransientCodeStyleSettings settings, PsiFile file) {
        if (!JavaFileType.INSTANCE.equals(file.getFileType())
                || !PalantirJavaFormatSettings.getInstance(file.getProject()).isEnabled()) {
            return false;
        }
        var javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
        javaSettings.USE_SINGLE_CLASS_IMPORTS = true;
        javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = NEVER_IMPORT_ON_DEMAND;
        javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = NEVER_IMPORT_ON_DEMAND;
        javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(new PackageEntryTable());
        // Static imports, a blank line, then the others, each group in the order of its names: ImportOrderer's.
        javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
        var importLayout = new PackageEntryTable();
        importLayout.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
        importLayout.addEntry(PackageEntry.BLANK_LINE_ENTRY);
        importLayout.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
        javaSettings.IMPORT_LAYOUT_TABLE.copyFrom(importLayout);
        return true;
    }

    // The Code Style page then says that its settings may be overridden by open-java-format.
    @Override
    public boolean mayOverrideSettingsOf(Project project) {
        return PalantirJavaFormatSettings.getInstance(project).isEnabled();
    }

    @Override
    public String getName() {
        return "open-java-format";
    }

    @Override
    @Nullable
    public CodeStyleStatusBarUIContributor getStatusBarUiContributor(TransientCodeStyleSettings transientSettings) {
        return null;
    }

    // What the Disable link next to that message runs: these settings come with the plugin, so it switches the plugin
    // off for the project, as unticking it on the plugin's settings page does.
    @Override
    public Consumer<CodeStyleSettings> getDisablingFunction(Project project) {
        return settings -> PalantirJavaFormatSettings.getInstance(project)
                .setEnabled(PalantirJavaFormatSettings.disabledState(project));
    }
}
