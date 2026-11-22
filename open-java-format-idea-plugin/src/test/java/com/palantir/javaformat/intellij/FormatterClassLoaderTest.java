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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.java.FormatterService;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FormatterClassLoaderTest {

    @Test
    void takesEveryClassOfTheSpiFromThePlugin() throws Exception {
        // The formatter's jars have the SPI too. A class of it that the formatter loaded for itself would not be the
        // one the plugin calls, and the SPI shares its package with the formatter, so its classes are listed by name.
        assertThat(spiClasses()).isNotEmpty().allMatch(FormatterClassLoader::isShared);
    }

    @Test
    void leavesTheFormattersOwnClassesToTheFormatter() {
        assertThat(FormatterClassLoader.isShared("com.palantir.javaformat.java.FormatterServiceImpl"))
                .isFalse();
        assertThat(FormatterClassLoader.isShared("com.palantir.javaformat.java.Formatter"))
                .isFalse();
        assertThat(FormatterClassLoader.isShared("com.fasterxml.jackson.databind.ObjectMapper"))
                .isFalse();
    }

    @Test
    void takesGuavaFromThePlugin() {
        // The SPI's signatures name ImmutableList and Range.
        assertThat(FormatterClassLoader.isShared("com.google.common.collect.ImmutableList"))
                .isTrue();
        assertThat(FormatterClassLoader.isShared("com.google.common.collect.Range$RangeLexOrdering"))
                .isTrue();
    }

    /** The names of the classes in the jar, or the directory, that FormatterService comes from. */
    private static List<String> spiClasses() throws Exception {
        var spi = TestClassPath.entryOf(FormatterService.class);
        if (Files.isDirectory(spi)) {
            return classesUnder(spi);
        }
        try (var jar = FileSystems.newFileSystem(spi)) {
            return classesUnder(jar.getPath("/"));
        }
    }

    private static List<String> classesUnder(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.map(file -> root.relativize(file).toString())
                    .filter(file -> file.endsWith(".class") && !file.endsWith("module-info.class"))
                    .map(file -> file.substring(0, file.length() - ".class".length())
                            .replace(root.getFileSystem().getSeparator(), "."))
                    .toList();
        }
    }
}
