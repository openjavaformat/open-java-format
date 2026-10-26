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
import java.util.List;
import java.util.regex.Pattern;

/**
 * A formatter test case written as a file: an optional header, six or more dashes that end a line, the code before
 * formatting, a second run of such dashes and the code expected after it. There are no settings yet, so the header
 * holds only blank lines and {@code #} comments, which explain the case; a file without one starts with the first
 * dashes.
 *
 * <p>The blocks are the text of the file as written. The before block runs from the line after the first dashes up to
 * the second ones: on a line of their own they leave it its last line break, and at the end of its last line they
 * leave it none, for code that does not end in one. The after block runs from the line after the second dashes to the
 * end of the file. Line breaks stay {@code \n}, {@code \r\n} or {@code \r}, whichever the file has, and a line of code
 * that ends in six or more dashes cannot be part of a case. {@code afterLine} is the line of the file the after block
 * starts on, so that a failure can name the line where the output departs from it.
 */
record CaseFile(String before, String after, int afterLine) {
    static final String EXTENSION = ".case";

    /** Six or more dashes that end a line, with the line break after them. */
    private static final Pattern SEPARATOR = Pattern.compile("-{6,}(?:\\r\\n|\\r|\\n|\\z)");

    /** Splits the text of the case file at {@code path}, which only names the file in an error. */
    static CaseFile parse(String path, String text) {
        var separators = SEPARATOR.matcher(text).results().toList();

        if (separators.size() != 2) {
            throw new IllegalArgumentException(
                    path + ": a case has two lines of six or more dashes, found " + separators.size());
        }

        for (var line : text.substring(0, separators.getFirst().start()).lines().toList()) {
            if (!line.isBlank() && !line.startsWith("#")) {
                throw new IllegalArgumentException(path
                        + ": there are no settings yet, the header takes blank lines and # comments only: " + line);
            }
        }

        var afterStart = separators.getLast().end();
        var afterLine = (int) text.substring(0, afterStart).lines().count() + 1;

        return new CaseFile(
                text.substring(separators.getFirst().end(), separators.getLast().start()),
                text.substring(afterStart),
                afterLine);
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
