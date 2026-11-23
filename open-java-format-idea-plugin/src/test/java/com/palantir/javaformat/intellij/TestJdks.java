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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** JDKs for a test to give the IDE, which lose them again when the test ends. */
final class TestJdks {

    private TestJdks() {}

    /** The JDK the tests run on, under a name of the test's own. */
    static Sdk running(String name, Disposable untilDisposed) {
        return register(
                name, Path.of(System.getProperty("java.home")), System.getProperty("java.version"), untilDisposed);
    }

    /** A JDK as the IDE lists it, which need not be on disk: versionString is what the IDE read from it. */
    static Sdk register(String name, Path home, String versionString, Disposable untilDisposed) {
        return WriteAction.computeAndWait(() -> {
            var sdk = ProjectJdkTable.getInstance().createSdk(name, JavaSdk.getInstance());
            var modificator = sdk.getSdkModificator();
            modificator.setHomePath(home.toString());
            modificator.setVersionString(versionString);
            modificator.commitChanges();
            ProjectJdkTable.getInstance().addJdk(sdk, untilDisposed);
            return sdk;
        });
    }

    /** Light projects outlive a test, so the SDK the project had comes back with the test's end. */
    static void setProjectSdk(Project project, @Nullable Sdk sdk, Disposable untilDisposed) {
        var roots = ProjectRootManager.getInstance(project);
        var before = roots.getProjectSdk();
        WriteAction.runAndWait(() -> roots.setProjectSdk(sdk));
        Disposer.register(untilDisposed, () -> WriteAction.runAndWait(() -> roots.setProjectSdk(before)));
    }
}
