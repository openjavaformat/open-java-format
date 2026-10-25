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

package com.palantir.javaformat.doc;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.CommentsHelper;
import org.junit.jupiter.api.Test;

/** A {@link ReservedWidth} takes the room of a NON-NLS marker that is written to its line later, and nothing else. */
final class ReservedWidthTest {

    private static final int WIDTH = " //$NON-NLS-1$".length();

    @Test
    void takesItsWidthOnTheLineButHasNoText() {
        ReservedWidth reserved = ReservedWidth.make(WIDTH);

        assertThat(reserved.getWidth()).isEqualTo((float) WIDTH);
        assertThat(reserved.getFlat()).isEmpty();
        assertThat(reserved.range().isEmpty()).isTrue();
    }

    @Test
    void movesTheColumnByItsWidthWhenTheBreaksAreComputed() {
        State before = State.startingState().withColumn(10);
        CommentsHelper noRewrite = (tok, maxWidth, column0) -> tok.getOriginalText();

        State after = ReservedWidth.make(WIDTH).computeBreaks(noRewrite, 120, before, Obs.createRoot(new NoopSink()));

        assertThat(after.column()).isEqualTo(10 + WIDTH);
    }
}
