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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;

/**
 * One test for each {@link CaseFile} in the given folders on the test classpath: formatting the before block gives the
 * after block, and formatting that again changes nothing. Adding a case is adding a file. The cases are in a folder of
 * the JDK they need, and each JDK has a class that runs the folders it can parse, such as
 * {@link FormatterCasesJava21Test}.
 */
final class FormatterCasesTest {
    private static final Formatter FORMATTER = Formatter.createFormatter(
            JavaFormatterOptions.builder().style(Style.OJF).build());

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
                }));
            }
        }

        return tests;
    }
}
