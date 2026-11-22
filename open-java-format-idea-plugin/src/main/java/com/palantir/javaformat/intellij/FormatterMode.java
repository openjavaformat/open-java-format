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

/** How a project asks for the formatter to run. The names are what .idea/open-java-format.xml holds. */
enum FormatterMode {
    /** Loaded into the IDE's own JVM: nothing to start for a file. */
    IN_PROCESS,
    /** In a JVM started for each call, on the JDK the settings name. */
    JVM,
    /** As the native binary, started for each call. */
    NATIVE
}
