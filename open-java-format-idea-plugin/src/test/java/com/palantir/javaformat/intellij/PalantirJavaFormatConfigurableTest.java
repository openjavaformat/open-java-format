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
import static com.google.common.collect.MoreCollectors.onlyElement;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.ActionLink;
import com.palantir.javaformat.intellij.EmbeddedNativeImage.Installed;
import com.palantir.javaformat.intellij.EmbeddedNativeImage.Status;
import com.palantir.javaformat.intellij.PalantirJavaFormatConfigurable.StatusLine;
import com.palantir.javaformat.intellij.PalantirJavaFormatSettings.StoredSettings;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settings page used to be assembled by the IntelliJ GUI designer, whose generated code this build never wove in:
 * every bound field stayed null, so the page rendered nothing and reset() threw as soon as it was opened. It is plain
 * Java now, and these tests fail if it goes back to a form the build does not instrument.
 */
final class PalantirJavaFormatConfigurableTest {

    @TempDir
    private Path dir;

    private static final String IN_PROCESS = "In the IDE process";
    private static final String SEPARATE_JVM = "In a separate JVM";
    private static final String NATIVE = "Native binary";

    private JavaCodeInsightTestFixture fixture;
    private PalantirJavaFormatSettings settings;
    private EmbeddedNativeImage embedded;
    private PalantirJavaFormatConfigurable configurable;
    private JComponent component;
    private Disposable untilTheTestEnds;

    @BeforeEach
    void setUp() throws Exception {
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createLightFixtureBuilder(new DefaultLightProjectDescriptor(), getClass().getName());
        fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        fixture.setUp();
        settings = PalantirJavaFormatSettings.getInstance(fixture.getProject());
        settings.loadState(new StoredSettings());
        settings.setEnabled(true);
        untilTheTestEnds = fixture.getTestRootDisposable();
        // Nothing downloaded, and nowhere to download from.
        embedded = new EmbeddedNativeImage(
                dir.resolve("native"), Optional.of(new NativePlatform("macos", "aarch64")), "http://127.0.0.1:1");
        // An IDE whose JVM lets the formatter reach javac.
        configurable = new PalantirJavaFormatConfigurable(fixture.getProject(), embedded, Optional::empty);
        component = requireNonNull(configurable.createComponent());
    }

    @AfterEach
    void tearDown() throws Exception {
        configurable.disposeUIResources();
        fixture.tearDown();
    }

    @Test
    void buildsAPanelWithItsControls() {
        assertThat(findCheckBox("Enable open-java-format")).isNotNull();
        assertThat(findAll(component, JRadioButton.class))
                .extracting(JRadioButton::getText)
                .containsExactly(IN_PROCESS, SEPARATE_JVM, NATIVE);
        assertThat(findAll(component, TextFieldWithBrowseButton.class)).hasSize(1);
    }

    @Test
    void hasNoChoiceOfCodeStyle() {
        // There is one style, so the page does not offer a list of them: its one list is of JDKs.
        assertThat(findAll(component, ComboBox.class)).hasSize(1);
    }

    @Test
    void runsTheFormatterInOneWayAtATime() throws Throwable {
        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            assertThat(selectedModes()).containsExactly(IN_PROCESS);

            findRadioButton(NATIVE).setSelected(true);
            assertThat(selectedModes()).containsExactly(NATIVE);

            findRadioButton(SEPARATE_JVM).setSelected(true);
            assertThat(selectedModes()).containsExactly(SEPARATE_JVM);
        });
    }

    @Test
    void readsAndWritesTheMode() throws Throwable {
        settings.setFormatterMode(FormatterMode.JVM);

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            assertThat(selectedModes()).containsExactly(SEPARATE_JVM);
            assertThat(configurable.isModified()).isFalse();

            findRadioButton(IN_PROCESS).setSelected(true);
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();
        });

        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.IN_PROCESS);
    }

    @Test
    void greysTheIdeProcessOutWhereTheFormatterCannotReachJavac() throws Throwable {
        openThePageOfAnIdeWithout("this IDE does not open java.lang to plugins");
        var ideRuntime = FormatterJdks.ideRuntime();

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();

            // What the settings have stays selected, with the reason under it and what runs instead at the bottom.
            assertThat(selectedModes()).containsExactly(IN_PROCESS);
            assertThat(findRadioButton(IN_PROCESS).isEnabled()).isFalse();
            assertThat(findAll(component, JLabel.class))
                    .extracting(JLabel::getText)
                    .contains("Not available: this IDE does not open java.lang to plugins.");
            assertThat(activeFormatter().getText())
                    .isEqualTo(
                            "%s (Java %s)\nA JVM of its own: the IDE process is not available.",
                            ideRuntime.java(), ideRuntime.javaVersion());
            assertThat(configurable.isModified()).isFalse();
        });
    }

    @Test
    void saysWhatTheIdeProcessIsGoodFor() throws Throwable {
        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();

            assertThat(findRadioButton(IN_PROCESS).isEnabled()).isTrue();
            assertThat(findAll(component, JLabel.class))
                    .extracting(JLabel::getText)
                    .contains("Fastest: nothing to start. The IDE's own Java "
                            + Runtime.version().feature() + " parses the code.");
        });
    }

    @Test
    void listsTheJdksThatCanRunTheFormatterAndGreysOutTheOthers() throws Throwable {
        TestJdks.running("this-jdk", untilTheTestEnds);
        TestJdks.register("temurin-17", dir, "Eclipse Temurin 17.0.9", untilTheTestEnds);

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            var jdk = jdk();
            assertThat(jdkLabels()).startsWith("IDE runtime", "Project SDK").contains("this-jdk", "temurin-17");
            assertThat(selectedJdkLabel()).isEqualTo("IDE runtime");

            jdk.setSelectedIndex(jdkLabels().indexOf("this-jdk"));
            assertThat(selectedJdkLabel()).isEqualTo("this-jdk");

            // Java 17 does not run the formatter: it is listed, to say why it is no choice, and stays unchosen.
            jdk.setSelectedIndex(jdkLabels().indexOf("temurin-17"));
            assertThat(selectedJdkLabel()).isEqualTo("this-jdk");
        });
    }

    @Test
    void readsAndWritesTheJdk() throws Throwable {
        TestJdks.running("this-jdk", untilTheTestEnds);
        settings.setFormatterMode(FormatterMode.JVM);
        settings.setFormatterJdk(new FormatterJdk.Named("this-jdk"));

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            assertThat(selectedJdkLabel()).isEqualTo("this-jdk");
            assertThat(configurable.isModified()).isFalse();
            assertThat(activeFormatter().getText())
                    .isEqualTo(
                            "%s (Java %s)",
                            Path.of(System.getProperty("java.home"), "bin", "java"),
                            Runtime.version().feature());

            jdk().setSelectedIndex(jdkLabels().indexOf("IDE runtime"));
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();
        });

        assertThat(settings.getFormatterJdk()).isEqualTo(new FormatterJdk.IdeRuntime());
    }

    @Test
    void refusesAJdkThatNoLongerRunsTheFormatter() throws Throwable {
        // Chosen when the IDE had it. The page shows what the settings have, and what runs instead.
        settings.setFormatterMode(FormatterMode.JVM);
        settings.setFormatterJdk(new FormatterJdk.Named("gone"));
        var ideRuntime = FormatterJdks.ideRuntime();

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            assertThat(selectedJdkLabel()).isEqualTo("gone");
            assertThat(activeFormatter().getText())
                    .isEqualTo(
                            "%s (Java %s)\nThe IDE's runtime: there is no JDK named gone.",
                            ideRuntime.java(), ideRuntime.javaVersion());

            assertThatThrownBy(configurable::apply)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Choose another JDK: there is no JDK named gone");

            // Only the JVM needs it.
            findRadioButton(IN_PROCESS).setSelected(true);
            configurable.apply();
        });

        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.IN_PROCESS);
    }

    @Test
    void readsAndWritesTheEnabledSetting() throws Throwable {
        var disabled = new StoredSettings();
        disabled.setEnabled("false");
        settings.loadState(disabled);

        EdtTestUtil.runInEdtAndWait(() -> {
            JCheckBox enable = findCheckBox("Enable open-java-format");

            configurable.reset();
            assertThat(enable.isSelected()).isFalse();
            assertThat(configurable.isModified()).isFalse();

            enable.setSelected(true);
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();
        });

        assertThat(settings.isEnabled()).isTrue();
    }

    @Test
    void refreshesTheCodeStyleOfOpenFilesWhenOpenJavaFormatIsSwitchedOnOrOff() throws Throwable {
        // The import settings of each file are cached, and the plugin sets them while it is on.
        var tracker = CodeStyle.getSettings(fixture.getProject()).getModificationTracker();
        var countBefore = tracker.getModificationCount();

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            configurable.apply();
            assertThat(tracker.getModificationCount()).isEqualTo(countBefore);

            findCheckBox("Enable open-java-format").setSelected(false);
            configurable.apply();
        });

        assertThat(tracker.getModificationCount()).isGreaterThan(countBefore);
    }

    @Test
    void sitsUnderTools() {
        var page = Configurable.PROJECT_CONFIGURABLE.getExtensions(fixture.getProject()).stream()
                .filter(extension -> "open-java-format.settings".equals(extension.id))
                .findFirst()
                .orElseThrow();

        assertThat(page.parentId).isEqualTo("tools");
    }

    @Test
    void hasNoRowsOfVersions() {
        // The plugin's version and the formatter's told the user nothing to act on.
        assertThat(findAll(component, JLabel.class))
                .extracting(JLabel::getText)
                .doesNotContain("Plugin version", "Implementation version");
    }

    @Test
    void asksNothingOfTheChoicesOnceOpenJavaFormatIsSwitchedOff() throws Throwable {
        // They are not on the page then, so there is nothing on it to put right.
        settings.setFormatterMode(FormatterMode.JVM);
        settings.setFormatterJdk(new FormatterJdk.Named("gone"));

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            findCheckBox("Enable open-java-format").setSelected(false);
            configurable.apply();

            configurable.reset();
            findRadioButton(NATIVE).setSelected(true);
            configurable.apply();
        });

        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.NATIVE);
    }

    @Test
    void showsTheWaysToRunTheFormatterOnlyWhileOpenJavaFormatIsOn() throws Throwable {
        EdtTestUtil.runInEdtAndWait(() -> {
            var enable = findCheckBox("Enable open-java-format");
            var nativeImage = findRadioButton(NATIVE);
            var path = findAll(component, TextFieldWithBrowseButton.class).get(0);
            configurable.reset();
            assertThat(isDisplayed(nativeImage)).isTrue();
            assertThat(isDisplayed(path)).isTrue();

            enable.setSelected(false);
            assertThat(isDisplayed(findRadioButton(IN_PROCESS))).isFalse();
            assertThat(isDisplayed(nativeImage)).isFalse();
            assertThat(isDisplayed(path)).isFalse();
            assertThat(isDisplayed(activeFormatter())).isFalse();

            enable.setSelected(true);
            assertThat(isDisplayed(nativeImage)).isTrue();
            assertThat(isDisplayed(path)).isTrue();
        });
    }

    @Test
    void showsTheActiveFormatterAsThePageStands() throws Throwable {
        // What runs once the page is applied, so it follows the choices and the path as they change.
        var pluginVersion = settings.getImplementationVersion().orElseThrow();

        var ideRuntime = FormatterJdks.ideRuntime();

        EdtTestUtil.runInEdtAndWait(() -> {
            var path = findAll(component, TextFieldWithBrowseButton.class).get(0);
            configurable.reset();
            assertThat(activeFormatter().getText())
                    .isEqualTo("The IDE process (Java %s)", System.getProperty("java.version"));

            findRadioButton(NATIVE).setSelected(true);
            assertThat(activeFormatter().getText())
                    .isEqualTo("Need to download version %s from Maven Central", pluginVersion);

            path.setText("/opt/open-java-format");
            assertThat(activeFormatter().getText()).isEqualTo("/opt/open-java-format");

            findRadioButton(SEPARATE_JVM).setSelected(true);
            assertThat(activeFormatter().getText())
                    .isEqualTo("%s (Java %s)", ideRuntime.java(), ideRuntime.javaVersion());
        });
    }

    @Test
    void showsTheEmbeddedBinaryOnceItIsThere() throws Throwable {
        var binary = installByHand("21.0.1");
        settings.setFormatterMode(FormatterMode.NATIVE);

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();

            assertThat(activeFormatter().getText()).isEqualTo(binary.toString());
        });
    }

    @Test
    void showsTheVersionOfThePlugin() {
        // The version the plugin downloads the native binary of: the one the platform read from the plugin's own
        // descriptor, not "unknown". Whatever the build stamped: CI checks out without tags, so there it is a commit
        // hash rather than a release-like version.
        assertThat(settings.getImplementationVersion())
                .hasValueSatisfying(version -> assertThat(version).isNotBlank());
    }

    @Test
    void offersTheOptionsOfTheChosenWayOnly() throws Throwable {
        // The others stay on the page, greyed: hidden, they would move the page about with every choice.
        EdtTestUtil.runInEdtAndWait(() -> {
            var path = findAll(component, TextFieldWithBrowseButton.class).get(0);

            configurable.reset();
            assertThat(path.isEnabled()).isFalse();
            assertThat(jdk().isEnabled()).isFalse();

            findRadioButton(NATIVE).setSelected(true);
            assertThat(path.isEnabled()).isTrue();
            assertThat(jdk().isEnabled()).isFalse();

            findRadioButton(SEPARATE_JVM).setSelected(true);
            assertThat(path.isEnabled()).isFalse();
            assertThat(jdk().isEnabled()).isTrue();
        });
    }

    @Test
    void readsAndWritesTheNativeBinarySettings() throws Throwable {
        var binary = executable("open-java-format");
        settings.setFormatterMode(FormatterMode.NATIVE);

        EdtTestUtil.runInEdtAndWait(() -> {
            var path = findAll(component, TextFieldWithBrowseButton.class).get(0);

            configurable.reset();
            assertThat(selectedModes()).containsExactly(NATIVE);
            assertThat(path.getText()).isEmpty();
            assertThat(configurable.isModified()).isFalse();

            path.setText(binary.toString());
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();
        });

        assertThat(settings.getFormatterMode()).isEqualTo(FormatterMode.NATIVE);
        assertThat(settings.getNativeImagePath()).hasValue(binary);
    }

    @Test
    void refusesTheNativeBinaryWithoutABinary() throws Throwable {
        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            findRadioButton(NATIVE).setSelected(true);

            assertThatThrownBy(configurable::apply)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Download the embedded version or choose a binary");
        });

        assertThat(settings.getFormatterMode()).isNotEqualTo(FormatterMode.NATIVE);
    }

    @Test
    void refusesAPathThatIsNotAnExecutableFile() throws Throwable {
        var missing = dir.resolve("missing");

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();
            findRadioButton(NATIVE).setSelected(true);
            findAll(component, TextFieldWithBrowseButton.class).get(0).setText(missing.toString());

            assertThatThrownBy(configurable::apply)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("%s is not an executable file", missing);
        });
    }

    @Test
    void offersADownloadBeforeTheFirstOne() throws Throwable {
        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();

            assertThat(findAll(component, JLabel.class))
                    .extracting(JLabel::getText)
                    .contains("No embedded version.");
            assertThat(findAll(component, ActionLink.class)).singleElement().satisfies(link -> {
                assertThat(link.getText()).isEqualTo("Download");
                assertThat(link.isVisible()).isTrue();
            });
        });
    }

    @Test
    void wrapsTheActiveFormatterAndLetsItsPathBeCopied() throws Throwable {
        // The embedded binary lives deep in the IDE's system directory: on one line its path made the page wider than
        // the dialog, and a label would not let anyone copy it. A long path of one's own in Binary must not either.
        var path = Path.of("/Users/someone/Library/Caches/JetBrains/IntelliJIdea2026.2/open-java-format/native/"
                + "21.0.1/".repeat(40) + "open-java-format-macos_aarch64");
        settings.setFormatterMode(FormatterMode.NATIVE);
        settings.setNativeImagePath(Optional.of(path));

        EdtTestUtil.runInEdtAndWait(() -> {
            configurable.reset();

            var formatterType = activeFormatter();
            assertThat(formatterType.getText()).isEqualTo(path.toString());
            assertThat(formatterType.isEditable()).isFalse();
            assertThat(formatterType.getLineWrap()).isTrue();
            assertThat(component.getPreferredSize().width).isLessThan(1000);

            // Laid out at a dialog's width, the path takes several lines rather than one cut off.
            component.setSize(600, 800);
            layOut(component);
            layOut(component);
            var line = formatterType.getFontMetrics(formatterType.getFont()).getHeight();
            assertThat(formatterType.getHeight()).isGreaterThan(2 * line);
        });
    }

    @Test
    void describesEveryStateOfTheEmbeddedBinary() {
        var installed = new Installed("21.0.1", Path.of("/system/open-java-format/native/21.0.1/open-java-format"));

        assertThat(PalantirJavaFormatConfigurable.statusLine(new Status.NotDownloaded()))
                .isEqualTo(new StatusLine("No embedded version.", Optional.of("Download")));
        assertThat(PalantirJavaFormatConfigurable.statusLine(new Status.Downloading("21.0.2")))
                .isEqualTo(new StatusLine("Downloading embedded version 21.0.2…", Optional.empty()));
        assertThat(PalantirJavaFormatConfigurable.statusLine(new Status.DownloadFailed("21.0.2", "connection refused")))
                .isEqualTo(new StatusLine("Download failed: connection refused.", Optional.of("Retry")));
        // A link only when there is something to download: the plugin's own version, named on the link.
        assertThat(PalantirJavaFormatConfigurable.statusLine(new Status.Current(installed)))
                .isEqualTo(new StatusLine("Use embedded version 21.0.1", Optional.empty()));
        assertThat(PalantirJavaFormatConfigurable.statusLine(new Status.OtherVersion(installed, "21.0.2")))
                .isEqualTo(new StatusLine("Use embedded version 21.0.1", Optional.of("Update to 21.0.2")));
        assertThat(PalantirJavaFormatConfigurable.statusLine(new Status.NoImageForPlatform()))
                .satisfies(line -> {
                    assertThat(line.text()).startsWith("No native binary is published for ");
                    assertThat(line.action()).isEmpty();
                });
    }

    /** The page as an IDE shows it whose JVM does not let the formatter reach javac. */
    private void openThePageOfAnIdeWithout(String javacAccess) {
        configurable.disposeUIResources();
        configurable =
                new PalantirJavaFormatConfigurable(fixture.getProject(), embedded, () -> Optional.of(javacAccess));
        component = requireNonNull(configurable.createComponent());
    }

    private JCheckBox findCheckBox(String text) {
        return findAll(component, JCheckBox.class).stream()
                .filter(checkBox -> checkBox.getText().equals(text))
                .findFirst()
                .orElseThrow();
    }

    private JRadioButton findRadioButton(String text) {
        return findAll(component, JRadioButton.class).stream()
                .filter(radioButton -> radioButton.getText().equals(text))
                .findFirst()
                .orElseThrow();
    }

    private List<String> selectedModes() {
        return findAll(component, JRadioButton.class).stream()
                .filter(JRadioButton::isSelected)
                .map(JRadioButton::getText)
                .toList();
    }

    /** The page's one list. */
    private ComboBox<?> jdk() {
        return findAll(component, ComboBox.class).stream().collect(onlyElement());
    }

    /** The names in the list of JDKs, without what stands beside them in grey. */
    private List<String> jdkLabels() {
        var model = jdk().getModel();
        return IntStream.range(0, model.getSize())
                .mapToObj(index -> jdkLabel(model.getElementAt(index)))
                .toList();
    }

    private String selectedJdkLabel() {
        return jdkLabel(requireNonNull(jdk().getSelectedItem()));
    }

    /** As the list draws the JDK. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String jdkLabel(Object choice) {
        var jdk = (ComboBox) jdk();
        var drawn = (SimpleColoredComponent)
                jdk.getRenderer().getListCellRendererComponent(new JList<>(), choice, 0, false, false);
        return drawn.getCharSequence(true).toString();
    }

    /** Whether the component and every container up to the page are visible. */
    private boolean isDisplayed(Component target) {
        for (var current = target; current != null && current != component; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    /** The text under Active formatter: the page's one text area. */
    private JTextArea activeFormatter() {
        return findAll(component, JTextArea.class).stream().collect(onlyElement());
    }

    private Path installByHand(String version) throws IOException {
        var binary = Files.createDirectories(dir.resolve("native").resolve(version))
                .resolve("open-java-format-macos_aarch64");
        Files.writeString(binary, "#!/bin/sh\n");
        assertThat(binary.toFile().setExecutable(true)).isTrue();
        return binary;
    }

    private Path executable(String name) throws IOException {
        var binary = Files.writeString(dir.resolve(name), "#!/bin/sh\n");
        assertThat(binary.toFile().setExecutable(true)).isTrue();
        return binary;
    }

    private static void layOut(Component root) {
        if (root instanceof Container container) {
            container.doLayout();
            Arrays.stream(container.getComponents()).forEach(PalantirJavaFormatConfigurableTest::layOut);
        }
    }

    private static <T extends Component> List<T> findAll(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return List.of(type.cast(root));
        }
        if (root instanceof Container container) {
            return Arrays.stream(container.getComponents())
                    .flatMap(child -> findAll(child, type).stream())
                    .toList();
        }
        return List.of();
    }
}
