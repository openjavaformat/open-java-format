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

package com.palantir.javaformat.intellij;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Range;
import com.palantir.javaformat.java.Replacement;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * What {@link JavacAccessTest} runs in a JVM started as the IDE's is, to see what the formatter can do there: the
 * JVM of the tests has javac exported to everything, as the build compiles the formatter. The arguments are the
 * formatter's jars, and the file to format comes on the standard input.
 */
final class InProcessFormatterProbe {

    /** Between the answers of a JVM that can format: no file has one. */
    static final String BETWEEN_ANSWERS = "\u0000";

    static final String SYNTAX_ERROR = "class {";

    private InProcessFormatterProbe() {}

    public static void main(String[] args) throws Exception {
        var out = new PrintStream(System.out, false, UTF_8);
        var jars = Arrays.stream(args).map(Path::of).toList();
        var formatters = InProcessFormatters.IDE;
        var unavailable = formatters.unavailable();
        if (unavailable.isPresent()) {
            out.println("unavailable: " + unavailable.get());
            try {
                formatters.load(jars);
                out.println("loaded");
            } catch (IllegalStateException e) {
                out.println(
                        "refused: " + (e.getCause() == null ? e : e.getCause().getMessage()));
            }
        } else {
            var input = new String(System.in.readAllBytes(), UTF_8);
            var anotherJvm = new RecordingFormatterService();
            var formatter = new InProcessFormatterService(formatters, jars, anotherJvm, failure -> {
                throw new AssertionError("could not format in this JVM", failure);
            });
            // From the last one, so that the offsets of the others still hold.
            List<Replacement> replacements =
                    formatter.getFormatReplacements(input, List.of(Range.closedOpen(0, input.length())));
            var replaced = new StringBuilder(input);
            for (var index = replacements.size() - 1; index >= 0; index--) {
                var replacement = replacements.get(index);
                replaced.replace(
                        replacement.getReplaceRange().lowerEndpoint(),
                        replacement.getReplaceRange().upperEndpoint(),
                        replacement.getReplacementString());
            }
            var jdkCompiler = ModuleLayer.boot().findModule("jdk.compiler").orElseThrow();
            out.print(String.join(
                    BETWEEN_ANSWERS,
                    formatter.formatSourceReflowStringsAndFixImports(input),
                    replaced,
                    formatter.fixImports(input),
                    formatter.fixImports(SYNTAX_ERROR),
                    // What this class can reach of javac, which in the IDE is what every plugin can.
                    JavacAccess.PACKAGES.stream()
                            .filter(javacPackage ->
                                    jdkCompiler.isExported(javacPackage, InProcessFormatterProbe.class.getModule()))
                            .toList()
                            .toString(),
                    "formatted in another JVM: " + anotherJvm.calls()));
        }
        out.flush();
    }
}
