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

import static org.assertj.core.api.Assertions.fail;

import com.google.common.collect.Streams;
import com.google.common.truth.Truth;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

final class NewlinesTest {
    @Test
    void offsets() {
        Truth.assertThat(Streams.stream(Newlines.lineOffsetIterator("foo\nbar\n"))
                        .toList())
                .containsExactly(0, 4, 8);
        Truth.assertThat(Streams.stream(Newlines.lineOffsetIterator("foo\nbar")).toList())
                .containsExactly(0, 4);

        Truth.assertThat(Streams.stream(Newlines.lineOffsetIterator("foo\rbar\r"))
                        .toList())
                .containsExactly(0, 4, 8);
        Truth.assertThat(Streams.stream(Newlines.lineOffsetIterator("foo\rbar")).toList())
                .containsExactly(0, 4);

        Truth.assertThat(Streams.stream(Newlines.lineOffsetIterator("foo\r\nbar\r\n"))
                        .toList())
                .containsExactly(0, 5, 10);
        Truth.assertThat(Streams.stream(Newlines.lineOffsetIterator("foo\r\nbar"))
                        .toList())
                .containsExactly(0, 5);
    }

    @Test
    void lines() {
        Truth.assertThat(Streams.stream(Newlines.lineIterator("foo\nbar\n")).toList())
                .containsExactly("foo\n", "bar\n");
        Truth.assertThat(Streams.stream(Newlines.lineIterator("foo\nbar")).toList())
                .containsExactly("foo\n", "bar");

        Truth.assertThat(Streams.stream(Newlines.lineIterator("foo\rbar\r")).toList())
                .containsExactly("foo\r", "bar\r");
        Truth.assertThat(Streams.stream(Newlines.lineIterator("foo\rbar")).toList())
                .containsExactly("foo\r", "bar");

        Truth.assertThat(Streams.stream(Newlines.lineIterator("foo\r\nbar\r\n")).toList())
                .containsExactly("foo\r\n", "bar\r\n");
        Truth.assertThat(Streams.stream(Newlines.lineIterator("foo\r\nbar")).toList())
                .containsExactly("foo\r\n", "bar");
    }

    @Test
    void terminalOffset() {
        Iterator<Integer> it = Newlines.lineOffsetIterator("foo\nbar\n");
        it.next();
        it.next();
        it.next();
        try {
            it.next();
            fail("fail");
        } catch (NoSuchElementException e) {
            // expected
        }

        it = Newlines.lineOffsetIterator("foo\nbar");
        it.next();
        it.next();
        try {
            it.next();
            fail("fail");
        } catch (NoSuchElementException e) {
            // expected
        }
    }

    @Test
    void terminalLine() {
        Iterator<String> it = Newlines.lineIterator("foo\nbar\n");
        it.next();
        it.next();
        try {
            it.next();
            fail("fail");
        } catch (NoSuchElementException e) {
            // expected
        }

        it = Newlines.lineIterator("foo\nbar");
        it.next();
        it.next();
        try {
            it.next();
            fail("fail");
        } catch (NoSuchElementException e) {
            // expected
        }
    }
}
