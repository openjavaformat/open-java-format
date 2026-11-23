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

package com.palantir.javaformat.gradle;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * What formatDiff formats, as git reports it: the output of {@code git diff -U0 HEAD} for a directory, and the top of
 * the work tree that the paths in it are relative to. {@link FormatDiff#formatChangedLines} takes one built by hand,
 * so that formatting can be tested without running git.
 */
record GitDiff(Path topLevelDir, String output) {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    static GitDiff of(Path dir) throws IOException, InterruptedException {
        // TODO(dfox): this does nothing if working dir is clean - maybe use HEAD^ to format prev commit?
        var output = run(
                dir, TIMEOUT, "git", "diff", "-U0", "HEAD", dir.toAbsolutePath().toString());
        var topLevelDir = Paths.get(run(dir, TIMEOUT, "git", "rev-parse", "--show-toplevel"));
        return new GitDiff(topLevelDir, output);
    }

    /**
     * Runs a command in a directory and returns what it printed, trimmed. Both of its streams go to files: a pipe
     * holds 64 KB on Linux and macOS, and a command that fills one waits for someone to read it, while this waits for
     * the command to exit (#257).
     */
    @VisibleForTesting
    static String run(Path dir, Duration timeout, String... command) throws IOException, InterruptedException {
        var stdout = Files.createTempFile("open-java-format-", ".stdout");
        var stderr = Files.createTempFile("open-java-format-", ".stderr");
        try {
            var process = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("%s did not finish within %d seconds"
                        .formatted(String.join(" ", command), timeout.toSeconds()));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("%s exited with %d\n stdout: %s\n stderr: %s"
                        .formatted(String.join(" ", command), process.exitValue(), read(stdout), read(stderr)));
            }
            return read(stdout).trim();
        } finally {
            delete(stdout);
            delete(stderr);
        }
    }

    // Not Files.readString, which throws on a byte that is not UTF-8: a diff holds the bytes of the files it is of.
    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), UTF_8);
    }

    // A command killed on Windows can leave a child behind with the file open, as Git for Windows' git.exe, a launcher,
    // does; failing to delete the file then would hide why the command was killed.
    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            file.toFile().deleteOnExit();
        }
    }
}
