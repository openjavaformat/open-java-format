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

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.nio.file.Path;

/** Where the classes of the tests come from. */
final class TestClassPath {

    private TestClassPath() {}

    /**
     * The jar or the directory a class was loaded from. Asked of the class file rather than of the class's code
     * source, which the class loader of the IDE's tests leaves empty.
     */
    static Path entryOf(Class<?> type) {
        var classFile = type.getName().replace('.', '/') + ".class";
        var url = requireNonNull(type.getClassLoader().getResource(classFile)).toString();
        return Path.of(URI.create(
                url.startsWith("jar:")
                        ? url.substring("jar:".length(), url.indexOf("!/"))
                        : url.substring(0, url.length() - classFile.length())));
    }
}
