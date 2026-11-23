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

import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** How a project asks for the formatter to run. */
enum FormatterMode {
    /** Loaded into the IDE's own JVM: nothing to start for a file. */
    IN_PROCESS("in-process"),
    /** In a JVM started for each call, on the JDK the settings name. */
    JVM("jvm"),
    /** As the native binary, started for each call. */
    NATIVE("native");

    private final String text;

    FormatterMode(String text) {
        this.text = text;
    }

    /** What .idea/open-java-format.xml holds for the mode, whatever the constant is called here. */
    String text() {
        return text;
    }

    /** The mode a file names: none for a file that names none, or one that only a later plugin has. */
    static Optional<FormatterMode> of(@Nullable String text) {
        return Arrays.stream(values()).filter(mode -> mode.text.equals(text)).findFirst();
    }
}
