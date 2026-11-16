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
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.Range;
import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;

/**
 * One test for each {@link CaseFile} in the given folders on the test classpath: formatting the before block gives the
 * after block, and formatting that again changes nothing. For the lines that formatting changes, taken as a range, the
 * service that formats in this JVM, as {@code formatDiff} does without the native binary, gives the replacements that
 * the command line prints, which the bootstrapping and native services run. Adding a case is adding a file. The cases
 * are in a folder of the JDK they need, and each JDK has a class that runs the folders it can parse, such as
 * {@link FormatterCasesJava21Test}.
 */
final class FormatterCasesTest {
    private static final Formatter FORMATTER = Formatter.createFormatter(
            JavaFormatterOptions.builder().style(Style.OJF).build());
    private static final FormatterService SERVICE = new FormatterServiceImpl();
    private static final ObjectMapper MAPPER =
            JsonMapper.builder().addModule(new GuavaModule()).build();

    /** The tests of the case files under {@code folders} on the test classpath. */
    static List<DynamicTest> casesIn(String... folders) throws Exception {
        var tests = new ArrayList<DynamicTest>();

        for (var folder : folders) {
            var url = requireNonNull(
                    FormatterCasesTest.class.getClassLoader().getResource(folder),
                    "no " + folder + " folder on the test classpath");
            var root = Path.of(url.toURI());
            var paths = CaseFile.pathsIn(root);

            // A wrong folder or a resource filter would otherwise pass with no tests at all.
            assertThat(paths).as("the case files under %s", root).isNotEmpty();

            for (var path : paths) {
                var name = path.substring(0, path.length() - CaseFile.EXTENSION.length());

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    var testCase = CaseFile.parse(path, Files.readString(root.resolve(path)));
                    var once = FORMATTER.formatSourceAndFixImports(testCase.before());

                    assertThat(once)
                            .as(() -> folder + "/" + path + ", first run, " + testCase.firstDifference(once))
                            .isEqualTo(testCase.after());

                    var twice = FORMATTER.formatSourceAndFixImports(once);

                    assertThat(twice)
                            .as(() -> folder + "/" + path + ", second run, " + testCase.firstDifference(twice))
                            .isEqualTo(once);

                    var lines = changedLines(testCase);
                    List<Replacement> replacements = MAPPER.readValue(
                            commandLine(
                                    testCase.before(),
                                    "--character-ranges",
                                    lines.lowerEndpoint() + ":" + (lines.upperEndpoint() - 1),
                                    "--output-replacements",
                                    "-"),
                            new TypeReference<>() {});

                    assertThat(SERVICE.getFormatReplacements(testCase.before(), List.of(lines)))
                            .as(() -> folder + "/" + path + ", the in-process service on characters " + lines)
                            .isEqualTo(replacements);
                }));
            }
        }

        return tests;
    }

    /**
     * The lines of the before block that formatting changes, from the first that differs from the after block to the
     * last, as one range of characters without the last line break; the whole block when no line differs.
     */
    private static Range<Integer> changedLines(CaseFile testCase) {
        var before = testCase.before();
        var beforeLines = List.of(before.split("(?<=\n)"));
        var afterLines = List.of(testCase.after().split("(?<=\n)"));
        var first = 0;
        while (first < beforeLines.size()
                && first < afterLines.size()
                && beforeLines.get(first).equals(afterLines.get(first))) {
            first++;
        }
        var last = 0;
        while (last < beforeLines.size() - first
                && last < afterLines.size() - first
                && beforeLines
                        .get(beforeLines.size() - 1 - last)
                        .equals(afterLines.get(afterLines.size() - 1 - last))) {
            last++;
        }
        var start = 0;
        for (var i = 0; i < first; i++) {
            start += beforeLines.get(i).length();
        }
        var end = start;
        for (var i = first; i < beforeLines.size() - last; i++) {
            end += beforeLines.get(i).length();
        }
        while (end > start && (before.charAt(end - 1) == '\n' || before.charAt(end - 1) == '\r')) {
            end--;
        }
        return end > start ? Range.closedOpen(start, end) : Range.closedOpen(0, before.length());
    }

    /** What {@code open-java-format} prints for the source on standard input. */
    static String commandLine(String source, String... args) throws UsageException {
        var out = new StringWriter();
        var err = new StringWriter();
        var main = new Main(
                new PrintWriter(out, true),
                new PrintWriter(err, true),
                new ByteArrayInputStream(source.getBytes(UTF_8)));

        assertThat(main.format(args)).as(err::toString).isZero();

        return out.toString();
    }
}
