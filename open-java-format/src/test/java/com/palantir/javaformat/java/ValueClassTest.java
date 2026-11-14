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

import static com.google.common.truth.Truth.assertThat;

import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import java.util.Arrays;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Value classes (Project Valhalla), a preview of JDK 28. They need a JDK build that has the {@code value} modifier, not
 * a version: a JDK 28 build without Valhalla has none.
 */
@EnabledIf("hasValueModifier")
@Execution(ExecutionMode.CONCURRENT)
final class ValueClassTest {
    static boolean hasValueModifier() {
        return Arrays.stream(Modifier.values())
                .anyMatch(modifier -> modifier.name().equals("VALUE"));
    }

    @Test
    void valueClassAndRecord() throws FormatterException {
        String input = """
            value final public class PointValueClass {
                final int x;
                final int y;

                PointValueClass(int x, int y) {
                    this.x = x;
                    this.y = y;
                }
            }

            value public record PointValueRecord(int x, int y) {}
            """;
        String expected = """
            public final value class PointValueClass {
                final int x;
                final int y;

                PointValueClass(int x, int y) {
                    this.x = x;
                    this.y = y;
                }
            }

            public value record PointValueRecord(int x, int y) {}
            """;
        Formatter formatter = Formatter.createFormatter(
                JavaFormatterOptions.builder().style(Style.OJF).build());

        assertThat(formatter.formatSource(input)).isEqualTo(expected);
    }

    @Test
    void valueClass() throws FormatterException {
        assertThat(ModifierOrderer.reorderModifiers("value final public").getText())
                .isEqualTo("public final value");
    }
}
