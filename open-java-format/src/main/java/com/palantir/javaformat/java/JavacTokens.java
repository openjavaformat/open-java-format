/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.palantir.javaformat.java;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.parser.Tokens.Token;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.util.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** A wrapper around javac's lexer. */
class JavacTokens {

    /** The lexer eats terminal comments, so feed it one we don't care about. */
    // TODO(b/33103797): fix javac and remove the work-around
    private static final CharSequence EOF_COMMENT = "\n//EOF";

    /** An unprocessed input token, including whitespace and comments. */
    static class RawTok {
        @Nullable
        private final TokenKind kind;

        private final int pos;
        private final int endPos;

        RawTok(@Nullable TokenKind kind, int pos, int endPos) {
            this.kind = kind;
            this.pos = pos;
            this.endPos = endPos;
        }

        /** The token kind, or {@code null} for whitespace and comments. */
        @Nullable
        public TokenKind kind() {
            return kind;
        }

        /** The start position. */
        public int pos() {
            return pos;
        }

        /** The end position. */
        public int endPos() {
            return endPos;
        }
    }

    /** Lex the input and return a list of {@link RawTok}s. */
    public static List<RawTok> getTokens(String source, Context context, Set<TokenKind> stopTokens) {
        if (source == null) {
            return List.of();
        }
        ScannerFactory fac = ScannerFactory.instance(context);
        char[] buffer = (source + EOF_COMMENT).toCharArray();
        Scanner scanner = new AccessibleScanner(fac, new CommentSavingTokenizer(fac, buffer, buffer.length));
        var tokens = new ArrayList<RawTok>();
        int end = source.length();
        int last = 0;
        do {
            scanner.nextToken();
            Token t = scanner.token();
            if (t.comments != null) {
                // javac stores the comments in reverse declaration order
                for (Comment comment : Lists.reverse(t.comments)) {
                    if (!(comment instanceof CommentWithPosition c)) {
                        throw new IllegalStateException(
                                "A bug in JavacTokens: CommentSavingTokenizer makes every comment, but the tokenizer"
                                        + " gave back a " + comment.getClass().getName());
                    }
                    if (last < c.pos()) {
                        tokens.add(new RawTok(null, last, c.pos()));
                    }
                    tokens.add(new RawTok(null, c.pos(), c.endPos()));
                    last = c.endPos();
                }
            }
            if (stopTokens.contains(t.kind)) {
                if (t.kind != TokenKind.EOF) {
                    end = t.pos;
                }
                break;
            }
            if (last < t.pos) {
                tokens.add(new RawTok(null, last, t.pos));
            }
            tokens.add(new RawTok(t.kind, t.pos, t.endPos));
            last = t.endPos;
        } while (scanner.token().kind != TokenKind.EOF);
        if (last < end) {
            tokens.add(new RawTok(null, last, end));
        }
        return List.copyOf(tokens);
    }

    /** A {@link JavaTokenizer} that records where each comment starts and ends. */
    static class CommentSavingTokenizer extends JavaTokenizer {
        private final char[] buffer;

        CommentSavingTokenizer(ScannerFactory fac, char[] buffer, int length) {
            super(fac, buffer, length);
            this.buffer = buffer;
        }

        @Override
        protected Comment processComment(int pos, int endPos, CommentStyle style) {
            return new CommentWithPosition(buffer, pos, endPos, style);
        }
    }

    /**
     * A {@link Comment} that records its start and end positions. Its text stays in the lexer's buffer until
     * {@link #getText} copies it out.
     */
    static class CommentWithPosition implements Comment {

        private final char[] buffer;
        private final int pos;
        private final int endPos;
        private final CommentStyle style;

        CommentWithPosition(char[] buffer, int pos, int endPos, CommentStyle style) {
            this.buffer = buffer;
            this.pos = pos;
            this.endPos = endPos;
            this.style = style;
        }

        /** The start position. */
        int pos() {
            return pos;
        }

        /** The end position. */
        int endPos() {
            return endPos;
        }

        /**
         * Returns the source position of the character at index {@code index} in the comment text.
         *
         * <p>The handling of javadoc comments in javac has more logic to skip over leading whitespace and '*'
         * characters when indexing into doc comments, but we don't need any of that.
         */
        @Override
        public int getSourcePos(int index) {
            checkArgument(
                    0 <= index && index < (endPos - pos), "Expected %s in the range [0, %s)", index, endPos - pos);
            return pos + index;
        }

        @Override
        public CommentStyle getStyle() {
            return style;
        }

        /** The comment as it is in the source, unicode escapes untranslated. */
        @Override
        public String getText() {
            return new String(buffer, pos, endPos - pos);
        }

        /**
         * We don't care about {@code @deprecated} javadoc tags (see the DepAnn check).
         *
         * @return false
         */
        @Override
        public boolean isDeprecated() {
            return false;
        }

        @Override
        public String toString() {
            return String.format("Comment: '%s'", getText());
        }
    }

    // Scanner(ScannerFactory, JavaTokenizer) is package-private
    static class AccessibleScanner extends Scanner {
        protected AccessibleScanner(ScannerFactory fac, JavaTokenizer tokenizer) {
            super(fac, tokenizer);
        }
    }
}
