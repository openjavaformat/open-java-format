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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.palantir.javaformat.Input;
import java.util.regex.Pattern;

/**
 * The code that {@code // @formatter:off} and {@code // @formatter:on} keep as written. It runs from the start of the
 * line with an off marker to the start of the line with the next on marker, or to the end of the file, so the on
 * marker itself is formatted; the whitespace at its edges is not part of it.
 *
 * <p>A marker is a line or block comment that starts with the marker text, the way IntelliJ IDEA and Eclipse write
 * them. The text elsewhere, in a string, a Javadoc or a Markdown doc comment, or later in a comment, is no marker, so
 * that code and documentation that mention it are formatted as usual. The markers are not configurable.
 */
final class FormatterOffRegions {
    private static final String OFF = "@formatter:off";

    // After `//` or `/*`, so not a Javadoc (`/**`) or a Markdown doc comment (`///`).
    private static final Pattern MARKER = Pattern.compile("/[/*][ \\t]*@formatter:(off|on)\\b");

    private final String text;
    private final RangeSet<Integer> code = TreeRangeSet.create();
    private int regionStart = -1;

    private FormatterOffRegions(String text) {
        this.text = text;
    }

    /** Whether {@code text} holds the off marker anywhere: if not, it has no code to keep, and need not be tokenised. */
    static boolean mayHoldMarkers(String text) {
        return text.contains(OFF);
    }

    /** The code to keep as written in {@code input}. */
    static RangeSet<Integer> find(JavaInput input) {
        if (!mayHoldMarkers(input.getText())) {
            return ImmutableRangeSet.of();
        }
        var regions = new FormatterOffRegions(input.getText());
        for (var token : input.getTokens()) {
            // Comments are among the toks before and after a token, in the order of the file.
            token.getToksBefore().forEach(regions::visit);
            token.getToksAfter().forEach(regions::visit);
        }
        if (regions.regionStart != -1) {
            regions.addRegion(regions.regionStart, regions.text.length());
        }
        return regions.code;
    }

    private void visit(Input.Tok tok) {
        if (!tok.isComment()) {
            return;
        }
        var marker = MARKER.matcher(tok.getOriginalText());
        if (!marker.lookingAt()) {
            return;
        }
        var off = marker.group(1).equals("off");
        if (off && regionStart == -1) {
            regionStart = lineStart(tok.getPosition());
        } else if (!off && regionStart != -1) {
            addRegion(regionStart, lineStart(tok.getPosition()));
            regionStart = -1;
        }
    }

    private int lineStart(int position) {
        var start = position;
        while (start > 0 && text.charAt(start - 1) != '\n' && text.charAt(start - 1) != '\r') {
            start--;
        }
        return start;
    }

    // Without the whitespace at its edges, which JavaOutput widens a replacement next to the region over.
    private void addRegion(int start, int end) {
        var whitespace = CharMatcher.whitespace();
        var codeStart = start;
        while (codeStart < end && whitespace.matches(text.charAt(codeStart))) {
            codeStart++;
        }
        var codeEnd = end;
        while (codeEnd > codeStart && whitespace.matches(text.charAt(codeEnd - 1))) {
            codeEnd--;
        }
        code.add(Range.closedOpen(codeStart, codeEnd));
    }
}
