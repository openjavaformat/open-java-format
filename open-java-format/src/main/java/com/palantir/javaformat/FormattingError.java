/*
 * Copyright 2016 Google Inc.
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

package com.palantir.javaformat;

import com.google.common.base.Joiner;
import com.palantir.javaformat.java.FormatterDiagnostic;
import java.util.List;
import java.util.stream.StreamSupport;

/** An unchecked formatting error. */
public class FormattingError extends RuntimeException {

    private final List<FormatterDiagnostic> diagnostics;

    public FormattingError(FormatterDiagnostic diagnostic) {
        this(List.of(diagnostic));
    }

    public FormattingError(Iterable<FormatterDiagnostic> diagnostics) {
        super(Joiner.on("\n").join(diagnostics) + "\n");
        this.diagnostics =
                StreamSupport.stream(diagnostics.spliterator(), false).toList();
    }

    public List<FormatterDiagnostic> diagnostics() {
        return diagnostics;
    }
}
