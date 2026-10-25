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

package com.palantir.javaformat.doc;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Range;
import com.google.errorprone.annotations.Immutable;
import com.palantir.javaformat.CommentsHelper;
import com.palantir.javaformat.Op;
import com.palantir.javaformat.Output;

/**
 * A leaf node in a {@link Doc} that takes room on its line without writing anything: the room for a
 * {@code //$NON-NLS-n$} marker after the string literal it marks, which {@link Comment} writes at the end of the
 * literal's line once the line breaks are known. So the layout already counts the marker on that line.
 */
@Immutable
public final class ReservedWidth extends Doc implements Op {
    private final int width;

    private ReservedWidth(int width) {
        this.width = width;
    }

    public static ReservedWidth make(int width) {
        return new ReservedWidth(width);
    }

    @Override
    public void add(DocBuilder builder) {
        builder.add(this);
    }

    @Override
    protected float computeWidth() {
        return width;
    }

    @Override
    protected String computeFlat() {
        return "";
    }

    @Override
    protected Range<Integer> computeRange() {
        return Doc.EMPTY_RANGE;
    }

    @Override
    public State computeBreaks(
            CommentsHelper commentsHelper, int maxWidth, State state, Obs.ExplorationNode observationNode) {
        return state.withColumn(state.column() + width);
    }

    @Override
    public void write(State state, Output output) {}

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("width", width).toString();
    }
}
