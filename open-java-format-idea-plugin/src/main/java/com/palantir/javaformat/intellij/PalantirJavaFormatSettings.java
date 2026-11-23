/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.palantir.javaformat.intellij;

import com.intellij.formatting.service.FormattingService;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@State(name = "PalantirJavaFormatSettings", storages = @Storage("open-java-format.xml"))
public class PalantirJavaFormatSettings implements PersistentStateComponent<PalantirJavaFormatSettings.StoredSettings> {

    private StoredSettings state = new StoredSettings();

    static PalantirJavaFormatSettings getInstance(Project project) {
        return project.getService(PalantirJavaFormatSettings.class);
    }

    @Override
    public StoredSettings getState() {
        return state;
    }

    @Override
    public void loadState(StoredSettings state) {
        this.state = state;
        // A file from before there were modes says only whether the native binary runs. It says so this once: the
        // mode takes its place here, and the file is next written without it.
        if (Boolean.TRUE.equals(state.useNativeImage)) {
            state.formatterMode = FormatterMode.NATIVE.text();
        }
        state.useNativeImage = null;
    }

    boolean isEnabled() {
        return state.enabled.equals(EnabledState.ENABLED);
    }

    void setEnabled(boolean enabled) {
        setEnabled(enabled ? EnabledState.ENABLED : EnabledState.DISABLED);
    }

    void setEnabled(EnabledState enabled) {
        state.enabled = enabled;
    }

    /**
     * The state that switches the plugin off. The default settings (inherited by new projects) are either 'enabled' or
     * 'show notification'. There's no way to default new projects to disabled. If someone wants that, we can add
     * another checkbox, I suppose.
     */
    static EnabledState disabledState(Project project) {
        return project.isDefault() ? EnabledState.UNKNOWN : EnabledState.DISABLED;
    }

    boolean isUninitialized() {
        return state.enabled.equals(EnabledState.UNKNOWN);
    }

    /**
     * The paths to jars that provide an alternative implementation of the formatter. If set, this implementation will
     * be used instead of the bundled version.
     */
    Optional<List<URI>> getImplementationClassPath() {
        return state.implementationClassPath;
    }

    /**
     * How the formatter runs: the one setting that says so. A project that names no mode, or one that this plugin
     * does not know, formats inside the IDE: until there were modes, such a project started a JVM for every file.
     */
    FormatterMode getFormatterMode() {
        return FormatterMode.of(state.formatterMode).orElse(FormatterMode.IN_PROCESS);
    }

    void setFormatterMode(FormatterMode mode) {
        state.formatterMode = mode.text();
    }

    /** Of {@link FormatterMode#JVM}: the JDK that the formatter's own JVM runs on. */
    FormatterJdk getJvmJdk() {
        return FormatterJdk.parse(state.jvmJdk);
    }

    void setJvmJdk(FormatterJdk jdk) {
        state.jvmJdk = jdk.stored();
    }

    /** Of {@link FormatterMode#NATIVE}: a native binary of the user's own, run instead of the embedded one. */
    Optional<Path> getNativeImagePath() {
        return Optional.ofNullable(state.nativeImagePath)
                .filter(path -> !path.isBlank())
                .map(Path::of);
    }

    void setNativeImagePath(Optional<Path> nativeImagePath) {
        state.nativeImagePath = nativeImagePath.map(Path::toString).orElse(null);
    }

    boolean injectedVersionIsOutdated() {
        Optional<FormatterVersion> formatterVersion = computeFormatterVersion().flatMap(FormatterVersion::parse);
        Optional<FormatterVersion> implementationVersion =
                getImplementationVersion().flatMap(FormatterVersion::parse);

        if (formatterVersion.isEmpty() || implementationVersion.isEmpty()) {
            return true;
        }

        return formatterVersion.get().compareTo(implementationVersion.get()) < 0;
    }

    Optional<String> getImplementationVersion() {
        return ofNullable(FormattingService.EP_NAME.findExtension(PalantirJavaFormatFormattingService.class))
                .flatMap(PalantirJavaFormatFormattingService::getPluginDescriptor)
                .map(PluginDescriptor::getVersion);
    }

    /** The version of the formatter on the implementation class path, if there is one and it states its version. */
    Optional<String> computeFormatterVersion() {
        return getImplementationClassPath()
                .flatMap(classpath -> FormatterVersions.IDE.of(
                        classpath.stream().map(Path::of).toList()));
    }

    enum EnabledState {
        UNKNOWN,
        ENABLED,
        DISABLED
    }

    static class StoredSettings {

        private EnabledState enabled = EnabledState.UNKNOWN;
        private Optional<List<URI>> implementationClassPath = Optional.empty();

        /** The text of a {@link FormatterMode}. */
        @Nullable
        public String formatterMode;

        // A setting of one mode has that mode at the start of its name, so that a mode added later, such as a daemon
        // with a JDK of its own, has settings that cannot be taken for another mode's.

        @Nullable
        public String jvmJdk;

        @Nullable
        public String nativeImagePath;

        /**
         * What a file from before there were modes has for the mode. Read, and never written: loadState turns it into
         * formatterMode and empties it, and an empty setting is left out of the file.
         */
        @Nullable
        public Boolean useNativeImage;

        public void setImplementationClassPath(@Nullable List<String> value) {
            implementationClassPath = Optional.ofNullable(value)
                    .map(strings -> strings.stream().map(URI::create).toList());
        }

        @Nullable
        public List<String> getImplementationClassPath() {
            return implementationClassPath
                    .map(paths -> paths.stream().map(URI::toString).toList())
                    .orElse(null);
        }

        // enabled used to be a boolean so we use bean property methods for backwards compatibility
        public void setEnabled(@Nullable String enabledStr) {
            if (enabledStr == null) {
                enabled = EnabledState.UNKNOWN;
            } else if (Boolean.valueOf(enabledStr)) {
                enabled = EnabledState.ENABLED;
            } else {
                enabled = EnabledState.DISABLED;
            }
        }

        @Nullable
        public String getEnabled() {
            return switch (enabled) {
                case ENABLED -> "true";
                case DISABLED -> "false";
                default -> null;
            };
        }

        @Override
        public String toString() {
            return "PalantirJavaFormatSettings{"
                    + "enabled="
                    + enabled
                    + ", implementationClassPath="
                    + implementationClassPath
                    + ", formatterMode="
                    + formatterMode
                    + ", jvmJdk="
                    + jvmJdk
                    + ", nativeImagePath="
                    + nativeImagePath
                    + '}';
        }
    }
}
