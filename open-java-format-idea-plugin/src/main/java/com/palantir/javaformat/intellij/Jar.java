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

package com.palantir.javaformat.intellij;

import java.nio.file.Path;

/**
 * A jar as it was when something was read from it: rebuilt in place, it changes size or time. A key for what is kept
 * of a set of jars for as long as they stay the same.
 */
record Jar(Path path, long length, long lastModified) {
    static Jar of(Path path) {
        var file = path.toFile();
        return new Jar(path, file.length(), file.lastModified());
    }
}
