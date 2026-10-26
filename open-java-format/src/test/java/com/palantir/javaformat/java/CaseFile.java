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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A formatter test case written as a file: the code before formatting and the code expected after it, separated by a
 * line of six or more dashes. A file with two such lines starts with a settings block. There are no settings yet, so
 * the block holds only blank lines and {@code #} comments, which explain the case.
 *
 * <p>Lines end in {@code \n} in both blocks, whatever the file uses. {@code afterLine} is the line of the file the
 * after block starts on, so that a failure can name the line of the file where the output departs from it.
 */
record CaseFile(String before, String after, int afterLine) {
    static final String EXTENSION = ".case";

    private static final Pattern SEPARATOR = Pattern.compile("-{6,}");

    /** Splits the text of the case file at {@code path}, which only names the file in an error. */
    static CaseFile parse(String path, String text) {
        var blocks = new ArrayList<StringBuilder>();
        blocks.add(new StringBuilder());
        var number = 0;
        var afterLine = 0;

        for (var line : text.lines().toList()) {
            number++;

            if (SEPARATOR.matcher(line).matches()) {
                blocks.add(new StringBuilder());
                afterLine = number + 1;
            } else {
                blocks.getLast().append(line).append('\n');
            }
        }

        var separators = blocks.size() - 1;

        if (separators != 1 && separators != 2) {
            throw new IllegalArgumentException(
                    path + ": a case has one or two lines of six or more dashes, found " + separators);
        }

        if (separators == 2) {
            for (var line : blocks.getFirst().toString().lines().toList()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    throw new IllegalArgumentException(path
                            + ": there are no settings yet, the first block takes blank lines and # comments only: "
                            + line);
                }
            }
        }

        return new CaseFile(
                blocks.get(separators - 1).toString(), blocks.get(separators).toString(), afterLine);
    }

    /**
     * Describes where {@code actual} first departs from the after block: the line, numbered as in the file, with what
     * the case expects there and what {@code actual} has.
     */
    String firstDifference(String actual) {
        var expectedLines = after.lines().toList();
        var actualLines = actual.lines().toList();
        var i = 0;

        while (i < expectedLines.size()
                && i < actualLines.size()
                && expectedLines.get(i).equals(actualLines.get(i))) {
            i++;
        }

        if (i == expectedLines.size() && i == actualLines.size()) {
            return "the lines are the same, their line breaks differ";
        }

        return "line " + (afterLine + i) + "\n  expected: " + lineOrEnd(expectedLines, i) + "\n  actual:   "
                + lineOrEnd(actualLines, i);
    }

    private static String lineOrEnd(List<String> lines, int index) {
        return index < lines.size() ? lines.get(index) : "(no more lines)";
    }

    /** Returns the case files under {@code root}, as paths relative to it with {@code /} between names, in order. */
    static List<String> pathsIn(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(EXTENSION))
                    .map(file -> root.relativize(file).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
    }
}
