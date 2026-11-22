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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.google.common.util.concurrent.internal.InternalFutureFailureAccess;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.palantir.javaformat.bootstrap.BootstrappingFormatterService;
import com.palantir.javaformat.java.FormatterService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The formatter inside a JVM started as the IDE's is. The JVM of the tests would not do: the build starts it with
 * javac exported to everything, so nothing there has to be exported.
 */
final class JavacAccessTest {

    // Among the additionalJvmArguments in the product-info.json of IntelliJ IDEA 2024.2.6 and of 2026.3.
    private static final String AS_THE_IDE_STARTS = "--add-opens=java.base/java.lang=ALL-UNNAMED";

    private JavaCodeInsightTestFixture fixture;
    private List<Path> formatterJars;

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
    void reachesJavacInTheJvmOfTheTests() {
        // Which proves nothing about the IDE: the build exports javac to every class loader of this JVM.
        assertThat(JavacAccess.unavailable()).isEmpty();
    }

    @Test
    void formatsInAJvmStartedAsTheIdeIs() throws Exception {
        var input = Files.readString(Path.of("src/test/resources/A.input"));
        var formatted = Files.readString(Path.of("src/test/resources/A.output"));

        assertThat(inAJvmWith(List.of(AS_THE_IDE_STARTS), input).split(InProcessFormatterProbe.BETWEEN_ANSWERS))
                .containsExactly(
                        formatted,
                        // The replacements of the whole file, applied: A.input has no imports to fix.
                        formatted,
                        separateJvm().fixImports(input),
                        InProcessFormatterProbe.SYNTAX_ERROR,
                        // javac is exported to the formatter alone, not to the plugin or to anything else of the IDE.
                        "[]",
                        "formatted in another JVM: 0");
    }

    @Test
    void formatsInAJvmWhoseUserExportedJavac() throws Exception {
        // As google-java-format asks of its users: the IDE's VM options edited by hand.
        var exports = JavacAccess.PACKAGES.stream()
                .map(javacPackage -> "--add-exports=jdk.compiler/" + javacPackage + "=ALL-UNNAMED")
                .toList();
        var input = Files.readString(Path.of("src/test/resources/A.input"));

        assertThat(inAJvmWith(exports, input).split(InProcessFormatterProbe.BETWEEN_ANSWERS))
                .startsWith(Files.readString(Path.of("src/test/resources/A.output")))
                .endsWith(JavacAccess.PACKAGES.toString(), "formatted in another JVM: 0");
    }

    @Test
    void saysWhyItCannotFormatInAJvmThatOpensNothing() throws Exception {
        var reason = "this IDE does not open java.lang to plugins, so javac cannot be reached";

        assertThat(inAJvmWith(List.of(), "").lines())
                // Asked to load the formatter all the same, it refuses rather than fail on the first file.
                .containsExactly("unavailable: " + reason, "refused: " + reason);
    }

    private FormatterService separateJvm() {
        var ideRuntime = FormatterJdks.ideRuntime();
        return new BootstrappingFormatterService(ideRuntime.java(), ideRuntime.javaVersion(), formatterJars);
    }

    /** What InProcessFormatterProbe prints in a JVM with these arguments and nothing else of the IDE's. */
    private String inAJvmWith(List<String> jvmArguments, String input) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmArguments);
        command.add("-cp");
        // What the plugin's class loader has of them in the IDE: the plugin, the SPI and Guava.
        command.add(Stream.of(
                        InProcessFormatterProbe.class,
                        JavacAccess.class,
                        FormatterService.class,
                        Range.class,
                        InternalFutureFailureAccess.class)
                .map(type -> TestClassPath.entryOf(type).toString())
                .distinct()
                .collect(Collectors.joining(File.pathSeparator)));
        command.add(InProcessFormatterProbe.class.getName());
        formatterJars.forEach(jar -> command.add(jar.toString()));

        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT);
        // Options a JVM takes from its environment would be the test's, not the IDE's.
        builder.environment().keySet().removeAll(List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"));
        var process = builder.start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(UTF_8));
        }
        var output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as("exit code, after printing %s", output).isZero();
        return output;
    }
}
