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

package com.palantir.javaformat.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.palantir.javaformat.Utils;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.FormatterService;
import com.palantir.javaformat.java.FormatterServiceImpl;
import com.palantir.javaformat.java.Replacement;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The services that need no native binary: the one in this JVM and the one that starts a new JVM. Unlike
 * {@link FormatterServicesTest}, this runs in every build.
 */
final class JvmFormatterServicesTest {

    @ParameterizedTest
    @MethodSource("getFormatters")
    void fixes_only_the_imports(FormatterService formatterService) throws FormatterException {
        String input = getTestResourceContent("fix-imports.input");
        String expectedOutput = getTestResourceContent("fix-imports.output");

        assertThat(formatterService.fixImports(input)).isEqualTo(expectedOutput);
    }

    @ParameterizedTest
    @MethodSource("getFormatters")
    void leaves_out_the_empty_ranges(FormatterService formatterService) throws FormatterException {
        String input = """
            class A {
                int  a;
                int  b;
            }
            """;
        int a = input.indexOf("    int  a");
        int b = input.indexOf("    int  b");

        List<Replacement> replacements = formatterService.getFormatReplacements(
                input,
                List.of(
                        Range.closedOpen(a, input.indexOf('\n', a)),
                        Range.closedOpen(b, b),
                        Range.closedOpen(input.length(), input.length())));

        assertThat(Utils.applyReplacements(input, replacements)).isEqualTo("""
            class A {
                int a;
                int  b;
            }
            """);
    }

    @ParameterizedTest
    @MethodSource("getFormatters")
    void formats_nothing_without_a_range_that_is_not_empty(FormatterService formatterService)
            throws FormatterException {
        String input = "class A {  int  a;  }\n";

        assertThat(formatterService.getFormatReplacements(input, List.of(Range.closedOpen(10, 10))))
                .isEmpty();
        assertThat(formatterService.getFormatReplacements(input, List.of())).isEmpty();
    }

    private static Stream<FormatterService> getFormatters() {
        return Stream.of(
                new FormatterServiceImpl(),
                new BootstrappingFormatterService(
                        FormatterServicesTest.javaBinPath(),
                        Runtime.version().feature(),
                        FormatterServicesTest.getClasspath()));
    }

    private String getTestResourceContent(String resourceName) {
        try {
            URI resource = getClass().getClassLoader().getResource(resourceName).toURI();
            return Files.readString(Path.of(resource));
        } catch (Exception e) {
            throw new RuntimeException("Error reading resource: " + resourceName, e);
        }
    }
}
