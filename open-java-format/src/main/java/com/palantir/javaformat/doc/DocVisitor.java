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

package com.palantir.javaformat.doc;

public interface DocVisitor<T> {
    default T visit(Doc doc) {
        if (doc instanceof Level level) {
            return visitLevel(level);
        } else if (doc instanceof Break b) {
            return visitBreak(b);
        } else if (doc instanceof Token token) {
            return visitToken(token);
        } else if (doc instanceof Comment comment) {
            return visitComment(comment);
        } else if (doc instanceof NonBreakingSpace space) {
            return visitSpace(space);
        }
        throw new RuntimeException();
    }

    T visitSpace(NonBreakingSpace doc);

    T visitComment(Comment doc);

    T visitToken(Token doc);

    T visitBreak(Break doc);

    T visitLevel(Level doc);
}
