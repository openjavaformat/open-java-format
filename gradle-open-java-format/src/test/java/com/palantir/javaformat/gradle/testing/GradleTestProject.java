/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.javaformat.gradle.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

/**
 * A generated Gradle project driven through plain Gradle TestKit.
 *
 * <p>Replaces {@code com.palantir.gradle-plugin-testing}, which could not run under Gradle 9: it is built against
 * gradle-consistent-versions and dragged that plugin onto the build's classpath purely to be instantiable.
 *
 * <p>The plugins block is kept apart from the rest of the script because it has to be rendered first, while tests add
 * to both in whatever order reads best; nothing is written to disk until a build is actually run.
 */
public final class GradleTestProject {

    /**
     * The oldest Gradle 9 we claim to support — the interesting end of the range to test against. There is no version
     * matrix any more: Gradle 8 was dropped when the build itself moved to 9.
     */
    private static final String GRADLE_VERSION = "9.3.1";

    private final Path projectDir;
    private final Set<String> plugins = new LinkedHashSet<>();
    private final StringBuilder buildGradle = new StringBuilder();
    private final StringBuilder gradleProperties = new StringBuilder();
    private boolean configurationCache;

    public GradleTestProject(Path projectDir) {
        this.projectDir = projectDir;
    }

    public Path path() {
        return projectDir;
    }

    public GradleTestProject plugins(String... ids) {
        plugins.addAll(Arrays.asList(ids));
        return this;
    }

    /** Appends to build.gradle, below the plugins block. {@code %s} placeholders are substituted as in printf. */
    public GradleTestProject buildGradle(String format, Object... args) {
        buildGradle.append(args.length == 0 ? format : String.format(format, args)).append('\n');
        return this;
    }

    public GradleTestProject gradleProperties(String line) {
        if (!line.isBlank()) {
            gradleProperties.append(line).append('\n');
        }
        return this;
    }

    /**
     * The exports the formatter needs to reach javac internals. Every generated build that actually runs the formatter
     * needs these, exactly as the real one does.
     */
    public GradleTestProject withJavacInternalExports() {
        return gradleProperties("org.gradle.jvmargs=--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED "
                + "--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED "
                + "--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED "
                + "--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED "
                + "--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
    }

    /** Builds run with the configuration cache off unless a test opts in, which is what the plugin's own tests need. */
    public GradleTestProject withConfigurationCache() {
        this.configurationCache = true;
        return this;
    }

    public Path file(String relativePath) {
        return projectDir.resolve(relativePath);
    }

    public GradleTestProject writeFile(String relativePath, String content) {
        Path target = file(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + relativePath, e);
        }
        return this;
    }

    public String readFile(String relativePath) {
        try {
            return Files.readString(file(relativePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + relativePath, e);
        }
    }

    public BuildResult succeeds(String... arguments) {
        return runner(arguments).build();
    }

    public BuildResult fails(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private GradleRunner runner(String... arguments) {
        write();
        List<String> allArguments = new ArrayList<>(Arrays.asList(arguments));
        allArguments.add("--stacktrace");
        allArguments.add(configurationCache ? "--configuration-cache" : "--no-configuration-cache");
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withGradleVersion(GRADLE_VERSION)
                .withArguments(allArguments)
                .forwardOutput();
    }

    private void write() {
        StringBuilder script = new StringBuilder("plugins {\n");
        plugins.forEach(id -> script.append("    id '").append(id).append("'\n"));
        script.append("}\n\nrepositories {\n    mavenCentral()\n}\n\n").append(buildGradle);

        writeFile("settings.gradle", "rootProject.name = 'test-project'\n");
        writeFile("build.gradle", script.toString());
        writeFile("gradle.properties", gradleProperties.toString());
    }
}
