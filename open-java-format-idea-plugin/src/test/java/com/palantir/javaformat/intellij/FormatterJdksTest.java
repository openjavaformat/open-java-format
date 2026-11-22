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

import com.intellij.openapi.Disposable;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.palantir.javaformat.intellij.FormatterJdk.IdeRuntime;
import com.palantir.javaformat.intellij.FormatterJdk.Named;
import com.palantir.javaformat.intellij.FormatterJdk.ProjectSdk;
import com.palantir.javaformat.intellij.FormatterJdk.Unusable;
import com.palantir.javaformat.intellij.FormatterJdk.Usable;
import com.palantir.javaformat.intellij.FormatterJdks.Label;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FormatterJdksTest {

    private static final Path RUNNING_JAVA = Path.of(System.getProperty("java.home"), "bin", "java");
    private static final int RUNNING_VERSION = Runtime.version().feature();

    @TempDir
    private Path dir;

    private JavaCodeInsightTestFixture fixture;
    private Disposable untilTheTestEnds;

    @BeforeEach
    void setUp() throws Exception {
        var projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createLightFixtureBuilder(
                        new DefaultLightProjectDescriptor(), getClass().getName());
        fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        fixture.setUp();
        untilTheTestEnds = fixture.getTestRootDisposable();
    }

    @AfterEach
    void tearDown() throws Exception {
        fixture.tearDown();
    }

    @Test
    void runsOnTheIdesRuntime() {
        assertThat(FormatterJdks.resolve(fixture.getProject(), new IdeRuntime()))
                .isEqualTo(new Usable(RUNNING_JAVA, RUNNING_VERSION));
        assertThat(FormatterJdks.label(fixture.getProject(), new IdeRuntime()))
                .isEqualTo(new Label("IDE runtime", "Java " + System.getProperty("java.version")));
    }

    @Test
    void runsOnAJdkOfTheIde() {
        TestJdks.running("this-jdk", untilTheTestEnds);

        assertThat(FormatterJdks.resolve(fixture.getProject(), new Named("this-jdk")))
                .isEqualTo(new Usable(RUNNING_JAVA, RUNNING_VERSION));
        assertThat(FormatterJdks.label(fixture.getProject(), new Named("this-jdk")))
                .isEqualTo(new Label("this-jdk", "Java " + RUNNING_VERSION));
    }

    @Test
    void runsOnTheProjectSdk() {
        TestJdks.setProjectSdk(fixture.getProject(), TestJdks.running("this-jdk", untilTheTestEnds), untilTheTestEnds);

        assertThat(FormatterJdks.resolve(fixture.getProject(), new ProjectSdk()))
                .isEqualTo(new Usable(RUNNING_JAVA, RUNNING_VERSION));
        assertThat(FormatterJdks.label(fixture.getProject(), new ProjectSdk()))
                .isEqualTo(new Label("Project SDK", "this-jdk, Java " + RUNNING_VERSION));
    }

    @Test
    void doesNotRunOnTheSdkOfAProjectWithoutOne() {
        TestJdks.setProjectSdk(fixture.getProject(), null, untilTheTestEnds);

        assertThat(FormatterJdks.resolve(fixture.getProject(), new ProjectSdk()))
                .isEqualTo(new Unusable("the project has no SDK"));
        assertThat(FormatterJdks.label(fixture.getProject(), new ProjectSdk()))
                .isEqualTo(new Label("Project SDK", "none"));
    }

    @Test
    void doesNotRunOnAJdkOlderThanTheFormatterNeeds() {
        // The formatter is compiled for Java 21, and a project SDK can be any version.
        TestJdks.setProjectSdk(
                fixture.getProject(),
                TestJdks.register("temurin-17", dir, "Eclipse Temurin 17.0.9", untilTheTestEnds),
                untilTheTestEnds);
        var tooOld = new Unusable("temurin-17 is Java 17, and the formatter needs Java 21 or later");

        assertThat(FormatterJdks.resolve(fixture.getProject(), new Named("temurin-17")))
                .isEqualTo(tooOld);
        assertThat(FormatterJdks.resolve(fixture.getProject(), new ProjectSdk()))
                .isEqualTo(tooOld);
        assertThat(FormatterJdks.label(fixture.getProject(), new Named("temurin-17")))
                .isEqualTo(new Label("temurin-17", "Java 17"));
    }

    @Test
    void doesNotRunOnAJdkTheIdeDoesNotHave() {
        assertThat(FormatterJdks.resolve(fixture.getProject(), new Named("gone")))
                .isEqualTo(new Unusable("there is no JDK named gone"));
        assertThat(FormatterJdks.label(fixture.getProject(), new Named("gone")))
                .isEqualTo(new Label("gone", "not found"));
    }

    @Test
    void doesNotRunOnAJdkWithoutAJavaToStart() {
        // A JDK whose directory is gone, or one in WSL or a container.
        TestJdks.register("moved", dir, "Eclipse Temurin 25", untilTheTestEnds);

        assertThat(FormatterJdks.resolve(fixture.getProject(), new Named("moved")))
                .isEqualTo(new Unusable("moved has no java to run in " + dir));
    }

    @Test
    void doesNotRunOnAJdkOfAnUnknownVersion() {
        TestJdks.register("odd", dir, "a JDK", untilTheTestEnds);

        assertThat(FormatterJdks.resolve(fixture.getProject(), new Named("odd")))
                .isEqualTo(new Unusable("the Java version of odd is not known"));
        assertThat(FormatterJdks.label(fixture.getProject(), new Named("odd"))).isEqualTo(new Label("odd", ""));
    }

    @Test
    void listsTheIdesRuntimeTheProjectSdkAndTheJdksByName() {
        TestJdks.register("temurin-25", dir, "Eclipse Temurin 25", untilTheTestEnds);
        TestJdks.register("Corretto 21", dir, "Amazon Corretto 21.0.3", untilTheTestEnds);

        assertThat(FormatterJdks.choices())
                .startsWith(new IdeRuntime(), new ProjectSdk())
                .containsSubsequence(new Named("Corretto 21"), new Named("temurin-25"));
    }
}
