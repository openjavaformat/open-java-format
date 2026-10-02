/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.javaformat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.errorprone.annotations.Immutable;
import com.palantir.javaformat.doc.Doc;
import com.palantir.javaformat.doc.Level;
import java.io.IOException;

/**
 * How a {@link Level} is broken when it does not fit on one line. Each case's {@code toString()} is what
 * {@link com.palantir.javaformat.doc.LevelDelimitedFlatValueDocVisitor} prints for it.
 */
@Immutable
@JsonSerialize(using = BreakBehaviour.Json.class)
public sealed interface BreakBehaviour {

    static BreakBehaviour breakThisLevel() {
        return new BreakThisLevel();
    }

    static BreakBehaviour preferBreakingLastInnerLevel(boolean keepIndentWhenInlined) {
        return new PreferBreakingLastInnerLevel(keepIndentWhenInlined);
    }

    static BreakBehaviour inlineSuffix() {
        return new InlineSuffix();
    }

    static BreakBehaviour breakOnlyIfInnerLevelsThenFitOnOneLine(boolean keepIndentWhenInlined) {
        return new BreakOnlyIfInnerLevelsThenFitOnOneLine(keepIndentWhenInlined);
    }

    /** Break this level. */
    record BreakThisLevel() implements BreakBehaviour {
        @Override
        public String toString() {
            return "breakThisLevel()";
        }
    }

    /**
     * If the last level is breakable, prefer breaking it if it will keep the rest of this level on line line.
     *
     * @param keepIndentWhenInlined whether to keep this level's indent when inlined as a recursive level (when
     *     reached via a previous `preferBreakingLastInnerLevel` whose breakability was
     *     {@link LastLevelBreakability#CHECK_INNER})
     */
    record PreferBreakingLastInnerLevel(boolean keepIndentWhenInlined) implements BreakBehaviour {
        @Override
        public String toString() {
            return "preferBreakingLastInnerLevel(" + keepIndentWhenInlined + ")";
        }
    }

    /**
     * Attempt to inline the suffix of this level (which must be a {@link Level} and the last doc), recursing into the
     * {@link Level} just before the last {@link Level} (if there is such a level) to see if that can be broken instead.
     *
     * <p>This behaves like {@link BreakThisLevel} if we couldn't recurse into such an inner level, or if the suffix
     * level doesn't fit on the last line.
     */
    record InlineSuffix() implements BreakBehaviour {
        @Override
        public String toString() {
            return "inlineSuffix()";
        }
    }

    /**
     * Break if by doing so all inner levels then fit on a single line. However, don't break if we can fit in the
     * {@link Doc docs} up to the first break (which might be nested inside the next doc if it's a {@link Level}), in
     * order to prevent exceeding the maxLength accidentally.
     *
     * @param keepIndentWhenInlined whether to keep this level's indent when inlined as a recursive level
     */
    record BreakOnlyIfInnerLevelsThenFitOnOneLine(boolean keepIndentWhenInlined) implements BreakBehaviour {
        @Override
        public String toString() {
            return "breakOnlyIfInnerLevelsThenFitOnOneLine(" + keepIndentWhenInlined + ")";
        }
    }

    /** Writes the case as {@code type} and its flag, if it has one. */
    final class Json extends JsonSerializer<BreakBehaviour> {
        @Override
        public void serialize(BreakBehaviour value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            switch (value) {
                case BreakThisLevel() -> gen.writeObjectField("type", "breakThisLevel");
                case PreferBreakingLastInnerLevel(boolean keepIndentWhenInlined) -> {
                    gen.writeObjectField("type", "preferBreakingLastInnerLevel");
                    gen.writeObjectField("keepIndentWhenInlined", keepIndentWhenInlined);
                }
                case InlineSuffix() -> gen.writeObjectField("type", "inlineSuffix");
                case BreakOnlyIfInnerLevelsThenFitOnOneLine(boolean keepIndentWhenInlined) -> {
                    gen.writeObjectField("type", "breakOnlyIfInnerLevelsThenFitOnOneLine");
                    gen.writeObjectField("keepIndentWhenInlined", keepIndentWhenInlined);
                }
            }
            gen.writeEndObject();
        }
    }
}
