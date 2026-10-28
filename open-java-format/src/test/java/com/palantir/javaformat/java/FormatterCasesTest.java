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
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * One test for each {@link CaseFile} under {@code test-cases} on the test classpath: formatting the before block gives
 * the after block, and formatting that again changes nothing. Adding a case is adding a file.
 */
@Execution(ExecutionMode.CONCURRENT)
final class FormatterCasesTest {
    private static final String CASES = "test-cases";

    private static final Formatter FORMATTER = Formatter.createFormatter(
            JavaFormatterOptions.builder().style(Style.OJF).build());

    @TestFactory
    List<DynamicTest> cases() throws Exception {
        var folder = requireNonNull(
                FormatterCasesTest.class.getClassLoader().getResource(CASES),
                "no " + CASES + " folder on the test classpath");
        var root = Path.of(folder.toURI());
        var paths = CaseFile.pathsIn(root);

        // A wrong folder or a resource filter would otherwise pass with no tests at all.
        assertThat(paths).as("the case files under %s", root).isNotEmpty();

        var tests = new ArrayList<DynamicTest>();

        for (var path : paths) {
            var name = path.substring(0, path.length() - CaseFile.EXTENSION.length());

            tests.add(DynamicTest.dynamicTest(name, () -> {
                var testCase = CaseFile.parse(path, Files.readString(root.resolve(path)));
                var once = FORMATTER.formatSourceAndFixImports(testCase.before());

                assertThat(once)
                        .as(() -> CASES + "/" + path + ", first run, " + testCase.firstDifference(once))
                        .isEqualTo(testCase.after());

                var twice = FORMATTER.formatSourceAndFixImports(once);

                assertThat(twice)
                        .as(() -> CASES + "/" + path + ", second run, " + testCase.firstDifference(twice))
                        .isEqualTo(once);
            }));
        }

        return tests;
    }
}
