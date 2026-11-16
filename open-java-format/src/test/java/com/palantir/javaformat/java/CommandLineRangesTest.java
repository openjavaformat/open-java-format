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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * The command line with {@code --lines}, {@code --offset} or {@code --character-ranges} prints a part of the file
 * formatted, the imports and the rest of the file as they are. {@link FormatterCasesTest} holds the text to what
 * applying {@code --output-replacements} for the same ranges gives, for every case.
 */
@Execution(ExecutionMode.CONCURRENT)
final class CommandLineRangesTest {
    @Test
    void linesLeaveTheImportsAndTheStringsElsewhere() throws Exception {
        String input = """
            import java.util.List;
            import java.io.File;

            class S {
                String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

                String changed = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna.";
            }
            """;

        assertThat(FormatterCasesTest.commandLine(input, "--lines", "7", "-")).isEqualTo("""
            import java.util.List;
            import java.io.File;

            class S {
                String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

                String changed =
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et"
                                + " dolore magna.";
            }
            """);
    }
}
