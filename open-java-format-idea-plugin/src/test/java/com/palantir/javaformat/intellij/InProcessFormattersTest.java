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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Range;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.palantir.javaformat.bootstrap.BootstrappingFormatterService;
import com.palantir.javaformat.java.FormatterService;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InProcessFormattersTest {

    private static final String SYNTAX_ERROR = "class {";
    private static final String A_CLASS = "class A {}\n";

    @TempDir
    private Path dir;

    private JavaCodeInsightTestFixture fixture;
    private List<Path> formatterJars;

    private final RecordingFormatterService anotherJvm = new RecordingFormatterService();
    private final List<Throwable> failures = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // The formatter's jars are in the plugin, which only an IDE finds.
        var projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createLightFixtureBuilder(
                        new DefaultLightProjectDescriptor(), getClass().getName());
        fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        fixture.setUp();
        formatterJars = FormatterProvider.getBundledImplementationUrls();
    }

    @AfterEach
    void tearDown() throws Exception {
        fixture.tearDown();
    }

    @Test
    void formatsAsAJvmOfItsOwnDoes() throws Exception {
        var input = Files.readString(Path.of("src/test/resources/A.input"));
        var wholeFile = List.of(Range.closedOpen(0, input.length()));
        var inProcess = inProcess(InProcessFormatters.IDE, formatterJars);
        var separateJvm = separateJvm();

        assertThat(inProcess.formatSourceReflowStringsAndFixImports(input))
                .isEqualTo(Files.readString(Path.of("src/test/resources/A.output")))
                .isEqualTo(separateJvm.formatSourceReflowStringsAndFixImports(input));
        assertThat(inProcess.getFormatReplacements(input, wholeFile))
                .isNotEmpty()
                .isEqualTo(separateJvm.getFormatReplacements(input, wholeFile));
        assertThat(inProcess.fixImports(input)).isEqualTo(separateJvm.fixImports(input));
        assertThat(anotherJvm.calls()).isZero();
    }

    @Test
    void leavesAFileWithASyntaxErrorAloneAsAJvmOfItsOwnDoes() throws Exception {
        // The formatter itself throws; on the command line that is an exit code, which the formatters that run it
        // answer by changing nothing. A file is rarely free of syntax errors while it is typed in.
        var wholeFile = List.of(Range.closedOpen(0, SYNTAX_ERROR.length()));
        var inProcess = inProcess(InProcessFormatters.IDE, formatterJars);
        var separateJvm = separateJvm();

        assertThat(inProcess.getFormatReplacements(SYNTAX_ERROR, wholeFile))
                .isEqualTo(separateJvm.getFormatReplacements(SYNTAX_ERROR, wholeFile))
                .isEmpty();
        assertThat(inProcess.fixImports(SYNTAX_ERROR))
                .isEqualTo(SYNTAX_ERROR)
                .isEqualTo(separateJvm.fixImports(SYNTAX_ERROR));
        assertThat(inProcess.formatSourceReflowStringsAndFixImports(SYNTAX_ERROR))
                .isEqualTo(SYNTAX_ERROR)
                .isEqualTo(separateJvm.formatSourceReflowStringsAndFixImports(SYNTAX_ERROR));
        assertThat(anotherJvm.calls()).isZero();
        assertThat(failures).isEmpty();
    }

    @Test
    void keepsAFormatterLoadedForAsLongAsItsJarsStayTheSame() throws Exception {
        var formatters = new InProcessFormatters();
        var jars = copiesOfTheFormatterJars();
        var loaded = formatters.load(jars);

        assertThat(formatters.load(jars)).isSameAs(loaded);
        // In another order they are another class path.
        assertThat(formatters.load(jars.reversed())).isNotSameAs(loaded);

        // A jar rebuilt in place: a JVM started for the next file would run the new one.
        Files.setLastModifiedTime(jars.get(0), FileTime.from(Instant.now().plusSeconds(60)));

        assertThat(formatters.load(jars)).isNotSameAs(loaded);
    }

    @Test
    void loadsTheFormatterApartFromTheIde() throws Exception {
        try (var classLoader = new FormatterClassLoader(urls(), FormatterService.class.getClassLoader())) {
            // The formatter and what it runs with are its own, whatever versions of them the IDE has.
            assertThat(classLoader
                            .loadClass("com.palantir.javaformat.java.FormatterServiceImpl")
                            .getClassLoader())
                    .isSameAs(classLoader);
            assertThat(classLoader
                            .loadClass("com.fasterxml.jackson.databind.ObjectMapper")
                            .getClassLoader())
                    .isSameAs(classLoader);
            // What the plugin and the formatter hand each other is the plugin's.
            assertThat(classLoader.loadClass(FormatterService.class.getName())).isSameAs(FormatterService.class);
            assertThat(classLoader.loadClass(Range.class.getName())).isSameAs(Range.class);
            assertThat(classLoader.loadClass("com.sun.tools.javac.parser.JavacParser"))
                    .isNotNull();
            assertThatThrownBy(() -> classLoader.loadClass("com.intellij.openapi.project.Project"))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void formatsInAJvmOfItsOwnOnceTheFormatterDoesNotLoad() throws Exception {
        var formatters = new InProcessFormatters();
        var noFormatter = List.of(dir.resolve("no-formatter-here.jar"));
        var inProcess = inProcess(formatters, noFormatter);

        assertThat(inProcess.fixImports(A_CLASS)).isEqualTo(RecordingFormatterService.FORMATTED);

        assertThat(failures)
                .singleElement()
                .satisfies(failure -> assertThat(failure)
                        .hasMessage("Cannot load the formatter from %s", noFormatter)
                        .hasRootCauseMessage("Expected one formatter, found 0 in %s", noFormatter));
        // The settings page says so, and every project formats in a JVM of its own until the IDE restarts.
        assertThat(formatters.unavailable())
                .hasValueSatisfying(
                        reason -> assertThat(reason).startsWith("the formatter does not load inside the IDE ("));

        // Without trying again, or saying so again, for every file.
        assertThat(inProcess.formatSourceReflowStringsAndFixImports(A_CLASS))
                .isEqualTo(RecordingFormatterService.FORMATTED);
        assertThat(failures).hasSize(1);
        assertThat(anotherJvm.calls()).isEqualTo(2);
    }

    @Test
    void formatsInAJvmOfItsOwnOnceTheFormatterDoesNotLink() throws Exception {
        // A formatter from a build can need a class that neither its jars nor the plugin has in the version it needs.
        var formatters = new InProcessFormatters();
        var withoutADependency = formatterJars.stream()
                .filter(jar -> !jar.getFileName().toString().startsWith("functionaljava"))
                .toList();
        assertThat(withoutADependency).hasSize(formatterJars.size() - 1);
        var inProcess = inProcess(formatters, withoutADependency);

        assertThat(inProcess.formatSourceReflowStringsAndFixImports(A_CLASS))
                .isEqualTo(RecordingFormatterService.FORMATTED);

        assertThat(failures).singleElement().isInstanceOf(NoClassDefFoundError.class);
        assertThat(formatters.unavailable())
                .hasValueSatisfying(
                        reason -> assertThat(reason).startsWith("the formatter does not link inside the IDE ("));
    }

    @Test
    void formatsInAJvmOfItsOwnAFileThatOverflowsTheStack() throws Exception {
        // javac parses by recursion, and the IDE formats on threads with less stack than a JVM's main thread has.
        var formatters = new InProcessFormatters();
        var inProcess = inProcess(formatters, formatterJars);
        assertThat(inProcess.formatSourceReflowStringsAndFixImports(A_CLASS)).isEqualTo(A_CLASS);
        var nested = "class A { int x = " + "(".repeat(20_000) + "1" + ")".repeat(20_000) + "; }";

        assertThat(onAThreadOfTheIde(() -> inProcess.formatSourceReflowStringsAndFixImports(nested)))
                .isEqualTo(RecordingFormatterService.FORMATTED);

        assertThat(failures).singleElement().isInstanceOf(StackOverflowError.class);
        // That file alone: the next one is formatted inside the IDE again.
        assertThat(formatters.unavailable()).isEmpty();
        assertThat(inProcess.formatSourceReflowStringsAndFixImports(A_CLASS)).isEqualTo(A_CLASS);
        assertThat(anotherJvm.calls()).isEqualTo(1);
    }

    private interface Formatting {
        String format() throws Exception;
    }

    private static String onAThreadOfTheIde(Formatting formatting) throws Exception {
        var result = new AtomicReference<String>();
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(
                null,
                () -> {
                    try {
                        result.set(formatting.format());
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                },
                "formatting",
                512 * 1024);
        thread.start();
        thread.join();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    /** The formatter in this JVM, with a stand-in for the JVM that formats what this one cannot. */
    private FormatterService inProcess(InProcessFormatters formatters, List<Path> jars) {
        return new InProcessFormatterService(formatters, jars, anotherJvm, failures::add);
    }

    private FormatterService separateJvm() {
        var ideRuntime = FormatterJdks.ideRuntime();
        return new BootstrappingFormatterService(ideRuntime.java(), ideRuntime.javaVersion(), formatterJars);
    }

    /** In a directory of the test's own, where a jar can be touched. */
    private List<Path> copiesOfTheFormatterJars() throws Exception {
        var copies = new ArrayList<Path>();
        for (var jar : formatterJars) {
            copies.add(Files.copy(jar, dir.resolve(jar.getFileName())));
        }
        return copies;
    }

    private URL[] urls() throws Exception {
        var urls = new URL[formatterJars.size()];
        for (var index = 0; index < urls.length; index++) {
            urls[index] = formatterJars.get(index).toUri().toURL();
        }
        return urls;
    }
}
