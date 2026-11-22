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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.palantir.javaformat.intellij.EmbeddedNativeImage.Status;
import com.palantir.javaformat.intellij.FormatterJdk.Unusable;
import com.palantir.javaformat.intellij.FormatterJdk.Usable;
import com.palantir.javaformat.intellij.FormatterSelection.Fallback;
import com.palantir.javaformat.intellij.PalantirJavaFormatSettings.EnabledState;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import org.jetbrains.annotations.Nls;
import org.jspecify.annotations.Nullable;

class PalantirJavaFormatConfigurable extends BaseConfigurable implements SearchableConfigurable {

    private final Project project;
    private final EmbeddedNativeImage embeddedNativeImage;
    private final Supplier<Optional<String>> inProcessUnavailable;
    private final JPanel panel;
    private final JCheckBox enable;
    private final JPanel modes;
    private final JRadioButton inProcess;
    private final JLabel inProcessNote;
    private final JRadioButton separateJvm;
    private final JdkChoices jdks = new JdkChoices();
    private final JPanel jdkOptions;
    private final JRadioButton nativeImage;
    private final JPanel nativeImageOptions;
    private final JLabel embeddedStatus;
    private final ActionLink embeddedAction;
    private final TextFieldWithBrowseButton nativeImagePath;
    private final JPanel activeFormatterRow;
    private final JTextArea activeFormatter;

    // Lives from createComponent() to disposeUIResources(): the download reports its progress to the page.
    @Nullable
    private Disposable uiDisposable;

    public PalantirJavaFormatConfigurable(Project project) {
        this(project, EmbeddedNativeImage.getInstance(), InProcessFormatters.IDE::unavailable);
    }

    /** inProcessUnavailable: why the formatter cannot run in the IDE's own JVM, if it cannot. */
    PalantirJavaFormatConfigurable(
            Project project, EmbeddedNativeImage embeddedNativeImage, Supplier<Optional<String>> inProcessUnavailable) {
        this.project = project;
        this.embeddedNativeImage = embeddedNativeImage;
        this.inProcessUnavailable = inProcessUnavailable;
        this.enable = new JCheckBox("Enable open-java-format");
        this.inProcess = new JRadioButton("In the IDE process");
        this.inProcessNote = new JBLabel();
        inProcessNote.setForeground(UIUtil.getContextHelpForeground());
        this.separateJvm = new JRadioButton("In a separate JVM");
        this.nativeImage = new JRadioButton("Native binary");
        var mode = new ButtonGroup();
        mode.add(inProcess);
        mode.add(separateJvm);
        mode.add(nativeImage);
        var jdk = new ComboBox<>(jdks);
        jdk.setRenderer(new JdkChoiceRenderer());
        this.embeddedStatus = new JLabel();
        this.embeddedAction = new ActionLink();
        embeddedAction.addActionListener(event -> embeddedNativeImage.download(project, pluginVersion()));
        var pathField = new JBTextField();
        pathField.getEmptyText().setText("Leave empty to use the embedded version");
        // A fixed preferred width, which the form stretches: sized to a long path, it would widen the page instead.
        pathField.setColumns(30);
        this.nativeImagePath = new TextFieldWithBrowseButton(pathField);
        nativeImagePath.addBrowseFolderListener(new TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Native Binary"), project));
        this.activeFormatter = selectableText();

        var statusRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        statusRow.add(embeddedStatus);
        statusRow.add(Box.createHorizontalStrut(JBUI.scale(6)));
        statusRow.add(embeddedAction);
        this.jdkOptions = underItsMode().addLabeledComponent("JDK", jdk).getPanel();
        this.nativeImageOptions = underItsMode()
                .addComponent(statusRow)
                .addLabeledComponent("Binary", nativeImagePath)
                .getPanel();
        // The options of every mode stay on the page, those of the modes not chosen greyed: hidden, they would move
        // the modes under them each time another is chosen.
        this.modes = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("Run the formatter"))
                .addComponent(inProcess)
                .addComponent(underItsMode().addComponent(inProcessNote).getPanel())
                .addComponent(separateJvm)
                .addComponent(jdkOptions)
                .addComponent(nativeImage)
                .addComponent(nativeImageOptions)
                .getPanel();
        // Filling its own form vertically: only then does the form put the label above the text, rather than beside
        // the middle of a wrapped path.
        this.activeFormatterRow = FormBuilder.createFormBuilder()
                .addSeparator()
                .addLabeledComponentFillVertically("Active formatter", activeFormatter)
                .getPanel();

        // The page shows what runs once it is applied, so it follows every choice and the path as they change.
        enable.addItemListener(event -> showPage());
        inProcess.addItemListener(event -> showPage());
        separateJvm.addItemListener(event -> showPage());
        nativeImage.addItemListener(event -> showPage());
        jdk.addItemListener(event -> showActiveFormatter());
        nativeImagePath.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent event) {
                showActiveFormatter();
            }
        });

        // Laid out here rather than by the IntelliJ GUI designer. A .form is woven into the class at build
        // time, and this build does not instrument bytecode (see build.gradle), which left every field
        // above null: the page rendered nothing and reset() threw the moment it was opened.
        this.panel = FormBuilder.createFormBuilder()
                .addComponent(enable)
                .addComponent(modes)
                .addComponent(activeFormatterRow)
                // Takes the rest of the page, so the rows stay at the top whichever of them are hidden.
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public String getId() {
        return "open-java-format.settings";
    }

    @Override
    @Nullable
    public Runnable enableSearch(String option) {
        return null;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "open-java-format Settings";
    }

    @Override
    @Nullable
    public String getHelpTopic() {
        return null;
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        if (uiDisposable == null) {
            uiDisposable = Disposer.newDisposable("open-java-format settings page");
            embeddedNativeImage.addChangeListener(this::showEmbeddedNativeImage, uiDisposable);
        }
        return panel;
    }

    @Override
    public void apply() throws ConfigurationException {
        PalantirJavaFormatSettings settings = PalantirJavaFormatSettings.getInstance(project);
        var mode = selectedMode();
        var path = enteredNativeImagePath();
        // Only of what the page shows: switched off, it has nothing on it to put right.
        if (enable.isSelected() && mode == FormatterMode.NATIVE) {
            if (path.isPresent() && !(Files.isRegularFile(path.get()) && Files.isExecutable(path.get()))) {
                throw new ConfigurationException(path.get() + " is not an executable file");
            }
            if (path.isEmpty() && embeddedNativeImage.installed().isEmpty()) {
                throw new ConfigurationException("Download the embedded version or choose a binary");
            }
        }
        // A JDK that stopped being usable since it was chosen: the list lets no other such one be chosen.
        if (enable.isSelected()
                && mode == FormatterMode.JVM
                && FormatterJdks.resolve(project, selectedJdk()) instanceof Unusable unusable) {
            throw new ConfigurationException("Choose another JDK: " + unusable.reason());
        }
        var wasEnabled = settings.isEnabled();
        settings.setEnabled(
                enable.isSelected() ? EnabledState.ENABLED : PalantirJavaFormatSettings.disabledState(project));
        settings.setFormatterMode(mode);
        settings.setFormatterJdk(selectedJdk());
        settings.setNativeImagePath(path);
        if (settings.isEnabled() != wasEnabled) {
            // The Java import settings of each file are cached, and PalantirJavaFormatCodeStyleSettingsModifier sets
            // them while the plugin is enabled.
            CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged();
        }
    }

    @Override
    public void reset() {
        PalantirJavaFormatSettings settings = PalantirJavaFormatSettings.getInstance(project);
        enable.setSelected(settings.isEnabled());
        switch (settings.getFormatterMode()) {
            case IN_PROCESS -> inProcess.setSelected(true);
            case JVM -> separateJvm.setSelected(true);
            case NATIVE -> nativeImage.setSelected(true);
        }
        showJdks(settings.getFormatterJdk());
        nativeImagePath.setText(
                settings.getNativeImagePath().map(Path::toString).orElse(""));
        showPage();
    }

    @Override
    public boolean isModified() {
        PalantirJavaFormatSettings settings = PalantirJavaFormatSettings.getInstance(project);
        Optional<Path> path;
        try {
            path = enteredNativeImagePath();
        } catch (ConfigurationException e) {
            return true;
        }
        return enable.isSelected() != settings.isEnabled()
                || selectedMode() != settings.getFormatterMode()
                || !selectedJdk().equals(settings.getFormatterJdk())
                || !path.equals(settings.getNativeImagePath());
    }

    @Override
    public void disposeUIResources() {
        if (uiDisposable != null) {
            Disposer.dispose(uiDisposable);
            uiDisposable = null;
        }
    }

    record StatusLine(String text, Optional<String> action) {}

    /**
     * The line under Native binary, and the link next to it, which downloads the plugin's version. There is a link
     * only when there is something to download.
     */
    static StatusLine statusLine(Status status) {
        return switch (status) {
            case Status.NoImageForPlatform noImage ->
                new StatusLine(
                        "No native binary is published for " + System.getProperty("os.name") + " "
                                + System.getProperty("os.arch") + ".",
                        Optional.empty());
            case Status.NotDownloaded notDownloaded -> new StatusLine("No embedded version.", Optional.of("Download"));
            case Status.Downloading downloading ->
                new StatusLine("Downloading embedded version " + downloading.version() + "…", Optional.empty());
            case Status.DownloadFailed failed ->
                new StatusLine("Download failed: " + failed.reason() + ".", Optional.of("Retry"));
            case Status.Current current ->
                new StatusLine("Use embedded version " + current.installed().version(), Optional.empty());
            // The plugin's own version, which formats as the formatter bundled with it does.
            case Status.OtherVersion other ->
                new StatusLine(
                        "Use embedded version " + other.installed().version(),
                        Optional.of("Update to " + other.pluginVersion()));
        };
    }

    private void showPage() {
        modes.setVisible(enable.isSelected());
        activeFormatterRow.setVisible(enable.isSelected());
        var unavailable = inProcessUnavailable.get();
        // Left selected when the settings have it and it cannot run here: the page shows what is stored, why it does
        // not run, and under Active formatter what runs instead.
        inProcess.setEnabled(unavailable.isEmpty());
        inProcessNote.setText(unavailable
                .map(reason -> "Not available: " + reason + ".")
                .orElseGet(() -> "Fastest: nothing to start. The IDE's own Java "
                        + Runtime.version().feature() + " parses the code."));
        UIUtil.setEnabled(jdkOptions, separateJvm.isSelected(), true);
        UIUtil.setEnabled(nativeImageOptions, nativeImage.isSelected(), true);
        showEmbeddedNativeImage();
    }

    private void showEmbeddedNativeImage() {
        var line = statusLine(embeddedNativeImage.status(pluginVersion()));
        embeddedStatus.setText(line.text());
        embeddedAction.setText(line.action().orElse(""));
        embeddedAction.setVisible(line.action().isPresent());
        // The embedded binary can arrive while the page is open.
        showActiveFormatter();
    }

    /**
     * The JDKs to choose from, as they are now: the IDE's can change between two visits of the page. stored: what the
     * settings have, listed even when the IDE no longer has a JDK of that name.
     */
    private void showJdks(FormatterJdk stored) {
        var choices = new ArrayList<>(FormatterJdks.choices());
        if (!choices.contains(stored)) {
            choices.add(stored);
        }
        jdks.removeAllElements();
        for (var choice : choices) {
            var jdkChoice = new JdkChoice(
                    choice,
                    FormatterJdks.label(project, choice),
                    FormatterJdks.resolve(project, choice) instanceof Usable);
            jdks.addElement(jdkChoice);
            if (choice.equals(stored)) {
                jdks.show(jdkChoice);
            }
        }
    }

    private FormatterMode selectedMode() {
        if (inProcess.isSelected()) {
            return FormatterMode.IN_PROCESS;
        }
        return nativeImage.isSelected() ? FormatterMode.NATIVE : FormatterMode.JVM;
    }

    private FormatterJdk selectedJdk() {
        return jdks.getSelectedItem() instanceof JdkChoice choice ? choice.jdk() : new FormatterJdk.IdeRuntime();
    }

    /** The formatter the page would run once applied, chosen as FormatterProvider chooses it. */
    private void showActiveFormatter() {
        var pending = new PalantirJavaFormatSettings();
        pending.setFormatterMode(selectedMode());
        pending.setFormatterJdk(selectedJdk());
        try {
            pending.setNativeImagePath(enteredNativeImagePath());
        } catch (ConfigurationException e) {
            // Not a path at all: shown as typed, and Apply says what is wrong with it.
            activeFormatter.setText(nativeImagePath.getText().strip());
            return;
        }
        activeFormatter.setText(
                switch (FormatterSelection.of(
                        pending,
                        FormatterSelection.in(project, inProcessUnavailable, embeddedNativeImage::installedPath))) {
                    case FormatterSelection.InProcess inProcessFormatter ->
                        "The IDE process (Java " + System.getProperty("java.version") + ")";
                    case FormatterSelection.NativeImage nativeImageFormatter ->
                        nativeImageFormatter.path().toString();
                    case FormatterSelection.SeparateJvm jvm
                    when jvm.fallbacks().contains(new Fallback.NativeImageMissing()) ->
                        embeddedNativeImage.status(pluginVersion()) instanceof Status.NoImageForPlatform
                                ? "Choose a native binary: none is published for this platform"
                                : "Need to download version " + pluginVersion() + " from Maven Central";
                    case FormatterSelection.SeparateJvm jvm -> separateJvmText(jvm);
                });
    }

    /** The java that runs, and under it why it is not what the page asks for, when it is not. */
    static String separateJvmText(FormatterSelection.SeparateJvm jvm) {
        var lines = new ArrayList<String>();
        lines.add(jvm.java() + " (Java " + jvm.javaVersion() + ")");
        for (var fallback : jvm.fallbacks()) {
            switch (fallback) {
                case Fallback.InProcessUnavailable unavailable ->
                    lines.add("A JVM of its own: the IDE process is not available.");
                case Fallback.JdkUnusable unusable -> lines.add("The IDE's runtime: " + unusable.reason() + ".");
                case Fallback.NativeImageMissing missing -> lines.add("A JVM of its own: there is no native binary.");
            }
        }
        return String.join("\n", lines);
    }

    private String pluginVersion() {
        return PalantirJavaFormatSettings.getInstance(project)
                .getImplementationVersion()
                .orElse("unknown");
    }

    private Optional<Path> enteredNativeImagePath() throws ConfigurationException {
        var text = nativeImagePath.getText().strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(text));
        } catch (InvalidPathException e) {
            throw new ConfigurationException(text + " is not a path: " + e.getReason());
        }
    }

    /**
     * Read-only text that wraps at the page's width and can be selected and copied: the active formatter is often a
     * path, and the embedded binary's, deep in the IDE's system directory, would otherwise widen the page past the
     * dialog.
     */
    private static JTextArea selectableText() {
        var text = new JBTextArea() {
            @Override
            public Dimension getPreferredSize() {
                // No wider than a short line: the form stretches it to the page, and once it has that width, the
                // height is that of its lines wrapped to it.
                var preferred = super.getPreferredSize();
                return new Dimension(Math.min(preferred.width, JBUI.scale(200)), preferred.height);
            }
        };
        // The height depends on the width the layout gives, which is known only after the layout.
        text.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                text.revalidate();
            }
        });
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(JBUI.Borders.empty());
        text.setFont(JBUI.Fonts.label());
        return text;
    }

    /** The options of a mode, indented under its radio button. */
    private static FormBuilder underItsMode() {
        return FormBuilder.createFormBuilder().setFormLeftIndent(JBUI.scale(20));
    }

    /** usable: whether the formatter can run on it, which only Java 21 or later that is on this machine can. */
    private record JdkChoice(FormatterJdk jdk, FormatterJdks.Label label, boolean usable) {}

    /** Lets a JDK be chosen only if the formatter can run on it. The others stay in the list, greyed. */
    private static final class JdkChoices extends DefaultComboBoxModel<JdkChoice> {
        @Override
        public void setSelectedItem(@Nullable Object choice) {
            if (choice instanceof JdkChoice jdkChoice && jdkChoice.usable()) {
                super.setSelectedItem(choice);
            }
        }

        /** What the settings have, usable or not. */
        void show(JdkChoice stored) {
            super.setSelectedItem(stored);
        }
    }

    private static final class JdkChoiceRenderer extends ColoredListCellRenderer<JdkChoice> {
        @Override
        protected void customizeCellRenderer(
                JList<? extends JdkChoice> list,
                @Nullable JdkChoice choice,
                int index,
                boolean selected,
                boolean focus) {
            if (choice == null) {
                return;
            }
            append(
                    choice.label().name(),
                    choice.usable() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("  " + choice.label().detail(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }
}
