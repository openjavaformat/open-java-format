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

import com.google.common.collect.ImmutableList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The markers with which Eclipse exempts a string literal from its warning about non-externalized strings: a trailing
 * line comment {@code //$NON-NLS-n$}, where n is the position of the literal among the string literals of its line,
 * one marker per exempted literal, as in {@code //$NON-NLS-1$ //$NON-NLS-2$}. Eclipse reads a marker only with this
 * exact spelling and only on the line of its literal, so the formatter leaves the spelling alone, never wraps a comment
 * of markers, and when it moves a literal to another line it moves the marker along and renumbers it (#23): see
 * {@link com.palantir.javaformat.OpsBuilder#build()}, which pairs each marker with its literal, and
 * {@link JavaOutput#appendNonNlsMarkers}, which writes it on the literal's line.
 */
public final class NonNlsMarkers {
    private static final Pattern MARKER = Pattern.compile("//\\$NON-NLS-(\\d{1,9})\\$");

    /** A line comment that holds markers and nothing else. */
    private static final Pattern MARKERS_ONLY =
            Pattern.compile("//\\$NON-NLS-\\d{1,9}\\$([ \\t]+//\\$NON-NLS-\\d{1,9}\\$)*");

    public static boolean isMarkersOnly(String comment) {
        return comment.startsWith("//$NON-NLS-") && MARKERS_ONLY.matcher(comment).matches();
    }

    /** The numbers of the markers in a comment, in order. */
    public static ImmutableList<Integer> numbers(String comment) {
        ImmutableList.Builder<Integer> numbers = ImmutableList.builder();
        Matcher matcher = MARKER.matcher(comment);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }
        return numbers.build();
    }

    public static String marker(int n) {
        return "//$NON-NLS-" + n + "$";
    }

    private NonNlsMarkers() {}
}
