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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** {@link ModifierOrderer} on the {@code value} modifier of value classes, a preview of JDK 28. */
@EnabledForJreRange(min = JRE.JAVA_28)
@Execution(ExecutionMode.CONCURRENT)
final class ModifierOrdererJava28Test {
    @Test
    void valueClass() throws FormatterException {
        assertThat(ModifierOrderer.reorderModifiers("value final public").getText())
                .isEqualTo("public final value");
    }
}
