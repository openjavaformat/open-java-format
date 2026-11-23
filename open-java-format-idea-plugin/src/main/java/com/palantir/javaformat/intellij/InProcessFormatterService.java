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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.FormatterService;
import com.palantir.javaformat.java.Replacement;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * The formatter in the IDE's own JVM, for a project that formats with these jars.
 *
 * <p>It leaves a file with a syntax error alone, as the formatters that run in a process of their own do. And what it
 * cannot format here it formats in a JVM of its own: a file that the formatter fails on with an {@code Error}, and
 * every file once the formatter turns out not to load or link in this JVM. The IDE process is the faster way, never
 * the only one.
 */
final class InProcessFormatterService implements FormatterService {

    private final InProcessFormatters formatters;
    private final List<Path> jars;
    private final FormatterService separateJvm;
    private final Consumer<Throwable> onFailure;

    /**
     * separateJvm: formats what this JVM cannot. onFailure: told why, each time a file goes there for a reason that
     * {@link InProcessFormatters#unavailable} did not give beforehand.
     */
    InProcessFormatterService(
            InProcessFormatters formatters,
            List<Path> jars,
            FormatterService separateJvm,
            Consumer<Throwable> onFailure) {
        this.formatters = formatters;
        this.jars = jars;
        this.separateJvm = separateJvm;
        this.onFailure = onFailure;
    }

    @Override
    @SuppressForbidden(reason = "FormatterService, kept in the SPI for compatibility, returns ImmutableList")
    public ImmutableList<Replacement> getFormatReplacements(String input, Collection<Range<Integer>> ranges)
            throws FormatterException {
        return run(formatter -> formatter.getFormatReplacements(input, ranges), ImmutableList.of());
    }

    @Override
    public String formatSourceReflowStringsAndFixImports(String input) throws FormatterException {
        return run(formatter -> formatter.formatSourceReflowStringsAndFixImports(input), input);
    }

    @Override
    public String fixImports(String input) throws FormatterException {
        return run(formatter -> formatter.fixImports(input), input);
    }

    private interface Call<T> {
        T with(FormatterService formatter) throws FormatterException;
    }

    /** unparsed: the answer for a file with a syntax error, which is to change nothing. */
    private <T> T run(Call<T> call, T unparsed) throws FormatterException {
        // A project can still hold this service after the formatter failed for another, or in another thread.
        if (formatters.unavailable().isPresent()) {
            return call.with(separateJvm);
        }
        FormatterService formatter;
        try {
            // For every file, so that a jar rebuilt in place formats the next one, as in a JVM started for it.
            formatter = formatters.load(jars);
        } catch (IllegalStateException e) {
            onFailure.accept(e);
            return call.with(separateJvm);
        }
        var thread = Thread.currentThread();
        var contextClassLoader = thread.getContextClassLoader();
        // The IDE's, on its threads: a library that looks its services up there would find the IDE's.
        thread.setContextClassLoader(formatter.getClass().getClassLoader());
        try {
            return call.with(formatter);
        } catch (FormatterException e) {
            if (isSyntaxError(e)) {
                return unparsed;
            }
            throw e;
        } catch (Error e) {
            // Out of memory, or a JVM that is breaking down, is the IDE's to deal with.
            if (e instanceof VirtualMachineError && !(e instanceof StackOverflowError)) {
                throw e;
            }
            // A process of the formatter's own exits with any other, where here it would pass every catch of the
            // plugin: javac's Abort and its assertions are Errors too. And the file may well format there: the main
            // thread of a JVM has a deeper stack than the IDE's threads.
            if (e instanceof LinkageError linkageError) {
                formatters.failedToLink(linkageError);
            }
            onFailure.accept(e);
        } finally {
            thread.setContextClassLoader(contextClassLoader);
        }
        return call.with(separateJvm);
    }

    /**
     * What the command line prints as {@code <file>:<line>:<column>: error:}, which the formatters that run it take
     * for a syntax error and answer by leaving the file alone: a file is rarely free of them while it is typed in.
     */
    private static boolean isSyntaxError(FormatterException e) {
        return e.diagnostics().stream().anyMatch(diagnostic -> diagnostic.line() >= 0 && diagnostic.column() >= 0);
    }
}
