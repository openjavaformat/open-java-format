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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    /** Runs a command in a directory and returns what it printed, trimmed. */
    private static String run(Path dir, Duration timeout, String... command) throws IOException, InterruptedException {
        var process =
                new ProcessBuilder().command(command).directory(dir.toFile()).start();

        checkState(process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS), "git diff took too long to terminate");
        if (process.exitValue() != 0) {
            throw new RuntimeException("Expected return code of 0 but got " + process.exitValue()
                    + "\n stdout: " + new String(process.getInputStream().readAllBytes(), UTF_8)
                    + "\n stderr: " + new String(process.getErrorStream().readAllBytes(), UTF_8));
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        process.getInputStream().transferTo(baos);
        return new String(baos.toByteArray(), UTF_8).trim();
    }
}
