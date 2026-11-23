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

import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.palantir.javaformat.bootstrap.BootstrappingFormatterService;
import com.palantir.javaformat.bootstrap.NativeImageFormatterService;
import com.palantir.javaformat.intellij.PalantirJavaFormatSettings.StoredSettings;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class FormatterProviderTest {

    private static final Path EMBEDDED = Path.of("/ide/system/open-java-format/native/21.0.1/open-java-format");

    private JavaCodeInsightTestFixture fixture;
    private PalantirJavaFormatSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        var projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createLightFixtureBuilder(
                        new DefaultLightProjectDescriptor(), getClass().getName());
        fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        fixture.setUp();
        settings = PalantirJavaFormatSettings.getInstance(fixture.getProject());
        settings.loadState(new StoredSettings());
    }

    @AfterEach
    void tearDown() throws Exception {
        fixture.tearDown();
    }

    @Test
    void formatsInsideTheIdeUnlessTheSettingsSayOtherwise() {
        assertThat(new FormatterProvider(Optional::empty, Optional::empty).get(fixture.getProject(), settings))
                .get()
                .isInstanceOf(InProcessFormatterService.class);
    }

    @Test
    void startsAJvmWhereTheIdeGivesNoAccessToJavac() {
        var provider = new FormatterProvider(Optional::empty, () -> Optional.of("the IDE opens nothing"));

        assertThat(provider.get(fixture.getProject(), settings))
                .get()
                .isInstanceOf(BootstrappingFormatterService.class)
                .extracting("jdkPath")
                .isEqualTo(FormatterJdks.ideRuntime().java());
    }

    @Test
    void startsTheJvmOnTheJdkTheUserChose() {
        // The formatter is cached: one created for a JDK must not outlive the choice of another.
        var home = Path.of(System.getProperty("java.home"));
        TestJdks.register("moved-here", home, "Eclipse Temurin 25", fixture.getTestRootDisposable());
        settings.setFormatterMode(FormatterMode.JVM);
        var provider = new FormatterProvider(Optional::empty, Optional::empty);

        assertThat(provider.get(fixture.getProject(), settings))
                .get()
                .extracting("jdkMajorVersion")
                .isEqualTo(Runtime.version().feature());

        settings.setJvmJdk(new FormatterJdk.Named("moved-here"));

        assertThat(provider.get(fixture.getProject(), settings))
                .get()
                .isInstanceOf(BootstrappingFormatterService.class)
                .extracting("jdkPath", "jdkMajorVersion")
                .containsExactly(home.resolve("bin").resolve("java"), 25);
    }

    @Test
    void runsTheBinaryTheUserChose() {
        settings.setFormatterMode(FormatterMode.NATIVE);
        settings.setNativeImagePath(Optional.of(Path.of("/opt/open-java-format")));

        assertThat(new FormatterProvider(Optional::empty, Optional::empty).get(fixture.getProject(), settings))
                .get()
                .isInstanceOf(NativeImageFormatterService.class);
    }

    @Test
    void switchesToTheEmbeddedBinaryOnceItIsDownloaded() {
        // The formatter is cached: one created before the download must not outlive it.
        settings.setFormatterMode(FormatterMode.NATIVE);
        var embedded = new AtomicReference<Optional<Path>>(Optional.empty());
        var provider = new FormatterProvider(embedded::get, Optional::empty);

        assertThat(provider.get(fixture.getProject(), settings))
                .get()
                .isInstanceOf(BootstrappingFormatterService.class);

        embedded.set(Optional.of(EMBEDDED));

        assertThat(provider.get(fixture.getProject(), settings)).get().isInstanceOf(NativeImageFormatterService.class);
    }
}
