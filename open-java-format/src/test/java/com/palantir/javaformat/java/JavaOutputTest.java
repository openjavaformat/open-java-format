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

import org.junit.jupiter.api.Test;

final class JavaOutputTest {

    @Test
    void cachesTheIndentationStrings() {
        // Every output line used to get a fresh `" ".repeat(indent)`; the usual widths now come from a cache.
        assertThat(JavaOutput.spaces(4)).isEqualTo("    ");
        assertThat(JavaOutput.spaces(4)).isSameAs(JavaOutput.spaces(4));
        assertThat(JavaOutput.spaces(100)).isSameAs(JavaOutput.spaces(100));
    }

    @Test
    void returnsNoSpacesForZeroOrLess() {
        assertThat(JavaOutput.spaces(0)).isEmpty();
        assertThat(JavaOutput.spaces(-1)).isEmpty();
    }

    @Test
    void buildsWidthsBeyondTheCache() {
        assertThat(JavaOutput.spaces(104)).isEqualTo(" ".repeat(104));
    }
}
