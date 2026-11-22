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
import org.jspecify.annotations.Nullable;

/** The JDK a project names for the formatter's own JVM. {@link FormatterJdks} finds the java it stands for. */
sealed interface FormatterJdk {

    /** What a JDK comes to on this machine, in this project. */
    sealed interface Resolved {}

    /** java: the executable. javaVersion: the version of Java it runs, such as 21. */
    record Usable(Path java, int javaVersion) implements Resolved {}

    /** reason: a clause that says why, such as "the project has no SDK". */
    record Unusable(String reason) implements Resolved {}

    /** The runtime the IDE itself runs on: always there, and always recent enough for the formatter. */
    record IdeRuntime() implements FormatterJdk {
        @Override
        public @Nullable String stored() {
            return null;
        }
    }

    /** Whichever SDK the project has, as it changes. */
    record ProjectSdk() implements FormatterJdk {
        @Override
        public String stored() {
            return PROJECT_SDK;
        }
    }

    /** A JDK registered in the IDE, by the name it has there. */
    record Named(String name) implements FormatterJdk {
        @Override
        public String stored() {
            return name;
        }
    }

    // Not a name a JDK can have by accident: the IDE's own settings mark their special values with '#' too.
    String PROJECT_SDK = "#PROJECT_SDK";

    /** What .idea/open-java-format.xml holds for this JDK; nothing for the IDE's runtime, which is the default. */
    @Nullable
    String stored();

    static FormatterJdk parse(@Nullable String stored) {
        if (stored == null || stored.isBlank()) {
            return new IdeRuntime();
        }
        return stored.equals(PROJECT_SDK) ? new ProjectSdk() : new Named(stored);
    }
}
