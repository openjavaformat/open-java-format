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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.javaformat.java.FormatterService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class FormatterVersionsTest {

    @TempDir
    private Path dir;

    private final FormatterVersions versions = new FormatterVersions();

    @Test
    void readsTheVersionOfTheJarThatProvidesTheFormatter() throws Exception {
        // Every jar of a build's class path has a version of its own.
        var classPath = List.of(jar("guava.jar", "33.4.0", false), jar("open-java-format.jar", "21.1.0", true));

        assertThat(versions.of(classPath)).hasValue("21.1.0");
    }

    @Test
    void readsTheJarsOnceForAsLongAsTheyStayTheSame() throws Exception {
        var formatter = jar("open-java-format.jar", "21.1.0", true);
        assertThat(versions.of(List.of(formatter))).hasValue("21.1.0");

        // No jar at all now, of the same size and time: not opened again, it still has the version read before.
        var lastModified = Files.getLastModifiedTime(formatter);
        Files.write(formatter, new byte[(int) Files.size(formatter)]);
        Files.setLastModifiedTime(formatter, lastModified);

        assertThat(versions.of(List.of(formatter))).hasValue("21.1.0");

        // A jar rebuilt in place.
        jar("open-java-format.jar", "21.2.0", true);
        Files.setLastModifiedTime(
                formatter, FileTime.from(lastModified.toInstant().plusSeconds(60)));

        assertThat(versions.of(List.of(formatter))).hasValue("21.2.0");
    }

    @Test
    @EnabledOnOs(value = OS.LINUX, disabledReason = "Only Linux names the files a process holds open, in /proc/self/fd")
    void leavesNoJarOpen() throws Exception {
        // On Windows a jar the IDE holds open is one that nobody can replace or delete, a Gradle build included.
        var dependency = jar("guava.jar", "33.4.0", false);
        var formatter = jar("open-java-format.jar", "21.1.0", true);

        versions.of(List.of(dependency, formatter));

        assertThat(openFiles()).doesNotContain(dependency.toRealPath(), formatter.toRealPath());
    }

    @Test
    void takesAFormatterWithoutAVersionForNone() throws Exception {
        // As a version it cannot parse: the plugin formats with the formatter bundled with it.
        assertThat(versions.of(List.of(jar("open-java-format.jar", null, true))))
                .isEmpty();
    }

    @Test
    void saysSoWhenNoJarProvidesTheFormatter() throws Exception {
        var classPath = List.of(jar("guava.jar", "33.4.0", false));

        assertThatThrownBy(() -> versions.of(classPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No jar provides the formatter in %s", classPath);
    }

    /** A jar in the test's directory, written over the one there, with the formatter in it if it provides it. */
    private Path jar(String name, @Nullable String version, boolean providesTheFormatter) throws IOException {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (version != null) {
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        }
        var path = dir.resolve(name);
        try (var jar = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            if (providesTheFormatter) {
                jar.putNextEntry(new JarEntry("META-INF/services/" + FormatterService.class.getName()));
                jar.write("com.palantir.javaformat.java.FormatterServiceImpl\n".getBytes(UTF_8));
                jar.closeEntry();
            }
        }
        return path;
    }

    private static List<Path> openFiles() throws IOException {
        var open = new ArrayList<Path>();
        try (var descriptors = Files.list(Path.of("/proc/self/fd"))) {
            for (var descriptor : descriptors.toList()) {
                try {
                    open.add(Files.readSymbolicLink(descriptor));
                } catch (IOException e) {
                    // Closed since it was listed, as the listing's own is.
                }
            }
        }
        return open;
    }
}
