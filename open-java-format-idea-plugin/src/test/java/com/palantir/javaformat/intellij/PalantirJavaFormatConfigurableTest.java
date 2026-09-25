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

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.palantir.javaformat.intellij.PalantirJavaFormatSettings.State;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The settings page used to be assembled by the IntelliJ GUI designer, whose generated code this build never wove in:
 * every bound field stayed null, so the page rendered nothing and reset() threw as soon as it was opened. It is plain
 * Java now, and these tests fail if it goes back to a form the build does not instrument.
 */
public class PalantirJavaFormatConfigurableTest {

    private JavaCodeInsightTestFixture fixture;
    private PalantirJavaFormatSettings settings;

    @BeforeEach
    public void setUp() throws Exception {
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createLightFixtureBuilder(new DefaultLightProjectDescriptor(), getClass().getName());
        fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        fixture.setUp();
        settings = PalantirJavaFormatSettings.getInstance(fixture.getProject());
    }

    @AfterEach
    public void tearDown() throws Exception {
        fixture.tearDown();
    }

    @Test
    public void buildsAPanelWithItsControls() {
        PalantirJavaFormatConfigurable configurable = new PalantirJavaFormatConfigurable(fixture.getProject());

        JComponent component = requireNonNull(configurable.createComponent());

        assertThat(findCheckBox(component)).isPresent();
    }

    @Test
    public void readsAndWritesTheEnabledSetting() throws Throwable {
        State disabled = new State();
        disabled.setEnabled("false");
        settings.loadState(disabled);

        PalantirJavaFormatConfigurable configurable = new PalantirJavaFormatConfigurable(fixture.getProject());

        EdtTestUtil.runInEdtAndWait(() -> {
            JCheckBox enable = findCheckBox(requireNonNull(configurable.createComponent()))
                    .orElseThrow();

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
    public void showsTheVersionOfThePlugin() {
        // The "Plugin version" row: the version the platform read from the plugin's own descriptor, not "unknown".
        assertThat(settings.getImplementationVersion())
                .hasValueSatisfying(
                        version -> assertThat(FormatterVersion.parse(version)).isPresent());
    }

    private static Optional<JCheckBox> findCheckBox(Component root) {
        if (root instanceof JCheckBox checkBox) {
            return Optional.of(checkBox);
        }
        if (root instanceof Container container) {
            return Arrays.stream(container.getComponents())
                    .map(PalantirJavaFormatConfigurableTest::findCheckBox)
                    .flatMap(Optional::stream)
                    .findFirst();
        }
        return Optional.empty();
    }
}
