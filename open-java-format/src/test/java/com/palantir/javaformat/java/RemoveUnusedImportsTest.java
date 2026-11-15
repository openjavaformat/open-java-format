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

package com.palantir.javaformat.java;

import static com.google.common.truth.Truth.assertThat;
import static com.palantir.javaformat.java.RemoveUnusedImports.removeUnusedImports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * {@link RemoveUnusedImports} on its own, where a case under {@code test-cases-java21/imports} cannot show it. A case
 * fixes the imports before it formats, and formatting takes a doubled blank line away by itself, while the command line
 * formats first.
 */
@Execution(ExecutionMode.CONCURRENT)
final class RemoveUnusedImportsTest {
    // An unused import between blank lines takes one of them with it (#37, from google/google-java-format#1436 and
    // google/google-java-format#1437).
    @Test
    void unusedImportBetweenBlankLinesTakesOneOfThem() throws FormatterException {
        var input = """
            package com.example;

            import static io.grpc.MethodDescriptor.generateFullMethodName;

            /**
             * Javadoc for class.
             */
            public class TestBug {}
            """;

        assertThat(removeUnusedImports(input)).isEqualTo("""
            package com.example;

            /**
             * Javadoc for class.
             */
            public class TestBug {}
            """);
    }

    @Test
    void unusedImportsBetweenBlankLinesTakeOneOfThem() throws FormatterException {
        var input = """
            package com.example;

            import com.foo.Unused1;
            import com.foo.Unused2;

            public class TestBug {}
            """;

        assertThat(removeUnusedImports(input)).isEqualTo("""
            package com.example;

            public class TestBug {}
            """);
    }

    @Test
    void unusedImportAtTheStartTakesTheBlankLineAfterIt() throws FormatterException {
        var input = """
            import com.foo.Unused;

            public class TestBug {}
            """;

        assertThat(removeUnusedImports(input)).isEqualTo("""
            public class TestBug {}
            """);
    }
}
