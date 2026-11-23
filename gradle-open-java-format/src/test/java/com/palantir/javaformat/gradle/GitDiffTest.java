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

package com.palantir.javaformat.gradle;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * GitDiff runs git, and these tests run small Java programs in its place: what it does with a command's output does
 * not depend on the command, and a program can print more than any diff without a repository to make the diff in.
 */
final class GitDiffTest {

    @TempDir
    Path dir;

    @Test
    void reads_an_output_larger_than_a_pipe_holds() throws IOException, InterruptedException {
        // A pipe holds 64 KB on Linux and macOS. A larger git diff filled it, git waited for someone to read it, and
        // formatDiff waited for git to exit, for 30 seconds (#257).
        Files.writeString(dir.resolve("Prints.java"), """
            class Prints {
                public static void main(String[] args) {
                    System.out.print("x".repeat(1_000_000));
                }
            }
            """);

        var output = GitDiff.run(dir, Duration.ofSeconds(30), java(), "Prints.java");

        assertThat(output).hasSize(1_000_000);
    }

    @Test
    void reads_the_output_of_a_command_that_fills_a_pipe_with_warnings() throws IOException, InterruptedException {
        Files.writeString(dir.resolve("Warns.java"), """
            class Warns {
                public static void main(String[] args) {
                    System.err.print("warning: LF will be replaced by CRLF in A.java.\\n".repeat(50_000));
                    System.out.print("diff --git a/A.java b/A.java");
                }
            }
            """);

        var output = GitDiff.run(dir, Duration.ofSeconds(30), java(), "Warns.java");

        assertThat(output).isEqualTo("diff --git a/A.java b/A.java");
    }

    @Test
    void names_the_command_that_did_not_finish_in_time() throws IOException {
        Files.writeString(dir.resolve("Sleeps.java"), """
            class Sleeps {
                public static void main(String[] args) throws InterruptedException {
                    Thread.sleep(60_000);
                }
            }
            """);

        assertThatThrownBy(() -> GitDiff.run(dir, Duration.ofSeconds(1), java(), "Sleeps.java"))
                .hasMessageContaining("%s Sleeps.java", java())
                .hasMessageContaining("1 seconds");
    }

    @Test
    void names_the_command_that_failed_with_its_exit_code_and_standard_error() throws IOException {
        Files.writeString(dir.resolve("Fails.java"), """
            class Fails {
                public static void main(String[] args) {
                    System.err.print("fatal: not a git repository");
                    System.exit(128);
                }
            }
            """);

        assertThatThrownBy(() -> GitDiff.run(dir, Duration.ofSeconds(30), java(), "Fails.java"))
                .hasMessageContaining("%s Fails.java", java())
                .hasMessageContaining("128")
                .hasMessageContaining("fatal: not a git repository");
    }

    private static String java() {
        var javaHome = checkNotNull(System.getProperty("java.home"), "java.home property not set");
        var windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        return Path.of(javaHome, "bin", windows ? "java.exe" : "java").toString();
    }
}
