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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.palantir.javaformat.Utils;
import com.palantir.javaformat.java.SnippetFormatter.SnippetKind;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SnippetFormatterTest {
    private static final String LONG_STRINGS = """
        class S {
            String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

            String changed = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna.";
        }
        """;

    // The editor's Format: a compilation unit with its comments.
    @Test
    void reflowsTheLongStringOfTheRangeOnly() throws FormatterException {
        int start = LONG_STRINGS.indexOf("    String changed");
        Range<Integer> changed = Range.closedOpen(start, LONG_STRINGS.indexOf('\n', start));

        List<Replacement> replacements = new SnippetFormatter()
                .format(SnippetKind.COMPILATION_UNIT, LONG_STRINGS, List.of(changed), 0, /* includeComments= */ true);

        assertThat(Utils.applyReplacements(LONG_STRINGS, replacements)).isEqualTo("""
            class S {
                String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

                String changed =
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et"
                                + " dolore magna.";
            }
            """);
    }

    // Snippets, which Eclipse formats when it inserts code, are wrapped in a class and may only change whitespace.
    @Test
    void leavesTheLongStringOfAStatementSnippet() throws FormatterException {
        String literal = "\"Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt"
                + " ut labore et dolore magna.\"";
        String statement = "String s = " + literal + ";";

        // Two levels: the statement sits in a block of the dummy class, as in a method's body.
        List<Replacement> replacements = new SnippetFormatter()
                .format(
                        SnippetKind.STATEMENTS,
                        statement,
                        List.of(Range.closedOpen(0, statement.length())),
                        2,
                        /* includeComments= */ false);

        assertThat(Utils.applyReplacements(statement, replacements)).contains(literal);
    }
}
