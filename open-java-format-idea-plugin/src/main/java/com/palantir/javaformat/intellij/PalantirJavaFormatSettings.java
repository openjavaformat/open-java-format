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
import com.palantir.javaformat.java.FormatterService;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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
     * How the formatter runs. A file written before there was a choice says only whether the native binary runs: it
     * goes on running, and every other project moves from a JVM of its own into the IDE's.
     */
    FormatterMode getFormatterMode() {
        for (var mode : FormatterMode.values()) {
            if (mode.name().equals(state.formatterMode)) {
                return mode;
            }
        }
        return state.useNativeImage ? FormatterMode.NATIVE : FormatterMode.IN_PROCESS;
    }

    void setFormatterMode(FormatterMode mode) {
        state.formatterMode = mode.name();
        // All that a plugin from before the choice reads, in a file shared with one.
        state.useNativeImage = mode == FormatterMode.NATIVE;
    }

    /** The JDK of the formatter's own JVM, for when it runs in one. */
    FormatterJdk getFormatterJdk() {
        return FormatterJdk.parse(state.formatterJdk);
    }

    void setFormatterJdk(FormatterJdk jdk) {
        state.formatterJdk = jdk.stored();
    }

    /** A native binary of the user's own, run instead of the embedded one. */
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

    Optional<String> computeFormatterVersion() {
        return getImplementationClassPath()
                .map(classpath -> classpath.stream()
                        .flatMap(uri -> {
                            try {
                                JarFile jar = new JarFile(uri.getPath());
                                // Identify the implementation jar by the service it produces.
                                if (jar.getEntry("META-INF/services/" + FormatterService.class.getName()) != null) {
                                    String implementationVersion = jar.getManifest()
                                            .getMainAttributes()
                                            .getValue("Implementation-Version");
                                    return Stream.of(implementationVersion);
                                }
                                return Stream.empty();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Couldn't find implementation JAR")));
    }

    enum EnabledState {
        UNKNOWN,
        ENABLED,
        DISABLED
    }

    static class StoredSettings {

        private EnabledState enabled = EnabledState.UNKNOWN;
        private Optional<List<URI>> implementationClassPath = Optional.empty();

        public boolean useNativeImage = false;

        @Nullable
        public String formatterMode;

        @Nullable
        public String formatterJdk;

        @Nullable
        public String nativeImagePath;

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
                    + ", useNativeImage="
                    + useNativeImage
                    + ", formatterMode="
                    + formatterMode
                    + ", formatterJdk="
                    + formatterJdk
                    + ", nativeImagePath="
                    + nativeImagePath
                    + '}';
        }
    }
}
