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

import com.intellij.formatting.service.FormattingNotificationService;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.ImportOptimizer.CollectingInfoRunnable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.palantir.javaformat.intellij.PalantirJavaFormatFormattingService.PendingFormat;
import com.palantir.javaformat.java.FormatterException;
import java.util.concurrent.TimeUnit;

/**
 * Optimizes imports as the command line fixes them, with {@code FormatterService.fixImports}: the unused ones removed,
 * the others sorted. The IDE takes it instead of its own {@code JavaImportOptimizer} for every way of optimizing
 * imports, since {@link PalantirJavaFormatFormattingService} has {@code OPTIMIZE_IMPORTS}.
 */
final class PalantirJavaFormatImportOptimizer implements ImportOptimizer {
    private final Logger logger = Logger.getInstance(PalantirJavaFormatImportOptimizer.class);

    // Changes nothing. An explicit Optimize Imports shows the message in its hint, instead of "No unused imports
    // found"; on the fly nothing shows it.
    private static final CollectingInfoRunnable UNTRUSTED_PROJECT = new CollectingInfoRunnable() {
        @Override
        public void run() {}

        @Override
        public String getUserNotificationInfo() {
            return Notifications.untrustedProjectMessage();
        }
    };

    private final PalantirJavaFormatFormattingService formattingService;

    PalantirJavaFormatImportOptimizer(PalantirJavaFormatFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    @Override
    public boolean supports(PsiFile file) {
        return formattingService.canFormat(file);
    }

    // Called under a read action, in the background on most paths; the Runnable runs on the EDT under a write action.
    // So the formatter runs here, and the Runnable only writes its text. EmptyRunnable tells the IDE that nothing
    // changed. The formatter does not check for cancellation, so a write action waits for it: with "Optimize imports
    // on the fly", typing waits (#253).
    @Override
    public Runnable processFile(PsiFile file) {
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            debug(file, "skipped, the file has no document");
            return EmptyRunnable.getInstance();
        }
        if (leftToPendingFormat(document, file)) {
            return EmptyRunnable.getInstance();
        }
        if (!PalantirJavaFormatFormattingService.isTrusted(project)) {
            // Safe mode is a normal state, and on the fly this runs on every highlighting pass: no warning, no balloon.
            debug(file, "skipped, the project is not trusted");
            return UNTRUSTED_PROJECT;
        }
        var formatter = formattingService.getFormatter(project);
        if (formatter.isEmpty()) {
            reportError(
                    project,
                    Notifications.GENERIC_ERROR_NOTIFICATION_GROUP,
                    "Failed to optimize imports because formatterService is not configured");
            return EmptyRunnable.getInstance();
        }

        String text = document.getText();
        String fixedText;
        try {
            var start = System.nanoTime();
            fixedText = formatter.get().fixImports(text);
            if (logger.isDebugEnabled()) {
                debug(
                        file,
                        String.format(
                                "fixImports ran in %s ms, length=%s, text changed: %s",
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                                text.length(),
                                !fixedText.equals(text)));
            }
        } catch (FormatterException e) {
            // A syntax error in the file, not a fault of the plugin: an error would go to its error reports.
            logger.warn(String.format("Failed to optimize imports of file %s", file.getName()), e);
            reportError(project, Notifications.PARSING_ERROR_TITLE, Notifications.parsingErrorMessage(file.getName()));
            return EmptyRunnable.getInstance();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            // As in the formatting task: an error, so that it goes to the plugin's error reports.
            logger.error("open-java-format could not run the formatter", e);
            reportError(
                    project,
                    Notifications.GENERIC_ERROR_NOTIFICATION_GROUP,
                    Notifications.formatterFailedMessage(file.getName()));
            return EmptyRunnable.getInstance();
        }
        if (fixedText.equals(text)) {
            return EmptyRunnable.getInstance();
        }

        return () -> {
            // The document can have changed since: typed into, or given the text of a formatting request. Or a request
            // can have started formatting it meanwhile, which takes the imports over.
            if (!StringUtil.equals(document.getImmutableCharSequence(), text)) {
                debug(file, "dropped the fixed imports, the document changed meanwhile");
            } else if (!leftToPendingFormat(document, file)) {
                document.setText(fixedText);
                debug(file, "applied the fixed imports");
            }
        };
    }

    /**
     * Whether a formatting request is pending on the document: one that is still formatting fixes the imports itself,
     * and one whose text is not applied yet would lose it if the document changed now.
     */
    private boolean leftToPendingFormat(Document document, PsiFile file) {
        PendingFormat pendingFormat = PendingFormat.of(document);
        if (pendingFormat == null) {
            return false;
        }
        if (pendingFormat.handOverImports()) {
            debug(file, "handed over to the formatting request in progress, which fixes them after formatting");
            return true;
        }
        if (pendingFormat.isUnapplied()) {
            debug(file, "skipped, a formatting request has finished and its text is not applied yet");
            return true;
        }
        return false;
    }

    private void debug(PsiFile file, String message) {
        if (logger.isDebugEnabled()) {
            var virtualFile = file.getVirtualFile();
            logger.debug(String.format(
                    "Optimizing imports of file=%s: %s",
                    virtualFile == null ? file.getName() : virtualFile.getPath(), message));
        }
    }

    private static void reportError(Project project, String title, String message) {
        FormattingNotificationService.getInstance(project)
                .reportError(Notifications.PARSING_ERROR_NOTIFICATION_GROUP, title, message);
    }
}
