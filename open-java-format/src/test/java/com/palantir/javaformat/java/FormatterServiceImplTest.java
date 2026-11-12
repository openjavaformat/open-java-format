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

package com.palantir.javaformat.java;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.Range;
import com.palantir.javaformat.Utils;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The service that formats in the build's own JVM, for {@code formatDiff} without the native binary, the Spotless step
 * and the Maven plugin, against the command line, which the bootstrapping and native services run for IntelliJ IDEA
 * and {@code formatDiff}, and the GitHub Action runs to check.
 */
final class FormatterServiceImplTest {
    private static final FormatterService SERVICE = new FormatterServiceImpl();
    private static final ObjectMapper MAPPER =
            JsonMapper.builder().addModule(new GuavaModule()).build();

    // Two long strings, as in a file where only the line of the second one has changed.
    private static final String LONG_STRINGS = """
        class S {
            String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

            String changed = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna.";
        }
        """;

    static Stream<Arguments> sources() {
        return Stream.of(
                Arguments.of("long strings", LONG_STRINGS),
                Arguments.of("joinable literals", """
                    class J {
                        String joined = "one " + "two";
                        String wrapped = "Lorem ipsum dolor sit amet, " + "consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore";
                    }
                    """),
                // The comment stays behind when its import is removed, and the order of fixing the imports and
                // formatting decides where it ends up.
                Arguments.of("trailing comment of an unused import", """
                    package p;

                    // group one
                    import java.util.Map; // used
                    import java.io.File; // unused, trailing comment
                    /* block between */
                    import java.util.List;

                    class C { List<String> l; Map<String,String> m; }
                    """),
                Arguments.of("comment under an unused import", """
                    package p;

                    import java.io.File;
                    // comment about the class
                    class C {}
                    """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void formatsAFileAsTheCommandLineDoes(String name, String source) throws Exception {
        assertThat(SERVICE.formatSourceReflowStringsAndFixImports(source)).isEqualTo(commandLine(source, "-"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    void replacesEachLineAsTheCommandLineDoes(String name, String source) throws Exception {
        int start = 0;
        for (String line : source.split("\n", -1)) {
            int end = start + line.length();
            if (!line.isBlank()) {
                List<Replacement> printed = MAPPER.readValue(
                        commandLine(
                                source, "--character-ranges", start + ":" + (end - 1), "--output-replacements", "-"),
                        new TypeReference<>() {});
                assertThat(SERVICE.getFormatReplacements(source, List.of(Range.closedOpen(start, end))))
                        .as(line)
                        .isEqualTo(printed);
            }
            start = end + 1;
        }
    }

    @Test
    void reflowsTheLongStringOfAChangedLineOnly() throws Exception {
        String changed = LONG_STRINGS
                .lines()
                .filter(line -> line.contains("changed"))
                .findFirst()
                .orElseThrow();
        int start = LONG_STRINGS.indexOf(changed);
        int end = start + changed.length();

        List<Replacement> replacements =
                SERVICE.getFormatReplacements(LONG_STRINGS, List.of(Range.closedOpen(start, end)));
        // The command line used to reflow every long string in the file once one in the ranges needed it.
        List<Replacement> printed = MAPPER.readValue(
                commandLine(LONG_STRINGS, "--character-ranges", start + ":" + (end - 1), "--output-replacements", "-"),
                new TypeReference<>() {});

        assertThat(Utils.applyReplacements(LONG_STRINGS, printed))
                .isEqualTo(Utils.applyReplacements(LONG_STRINGS, replacements));
        assertThat(Utils.applyReplacements(LONG_STRINGS, replacements)).isEqualTo("""
            class S {
                String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

                String changed =
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et"
                                + " dolore magna.";
            }
            """);
    }

    @Test
    void failsOnAnExtraneousSemicolonAsTheCommandLineDoes() throws Exception {
        // Fixing the imports first used to drop the semicolon, which javac 21 rejects between imports, and format
        // the file that the command line refuses.
        String source = """
            package p;

            import java.util.List;;
            import java.io.File;

            class C { List<String> l; }
            """;
        StringWriter err = new StringWriter();
        Main main = new Main(
                new PrintWriter(new StringWriter(), true),
                new PrintWriter(err, true),
                new ByteArrayInputStream(source.getBytes(UTF_8)));

        assertThat(main.format("-")).isNotZero();
        assertThat(err.toString()).contains("extraneous semicolon");
        assertThatThrownBy(() -> SERVICE.formatSourceReflowStringsAndFixImports(source))
                .isInstanceOf(FormatterException.class)
                .hasMessageContaining("extraneous semicolon");
    }

    /** What {@code open-java-format} prints for the source on standard input. */
    private static String commandLine(String source, String... args) throws UsageException {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Main main = new Main(
                new PrintWriter(out, true),
                new PrintWriter(err, true),
                new ByteArrayInputStream(source.getBytes(UTF_8)));
        assertThat(main.format(args)).as(err::toString).isZero();
        return out.toString();
    }
}
