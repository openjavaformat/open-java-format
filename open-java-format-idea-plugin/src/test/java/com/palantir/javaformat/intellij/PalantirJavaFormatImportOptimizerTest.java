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
import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.formatting.service.CoreFormattingService;
import com.intellij.formatting.service.FormattingService;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.palantir.javaformat.intellij.PalantirJavaFormatSettings.StoredSettings;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PalantirJavaFormatImportOptimizerTest {
    private static final String UNOPTIMIZED = """
            package com.foo;

            import java.util.Map;
            import static java.util.Objects.requireNonNull;
            import java.util.List;
            import java.util.ArrayList;

            class ImportTest {
              Map<String,   String> map;
                List<String> list = requireNonNull(null);
            }
            """;

    private JavaCodeInsightTestFixture fixture;
    private PalantirJavaFormatSettings settings;
    private DelegatingFormatter delegatingFormatter;

    @BeforeEach
    void setUp() throws Exception {
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createLightFixtureBuilder(
                        new DefaultLightProjectDescriptor(), getClass().getName());
        fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        fixture.setUp();

        // The IDE's own service stays, after ours, for the IDE's own optimizer when the plugin is disabled.
        delegatingFormatter = new DelegatingFormatter();
        ExtensionTestUtil.maskExtensions(
                FormattingService.EP_NAME,
                List.of(delegatingFormatter, new CoreFormattingService()),
                fixture.getProjectDisposable());

        settings = PalantirJavaFormatSettings.getInstance(fixture.getProject());
        var resetState = new StoredSettings();
        resetState.setEnabled("true");
        settings.loadState(resetState);
    }

    @AfterEach
    void tearDown() throws Exception {
        fixture.tearDown();
    }

    @Test
    void optimize_imports_removes_the_unused_ones_and_sorts_the_others_as_the_command_line_does() {
        PsiFile file = createPsiFile(UNOPTIMIZED);

        run(new OptimizeImportsProcessor(file.getProject(), file));

        assertThat(file.getText()).isEqualTo("""
                package com.foo;

                import static java.util.Objects.requireNonNull;

                import java.util.List;
                import java.util.Map;

                class ImportTest {
                  Map<String,   String> map;
                    List<String> list = requireNonNull(null);
                }
                """);
        assertThat(delegatingFormatter.optimizerInvoked).isTrue();
    }

    @Test
    void reformat_with_optimize_imports_gives_what_the_command_line_gives() throws Exception {
        PsiFile file = createPsiFile(UNOPTIMIZED);

        run(new OptimizeImportsProcessor(new ReformatCodeProcessor(file, false)));

        assertThat(file.getText()).isEqualTo(commandLineOutput(UNOPTIMIZED));
    }

    @Test
    void optimize_imports_hands_the_imports_over_to_a_format_still_running_on_the_document() throws Exception {
        PsiFile file = createPsiFile(UNOPTIMIZED);
        var request = new RecordingRequest(file);
        Runnable task = startFormatting(request);

        // The platform starts the optimizer while the formatter runs in the background: if it wrote the imports now,
        // the platform would drop the formatted text, the document having changed since the request started.
        run(new OptimizeImportsProcessor(file.getProject(), file));
        assertThat(file.getText()).isEqualTo(UNOPTIMIZED);

        task.run();
        assertThat(request.text).isEqualTo(commandLineOutput(UNOPTIMIZED));
    }

    @Test
    void optimize_imports_writes_nothing_while_a_format_has_text_not_yet_applied() throws Exception {
        PsiFile file = createPsiFile(UNOPTIMIZED);
        var request = new RecordingRequest(file);
        Runnable task = startFormatting(request);
        task.run();

        run(new OptimizeImportsProcessor(file.getProject(), file));
        assertThat(file.getText()).isEqualTo(UNOPTIMIZED);

        // Once the platform applies the formatted text, optimizing imports works on that.
        WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
            var documentManager = PsiDocumentManager.getInstance(fixture.getProject());
            requireNonNull(documentManager.getDocument(file)).setText(requireNonNull(request.text));
            documentManager.commitAllDocuments();
        });
        run(new OptimizeImportsProcessor(file.getProject(), file));
        assertThat(file.getText()).isEqualTo(commandLineOutput(UNOPTIMIZED));
    }

    @Test
    void optimize_imports_is_the_ides_own_when_the_plugin_is_disabled() {
        settings.setEnabled(false);
        PsiFile file = createPsiFile(UNOPTIMIZED);

        run(new OptimizeImportsProcessor(file.getProject(), file));

        assertThat(delegatingFormatter.optimizerInvoked).isFalse();
    }

    @Test
    void optimize_imports_in_an_untrusted_project_changes_nothing_and_says_why_in_the_hint() {
        PsiFile file = createPsiFile(UNOPTIMIZED);
        TrustedProjects.setTrusted(fixture.getProject(), false);
        try {
            var processor = new OptimizeImportsProcessor(file.getProject(), file);
            processor.setCollectInfo(true);

            run(processor);

            assertThat(file.getText()).isEqualTo(UNOPTIMIZED);
            assertThat(requireNonNull(processor.getInfoCollector()).getOptimizeImportsNotification())
                    .isEqualTo(Notifications.untrustedProjectMessage());
        } finally {
            // The light project outlives the test.
            TrustedProjects.setTrusted(fixture.getProject(), true);
        }
    }

    private String commandLineOutput(String input) throws Exception {
        return delegatingFormatter
                .getFormatter(fixture.getProject())
                .orElseThrow()
                .formatSourceReflowStringsAndFixImports(input);
    }

    /** Starts a formatting request on the document as the platform does, without running it yet. */
    private Runnable startFormatting(AsyncFormattingRequest request) {
        return WriteCommandAction.writeCommandAction(fixture.getProject())
                .compute(() -> (Runnable) delegatingFormatter.createFormattingTask(request));
    }

    private void run(OptimizeImportsProcessor processor) {
        WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
            processor.run();
            PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
        });
    }

    private PsiFile createPsiFile(String contents) {
        PsiFile psiFile = fixture.configureByText("ImportTest.java", contents);
        assertThat(psiFile).isNotNull();
        return psiFile;
    }

    /** A request as the platform makes it for a Reformat of the whole file, which records the text it is given. */
    private static final class RecordingRequest implements AsyncFormattingRequest {
        private final PsiFile file;
        private final String documentText;

        @Nullable
        private String text;

        RecordingRequest(PsiFile file) {
            this.file = file;
            this.documentText = file.getText();
        }

        @Override
        public String getDocumentText() {
            return documentText;
        }

        @Override
        @Nullable
        public File getIOFile() {
            return null;
        }

        @Override
        public List<TextRange> getFormattingRanges() {
            return List.of(file.getTextRange());
        }

        @Override
        public boolean canChangeWhitespaceOnly() {
            return false;
        }

        @Override
        public boolean isQuickFormat() {
            return false;
        }

        @Override
        public FormattingContext getContext() {
            return FormattingContext.create(file, CodeStyle.getSettings(file));
        }

        @Override
        public void onTextReady(@Nullable String updatedText) {
            text = updatedText;
        }

        @Override
        public void onError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }

        @Override
        public void onError(String title, String message, int offset) {
            onError(title, message);
        }
    }

    private static final class DelegatingFormatter extends PalantirJavaFormatFormattingService {
        private boolean optimizerInvoked = false;

        DelegatingFormatter() {
            this.setPluginDescriptor(
                    requireNonNull(FormattingService.EP_NAME.findExtension(PalantirJavaFormatFormattingService.class))
                            .getPluginDescriptor()
                            .orElseThrow());
        }

        @Override
        public Set<ImportOptimizer> getImportOptimizers(PsiFile file) {
            var delegate = super.getImportOptimizers(file).iterator().next();
            return Set.of(new ImportOptimizer() {
                @Override
                public boolean supports(PsiFile file) {
                    return delegate.supports(file);
                }

                @Override
                public Runnable processFile(PsiFile file) {
                    optimizerInvoked = true;
                    return delegate.processFile(file);
                }
            });
        }

        @Override
        protected FormattingTask createFormattingTask(AsyncFormattingRequest request) {
            return requireNonNull(super.createFormattingTask(request));
        }
    }
}
