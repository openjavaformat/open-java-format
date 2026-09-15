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

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A formatter version as this project writes them: dot-separated numbers — upstream's X.Y.Z, or 2.98.0.1 with a build
 * number of our own — optionally followed by what {@code git describe} adds after a tag, such as {@code -3-gabc1234},
 * and {@code .dirty}. Numbers compare numerically with a missing one counting as zero; commits after the tag only break
 * a tie.
 */
final class FormatterVersion implements Comparable<FormatterVersion> {
    private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)(?:-(\\d+)-g\\p{XDigit}+)?(?:\\.dirty)?");

    private final int[] numbers;
    private final int commitsAfterTag;

    private FormatterVersion(int[] numbers, int commitsAfterTag) {
        this.numbers = numbers;
        this.commitsAfterTag = commitsAfterTag;
    }

    static Optional<FormatterVersion> parse(String version) {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            int[] numbers = Arrays.stream(matcher.group(1).split("\\."))
                    .mapToInt(Integer::parseInt)
                    .toArray();
            int commitsAfterTag = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            return Optional.of(new FormatterVersion(numbers, commitsAfterTag));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(FormatterVersion other) {
        for (int i = 0; i < Math.max(numbers.length, other.numbers.length); i++) {
            int difference = Integer.compare(numberAt(i), other.numberAt(i));
            if (difference != 0) {
                return difference;
            }
        }
        return Integer.compare(commitsAfterTag, other.commitsAfterTag);
    }

    private int numberAt(int index) {
        return index < numbers.length ? numbers[index] : 0;
    }
}
