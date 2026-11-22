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
import com.palantir.javaformat.java.FormatterService;
import com.palantir.javaformat.java.Replacement;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/** Stands for the JVM that formats what the IDE's own cannot: it formats nothing, and counts what it was asked. */
final class RecordingFormatterService implements FormatterService {

    static final String FORMATTED = "formatted in another JVM";

    private final AtomicInteger calls = new AtomicInteger();

    int calls() {
        return calls.get();
    }

    @Override
    @SuppressForbidden(reason = "FormatterService, kept in the SPI for compatibility, returns ImmutableList")
    public ImmutableList<Replacement> getFormatReplacements(String input, Collection<Range<Integer>> ranges) {
        calls.incrementAndGet();
        return ImmutableList.of(Replacement.create(0, input.length(), FORMATTED));
    }

    @Override
    public String formatSourceReflowStringsAndFixImports(String input) {
        calls.incrementAndGet();
        return FORMATTED;
    }

    @Override
    public String fixImports(String input) {
        calls.incrementAndGet();
        return FORMATTED;
    }
}
