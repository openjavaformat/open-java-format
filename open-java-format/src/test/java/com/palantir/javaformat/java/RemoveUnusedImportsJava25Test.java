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

import static com.google.common.truth.Truth.assertThat;
import static com.palantir.javaformat.java.RemoveUnusedImports.removeUnusedImports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** {@link RemoveUnusedImports} on module imports, which are final from JDK 25 on. */
@EnabledForJreRange(min = JRE.JAVA_25)
@Execution(ExecutionMode.CONCURRENT)
final class RemoveUnusedImportsJava25Test {
    // Module imports (JEP 511, `import module foo.bar;`) parse to JCModuleImport, a sibling of JCImport rather than a
    // subtype. They must never be reported as unused (see isUnused), and must not crash buildReplacements when mixed
    // with ordinary imports, some used and some not.
    @Test
    void moduleImportStaysAmongOrdinaryImports() throws FormatterException {
        var input = """
            import module java.base;
            import java.util.List;
            import java.util.Map;
            class T { List<String> xs; }
            """;

        assertThat(removeUnusedImports(input)).isEqualTo("""
            import module java.base;
            import java.util.List;
            class T { List<String> xs; }
            """);
    }
}
