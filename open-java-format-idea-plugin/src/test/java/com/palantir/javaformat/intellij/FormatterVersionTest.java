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

package com.palantir.javaformat.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatterVersionTest {

    @Test
    void buildNumberOrdersReleasesOfTheSameUpstreamVersion() {
        assertThat(version("2.98.0.1")).isLessThan(version("2.98.0.2"));
    }

    @Test
    void numbersCompareNumerically() {
        assertThat(version("2.98.0.10")).isGreaterThan(version("2.98.0.9"));
    }

    @Test
    void missingBuildNumberCountsAsZero() {
        assertThat(version("2.98.0")).isLessThan(version("2.98.0.1"));
        assertThat(version("2.98.0")).isEqualByComparingTo(version("2.98.0.0"));
    }

    @Test
    void commitsAfterATagOnlyBreakATie() {
        assertThat(version("2.98.0.1-3-gabc1234")).isGreaterThan(version("2.98.0.1"));
        assertThat(version("2.98.0-5-gabc1234")).isLessThan(version("2.98.0.1"));
    }

    @Test
    void dirtyBuildsParse() {
        assertThat(FormatterVersion.parse("2.98.0.1-3-gabc1234.dirty")).isPresent();
    }

    @Test
    void anythingElseDoesNotParse() {
        assertThat(FormatterVersion.parse("unspecified")).isEmpty();
        assertThat(FormatterVersion.parse("2.98.0-rc1")).isEmpty();
    }

    private static FormatterVersion version(String version) {
        return FormatterVersion.parse(version).orElseThrow();
    }
}
