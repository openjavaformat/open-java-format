/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.javaformat.java;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * JBang reads its directives from the line comments before the first line of code, and a shell runs the first line of
 * a script, so those lines are left exactly as written. The goldens {@code ojf-issue-24-jbang-*} show whole scripts,
 * with the same directives after the first line of code formatted like any other comment.
 */
@Execution(ExecutionMode.CONCURRENT)
final class JBangDirectivesTest {

    private static final Formatter FORMATTER = Formatter.createFormatter(
            JavaFormatterOptions.builder().style(Style.OJF).build());

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Every name JBang knows.
                "//CDS",
                "//COMPILE_OPTIONS -Xlint:all",
                "//DEPS info.picocli:picocli:4.7.6",
                "//DESCRIPTION Prints a greeting",
                "//DOCS guide=./readme.md",
                "//FILES application.properties",
                "//GAV org.example:hello:1.0",
                "//GROOVY 3.0.19",
                "//JAVA 21+",
                "//JAVAAGENT myagent.jar=option1,option2",
                "//JAVAC_OPTIONS -parameters",
                "//JAVA_OPTIONS -Xmx512m",
                "//KOTLIN 2.0.21",
                "//MAIN org.example.Hello",
                "//MANIFEST Built-By=jbang",
                "//MODULE org.example.hello",
                "//NATIVE_OPTIONS --no-fallback",
                "//NOINTEGRATIONS",
                "//PREVIEW",
                "//REPOS central,jitpack",
                "//RUNTIME_OPTIONS -XX:+UseSerialGC",
                "//SOURCES Helper.java",
                // A directive for a build integration: Quarkus reads //Q:CONFIG.
                "//Q:CONFIG quarkus.banner.enabled=false",
                // What JBang's syntax allows after the name.
                "//DEPS\tinfo.picocli:picocli:4.7.6",
                "//DEPS info.picocli:picocli:4.7.6 // parses the command line",
                "//DEPS org.postgresql:postgresql:${env.DB_VERSION:42.6.0}",
                "//DEPS",
                // Longer than the 120 columns at which a line comment is wrapped.
                "//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2 org.slf4j:slf4j-simple:2.0.13"
                        + " com.squareup.okhttp3:okhttp:4.12.0",
            })
    void keepsDirectiveBeforeFirstLineOfCode(String directive) throws FormatterException {
        String script = directive + "\n\nclass Hello {}\n";
        assertThat(FORMATTER.formatSource(script)).isEqualTo(script);
    }

    @ParameterizedTest
    @CsvSource(
            delimiterString = " => ",
            value = {
                // Not a name JBang knows.
                "//DEPSSS a:b:1 => // DEPSSS a:b:1",
                "//DEPS_ a:b:1 => // DEPS_ a:b:1",
                "//TODO pin the versions => // TODO pin the versions",
                // No whitespace after the name.
                "//JAVA21+ => // JAVA21+",
                "//DEPS:a:b:1 => // DEPS:a:b:1",
                // Directive names are upper case.
                "//deps a:b:1 => // deps a:b:1",
                "//Deps a:b:1 => // Deps a:b:1",
                // Three slashes, as in a Markdown doc comment.
                "///DEPS a:b:1 => /// DEPS a:b:1",
                // Switched off on purpose: JBang's own templates list optional dependencies this way.
                "// DEPS a:b:1 => // DEPS a:b:1",
                "// //DEPS a:b:1 => // //DEPS a:b:1",
            })
    void formatsLookalikeAsOrdinaryComment(String comment, String expected) throws FormatterException {
        assertThat(FORMATTER.formatSource(comment + "\n\nclass Hello {}\n"))
                .isEqualTo(expected + "\n\nclass Hello {}\n");
    }

    @Test
    void formatsShellLineAfterFirstLineAsOrdinaryComment() throws FormatterException {
        // The shell runs the first line of the file only.
        String script = "/* License */\n///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\nclass Hello {}\n";
        assertThat(FORMATTER.formatSource(script))
                .isEqualTo("/* License */\n/// usr/bin/env jbang \"$0\" \"$@\" ; exit $?\nclass Hello {}\n");
    }

    @Test
    void formatsFirstLineThatRunsNoCommandAsOrdinaryComment() throws FormatterException {
        // The first word is not a path, so the shell would have nothing to run.
        assertThat(FORMATTER.formatSource("//Hello from JBang\nclass Hello {}\n"))
                .isEqualTo("// Hello from JBang\nclass Hello {}\n");
    }
}
