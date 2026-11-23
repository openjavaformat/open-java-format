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

import com.palantir.javaformat.intellij.FormatterJdk.Resolved;
import com.palantir.javaformat.intellij.FormatterJdk.Unusable;
import com.palantir.javaformat.intellij.FormatterJdk.Usable;
import com.palantir.javaformat.intellij.FormatterSelection.Fallback;
import com.palantir.javaformat.intellij.FormatterSelection.InProcess;
import com.palantir.javaformat.intellij.FormatterSelection.NativeImage;
import com.palantir.javaformat.intellij.FormatterSelection.SeparateJvm;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class FormatterSelectionTest {

    private static final Path CUSTOM = Path.of("/opt/open-java-format");
    private static final Path EMBEDDED = Path.of("/ide/system/open-java-format/native/21.0.1/open-java-format");
    private static final Usable IDE_RUNTIME = new Usable(Path.of("/ide/jbr/bin/java"), 21);
    private static final Usable JDK_25 = new Usable(Path.of("/jdks/temurin-25/bin/java"), 25);
    private static final String NO_JAVAC = "this IDE does not open java.lang to plugins";
    private static final String TOO_OLD = "temurin-17 is Java 17, and the formatter needs Java 21 or later";

    private final PalantirJavaFormatSettings settings = new PalantirJavaFormatSettings();
    private final Machine machine = new Machine();

    @Test
    void formatsInsideTheIdeUnlessTheSettingsSayOtherwise() {
        assertThat(FormatterSelection.of(settings, machine)).isEqualTo(new InProcess());
    }

    @Test
    void formatsInAJvmOfItsOwnWhereTheIdeGivesNoAccessToJavac() {
        machine.inProcessUnavailable = Optional.of(NO_JAVAC);

        assertThat(FormatterSelection.of(settings, machine))
                .isEqualTo(
                        new SeparateJvm(IDE_RUNTIME.java(), 21, List.of(new Fallback.InProcessUnavailable(NO_JAVAC))));
    }

    @Test
    void runsTheJvmOnTheIdesRuntimeUnlessAJdkIsChosen() {
        settings.setFormatterMode(FormatterMode.JVM);

        assertThat(FormatterSelection.of(settings, machine))
                .isEqualTo(new SeparateJvm(IDE_RUNTIME.java(), 21, List.of()));
    }

    @Test
    void runsTheJvmOnTheJdkTheUserChose() {
        settings.setFormatterMode(FormatterMode.JVM);
        settings.setJvmJdk(new FormatterJdk.Named("temurin-25"));
        machine.jdk = JDK_25;

        assertThat(FormatterSelection.of(settings, machine)).isEqualTo(new SeparateJvm(JDK_25.java(), 25, List.of()));
    }

    @Test
    void runsTheJvmOnTheIdesRuntimeWhenTheChosenJdkCannotRunTheFormatter() {
        settings.setFormatterMode(FormatterMode.JVM);
        settings.setJvmJdk(new FormatterJdk.ProjectSdk());
        machine.jdk = new Unusable(TOO_OLD);

        assertThat(FormatterSelection.of(settings, machine))
                .isEqualTo(new SeparateJvm(IDE_RUNTIME.java(), 21, List.of(new Fallback.JdkUnusable(TOO_OLD))));
    }

    @Test
    void usesTheChosenJdkWhenAnotherModeFallsBackToAJvm() {
        // The JDK is kept whichever mode is on, and it is the JVM to fall back to.
        settings.setJvmJdk(new FormatterJdk.Named("temurin-25"));
        machine.jdk = JDK_25;
        machine.inProcessUnavailable = Optional.of(NO_JAVAC);

        assertThat(FormatterSelection.of(settings, machine))
                .isEqualTo(new SeparateJvm(JDK_25.java(), 25, List.of(new Fallback.InProcessUnavailable(NO_JAVAC))));
    }

    @Test
    void saysEveryThingThatIsMissing() {
        settings.setJvmJdk(new FormatterJdk.Named("temurin-17"));
        machine.jdk = new Unusable(TOO_OLD);
        machine.inProcessUnavailable = Optional.of(NO_JAVAC);

        assertThat(FormatterSelection.of(settings, machine))
                .isEqualTo(new SeparateJvm(
                        IDE_RUNTIME.java(),
                        21,
                        List.of(new Fallback.InProcessUnavailable(NO_JAVAC), new Fallback.JdkUnusable(TOO_OLD))));
    }

    @Test
    void runsTheBinaryTheUserChose() {
        settings.setFormatterMode(FormatterMode.NATIVE);
        settings.setNativeImagePath(Optional.of(CUSTOM));
        machine.embeddedNativeImage = Optional.of(EMBEDDED);

        assertThat(FormatterSelection.of(settings, machine)).isEqualTo(new NativeImage(CUSTOM, false));
    }

    @Test
    void runsTheEmbeddedBinaryWithoutAPathOfTheUsersOwn() {
        settings.setFormatterMode(FormatterMode.NATIVE);
        machine.embeddedNativeImage = Optional.of(EMBEDDED);

        assertThat(FormatterSelection.of(settings, machine)).isEqualTo(new NativeImage(EMBEDDED, true));
    }

    @Test
    void fallsBackToAJvmWithoutABinary() {
        settings.setFormatterMode(FormatterMode.NATIVE);

        assertThat(FormatterSelection.of(settings, machine))
                .isEqualTo(new SeparateJvm(IDE_RUNTIME.java(), 21, List.of(new Fallback.NativeImageMissing())));
    }

    @Test
    void runsNoBinaryUnlessTheNativeFormatterIsOn() {
        settings.setNativeImagePath(Optional.of(CUSTOM));
        machine.embeddedNativeImage = Optional.of(EMBEDDED);

        assertThat(FormatterSelection.of(settings, machine)).isEqualTo(new InProcess());
    }

    @Test
    void looksOnlyForWhatWouldRun() {
        // It is asked for every file formatted: the embedded binary lists a directory, a JDK is looked up in the IDE.
        var unasked = new Machine() {
            @Override
            public Optional<Path> embeddedNativeImage() {
                throw new AssertionError("the embedded binary was looked for");
            }

            @Override
            public Resolved resolve(FormatterJdk chosen) {
                throw new AssertionError("the JDK was looked for");
            }
        };
        settings.setNativeImagePath(Optional.of(CUSTOM));

        assertThat(FormatterSelection.of(settings, unasked)).isEqualTo(new InProcess());
        settings.setFormatterMode(FormatterMode.NATIVE);
        assertThat(FormatterSelection.of(settings, unasked)).isEqualTo(new NativeImage(CUSTOM, false));
    }

    /** An IDE whose JVM reaches javac, with no native binary downloaded, and one JDK besides its runtime. */
    private static class Machine implements FormatterSelection.Environment {
        Optional<String> inProcessUnavailable = Optional.empty();
        Optional<Path> embeddedNativeImage = Optional.empty();
        Resolved jdk = new Unusable("there is no such JDK");

        @Override
        public Optional<String> inProcessUnavailable() {
            return inProcessUnavailable;
        }

        @Override
        public Resolved resolve(FormatterJdk chosen) {
            return chosen instanceof FormatterJdk.IdeRuntime ? IDE_RUNTIME : jdk;
        }

        @Override
        public Usable ideRuntime() {
            return IDE_RUNTIME;
        }

        @Override
        public Optional<Path> embeddedNativeImage() {
            return embeddedNativeImage;
        }
    }
}
