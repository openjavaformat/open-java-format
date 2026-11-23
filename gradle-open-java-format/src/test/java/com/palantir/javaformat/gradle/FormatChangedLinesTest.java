/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
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

import com.google.common.base.Splitter;
import com.palantir.javaformat.bootstrap.BootstrappingFormatterService;
import com.palantir.javaformat.java.FormatterService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Formats the lines of a diff written out as git prints it, so these need neither a repository nor the native binary,
 * and run in every build. {@link FormatDiffTest} runs formatDiff through git with each formatter.
 */
final class FormatChangedLinesTest {

    private static final File CLASSPATH_FILE = new File("build/impl.classpath");

    @TempDir
    Path workTree;

    @Test
    void parsing_git_diff_output_works() throws IOException {
        // A Windows checkout gives the fixture CRLF line endings, while git prints a diff with LF.
        String example1 = Files.readString(
                        Paths.get("src/test/resources/com/palantir/javaformat/java/FormatDiffCliTest/example1.patch"))
                .replace("\r\n", "\n");

        List<String> strings = FormatDiff.parseGitDiffOutput(example1)
                .map(FormatDiff.SingleFileDiff::toString)
                .toList();
        assertThat(strings)
                .containsExactly(
                        "SingleFileDiff{path=build.gradle, lineRanges=[[24..25), [29..30)]}",
                        // The path is a Path, which prints with backslashes on Windows.
                        "SingleFileDiff{path=" + Path.of("tracing/src/test/java/com/palantir/tracing/TracersTest.java")
                                + ", lineRanges=[[659..660), [675..676)]}");
    }

    @ParameterizedTest
    @MethodSource("getFormatters")
    void reflows_the_long_string_of_a_changed_line(FormatterService formatterService) throws IOException {
        Path longString = workTree.resolve("LongString.java");
        Files.writeString(longString, """
            class LongString {
                String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

                String changed = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna.";
            }
            """);
        // What git prints once the blank line and the second field are added to a commit that had the first.
        String diff = """
            diff --git a/LongString.java b/LongString.java
            index 4212039..87d2584 100644
            --- a/LongString.java
            +++ b/LongString.java
            @@ -2,0 +3,2 @@ class LongString {
            +
            +    String changed = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna.";
            """;

        FormatDiff.formatChangedLines(new GitDiff(workTree, diff), formatterService);

        assertThat(longString).hasContent("""
            class LongString {
                String outside = "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat sed.";

                String changed =
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et"
                                + " dolore magna.";
            }
            """);
    }

    // Not the native binary: FormatDiffTest runs formatDiff with it, and FormatterServicesTest reflows a range with it.
    private static Stream<FormatterService> getFormatters() throws IOException {
        return Stream.of(
                // In this JVM, as formatDiff runs it without the native binary.
                FormatterServiceLoader.load(
                        getClasspath().stream().map(Path::toFile).toList()),
                new BootstrappingFormatterService(
                        javaBinPath(), Runtime.version().feature(), getClasspath()));
    }

    private static List<Path> getClasspath() throws IOException {
        return Splitter.on(File.pathSeparatorChar)
                .trimResults()
                .omitEmptyStrings()
                .splitToStream(Files.readString(CLASSPATH_FILE.toPath()))
                .map(Path::of)
                .toList();
    }

    private static Path javaBinPath() {
        String javaHome = checkNotNull(System.getProperty("java.home"), "java.home property not set");
        return Path.of(javaHome).resolve("bin").resolve("java" + (isWindows() ? ".exe" : ""));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
