/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.javaformat.bootstrap;

import com.google.common.collect.BoundType;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import java.util.Collection;
import java.util.List;

public final class RangeUtils {

    /** Returns a range representation as parsed by "com.palantir.javaformat.java.CommandLineOptionsParser". */
    public static String toStringRange(Range<Integer> range) {
        int lower = range.lowerBoundType() == BoundType.CLOSED ? range.lowerEndpoint() : range.lowerEndpoint() + 1;
        int higher = range.upperBoundType() == BoundType.CLOSED ? range.upperEndpoint() : range.upperEndpoint() - 1;
        if (lower == higher) {
            return String.valueOf(lower);
        }
        return String.format("%s:%s", lower, higher);
    }

    /**
     * Returns the ranges as "--character-ranges" takes them. Its ranges are closed, so an empty range, which IntelliJ
     * IDEA passes among the others, has no form there: it is left out, as {@code FormatterServiceImpl} leaves it out
     * in process.
     */
    static List<String> toStringRanges(Collection<Range<Integer>> ranges) {
        return ranges.stream()
                .map(range -> range.canonical(DiscreteDomain.integers()))
                .filter(range -> !range.isEmpty())
                .map(RangeUtils::toStringRange)
                .toList();
    }

    private RangeUtils() {}
}
