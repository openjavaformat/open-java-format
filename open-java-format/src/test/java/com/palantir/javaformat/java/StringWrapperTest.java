/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.palantir.javaformat.java;

import static com.google.common.truth.Truth.assertThat;
import static com.palantir.javaformat.java.JavaFormatterOptions.Style;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class StringWrapperTest {
    @Test
    public void testAwkwardLineEndWrapping() throws Exception {
        String input = lines(
                "class T {",
                // This is a wide line, but has to be split in code because of 100-char limit.
                "  String s = someMethodWithQuiteALongNameThatWillGetUsUpCloseToTheColumnLimit() "
                        + "+ \"foo bar foo bar foo bar\";",
                "",
                "  String someMethodWithQuiteALongNameThatWillGetUsUpCloseToTheColumnLimit() {",
                "    return null;",
                "  }",
                "}");
        String output = lines(
                "class T {",
                "  String s = someMethodWithQuiteALongNameThatWillGetUsUpCloseToTheColumnLimit()",
                "      + \"foo bar foo bar foo bar\";",
                "",
                "  String someMethodWithQuiteALongNameThatWillGetUsUpCloseToTheColumnLimit() {",
                "    return null;",
                "  }",
                "}");

        assertThat(StringWrapper.wrap(100, input, Formatter.create())).isEqualTo(output);
    }

    @Test
    public void wrapsAStringWithEscapedBackslashes() throws Exception {
        // In D:\\tempDb the second backslash and the t are not an escaped tab. Splitting there left a lone backslash
        // at the end of a piece, where it escaped the closing quote.
        String input = lines(
                "class C {",
                "    void m(java.sql.Connection con) throws Exception {",
                "        con.createStatement()",
                "                .execute(\"ALTER DATABASE tempdb MODIFY FILE (NAME = 'tempdev',"
                        + " FILENAME = 'D:\\\\tempDb\\\\DATA\\\\tempdb.mdf')\");",
                "    }",
                "}");
        Formatter formatter = Formatter.createFormatter(
                JavaFormatterOptions.builder().style(Style.OJF).build());

        String output = formatter.formatSourceAndFixImports(input);

        assertThat(formatter.formatSourceAndFixImports(output)).isEqualTo(output);
    }

    @Test
    public void wrappingKeepsTheValueOfAStringWithEscapes() throws Exception {
        // At 40 columns the first line fills up right inside C:\\temp, where \\t is not an escaped tab.
        String input = lines("class T {", "  String s = \"copy the file to C:\\\\temp and back\";", "}");

        String output = StringWrapper.wrap(40, input, Formatter.create());

        assertThat(output).isNotEqualTo(input);
        assertThat(stringValue(output)).isEqualTo(stringValue(input));
    }

    /** The value javac gives the initializer of the only field: a string literal, or literals joined with +. */
    private static String stringValue(String source) throws IOException {
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///T.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        JavacTask task =
                (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, List.of(file));
        CompilationUnitTree unit = Iterables.getOnlyElement(task.parse());
        ClassTree type = (ClassTree) Iterables.getOnlyElement(unit.getTypeDecls());
        VariableTree field = (VariableTree) Iterables.getOnlyElement(type.getMembers());
        return concatenation(field.getInitializer());
    }

    private static String concatenation(ExpressionTree expression) {
        if (expression instanceof BinaryTree plus) {
            return concatenation(plus.getLeftOperand()) + concatenation(plus.getRightOperand());
        }
        return (String) ((LiteralTree) expression).getValue();
    }

    private static String lines(String... line) {
        return Joiner.on('\n').join(line) + '\n';
    }
}
