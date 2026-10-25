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

import java.util.List;
import org.junit.jupiter.api.Test;

final class NonNlsMarkersTest {

    @Test
    void aCommentOfMarkersAloneIsMarkersOnly() {
        assertThat(NonNlsMarkers.isMarkersOnly("//$NON-NLS-1$")).isTrue();
        assertThat(NonNlsMarkers.isMarkersOnly("//$NON-NLS-1$ //$NON-NLS-2$")).isTrue();
        assertThat(NonNlsMarkers.isMarkersOnly("//$NON-NLS-1$\t//$NON-NLS-12$")).isTrue();
    }

    @Test
    void anyOtherTextMakesACommentNotMarkersOnly() {
        // A blank after the slashes, text next to a marker, or a marker without its number or its closing dollar.
        List<String> comments = List.of(
                "// $NON-NLS-1$", "//$NON-NLS-1$ note", "// note //$NON-NLS-1$", "//$NON-NLS-$", "//$NON-NLS-1", "// foo");

        for (String comment : comments) {
            assertThat(NonNlsMarkers.isMarkersOnly(comment)).as(comment).isFalse();
        }
    }

    @Test
    void numbersListsTheMarkersInTheirOrder() {
        assertThat(NonNlsMarkers.numbers("//$NON-NLS-3$ //$NON-NLS-1$ //$NON-NLS-12$")).containsExactly(3, 1, 12);
    }

    @Test
    void numbersFindsMarkersAnywhereInAComment() {
        assertThat(NonNlsMarkers.numbers("// note //$NON-NLS-2$")).containsExactly(2);
        assertThat(NonNlsMarkers.numbers("// no markers here")).isEmpty();
    }

    @Test
    void markerSpellsWhatEclipseReads() {
        assertThat(NonNlsMarkers.marker(7)).isEqualTo("//$NON-NLS-7$");
        assertThat(NonNlsMarkers.numbers(NonNlsMarkers.marker(42))).containsExactly(42);
    }
}
