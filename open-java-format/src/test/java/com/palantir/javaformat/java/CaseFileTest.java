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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CaseFileTest {
    @Test
    void theBlocksLieBetweenTheTwoSeparatorLines() {
        assertThat(CaseFile.parse("a.case", "------\nclass A{}\n------\nclass A {}\n"))
                .isEqualTo(new CaseFile("class A{}\n", "class A {}\n", 4));
    }

    @Test
    void aHeaderBeforeTheFirstSeparatorExplainsTheCase() {
        var text = "# Why this case exists.\n\n----------\nclass A{}\n----------\nclass A {}\n";

        assertThat(CaseFile.parse("a.case", text)).isEqualTo(new CaseFile("class A{}\n", "class A {}\n", 6));
    }

    @Test
    void aFileWithoutASeparatorIsNotACase() {
        assertThatThrownBy(() -> CaseFile.parse("dir/a.case", "class A {}\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dir/a.case")
                .hasMessageContaining("found 0");
    }

    @Test
    void aFileWithOneSeparatorIsNotACase() {
        assertThatThrownBy(() -> CaseFile.parse("a.case", "class A{}\n------\nclass A {}\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.case")
                .hasMessageContaining("found 1");
    }

    @Test
    void aFileWithThreeSeparatorsIsNotACase() {
        assertThatThrownBy(() -> CaseFile.parse("a.case", "------\ny\n------\nz\n------\nw\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.case")
                .hasMessageContaining("found 3");
    }

    @Test
    void fiveDashesOrATrailingSpaceAreNotASeparator() {
        assertThat(CaseFile.parse("a.case", "------\na\n-----\nb\n------ \nc\n------\nd\n"))
                .isEqualTo(new CaseFile("a\n-----\nb\n------ \nc\n", "d\n", 8));
    }

    @Test
    void lineBreaksTabsAndTrailingSpacesStayAsWritten() {
        var text = "------\r\nclass A {\r\n\tint x;  \r\n}\r\n------\r\nclass A {\r\n    int x;\r\n}";

        assertThat(CaseFile.parse("a.case", text))
                .isEqualTo(new CaseFile("class A {\r\n\tint x;  \r\n}\r\n", "class A {\r\n    int x;\r\n}", 6));
    }

    @Test
    void dashesThatEndALineCloseTheBlockBeforeThem() {
        assertThat(CaseFile.parse("a.case", "------\nclass A{}------\nclass A {}\n"))
                .isEqualTo(new CaseFile("class A{}", "class A {}\n", 3));
    }

    @Test
    void aCodeLineThatEndsInDashesCountsAsASeparator() {
        assertThatThrownBy(() -> CaseFile.parse("a.case", "------\n// ----------\nclass A{}\n------\nclass A {}\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found 3");
    }

    @Test
    void carriageReturnsAloneBreakLinesToo() {
        assertThat(CaseFile.parse("a.case", "------\rclass A{}\r------\rclass A {}\r"))
                .isEqualTo(new CaseFile("class A{}\r", "class A {}\r", 4));
    }

    @Test
    void aSettingIsRejectedWhileThereAreNone() {
        assertThatThrownBy(() -> CaseFile.parse("a.case", "# fine\nstyle: google\n------\na\n------\nb\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.case")
                .hasMessageContaining("style: google");
    }

    @Test
    void findsCaseFilesByExtensionInSubfolders(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("sub"));
        for (var name : List.of("b.case", "sub/a.case", "notes.txt", ".DS_Store", "sub/c.case.txt")) {
            Files.writeString(root.resolve(name), "");
        }

        assertThat(CaseFile.pathsIn(root)).containsExactly("b.case", "sub/a.case");
    }

    @Test
    void aDifferenceNamesItsLineInTheFile() {
        var testCase = new CaseFile("x\n", "a\nb\nc\n", 10);

        assertThat(testCase.firstDifference("a\nB\nc\n")).isEqualTo("line 11\n  expected: b\n  actual:   B");
    }

    @Test
    void aShorterOutputNamesTheFirstMissingLine() {
        var testCase = new CaseFile("x\n", "a\nb\nc\n", 10);

        assertThat(testCase.firstDifference("a\n")).isEqualTo("line 11\n  expected: b\n  actual:   (no more lines)");
    }

    @Test
    void aLongerOutputNamesTheFirstExtraLine() {
        var testCase = new CaseFile("x\n", "a\nb\nc\n", 10);

        assertThat(testCase.firstDifference("a\nb\nc\nd\n"))
                .isEqualTo("line 13\n  expected: (no more lines)\n  actual:   d");
    }

    @Test
    void sameLinesWithOtherLineBreaksAreSaidToBeSo() {
        var testCase = new CaseFile("x\n", "a\nb\n", 10);

        assertThat(testCase.firstDifference("a\r\nb\r\n"))
                .isEqualTo("the lines are the same, their line breaks differ");
    }
}
