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
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.palantir.javaformat.intellij.PalantirJavaFormatFormattingService.PendingFormat;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.FormatterService;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

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

    // The fixImports calls still running, by formatter and text. A read action that runs again after a write action,
    // on the same text, waits for the call it started before instead of starting another: a JVM, in that mode.
    private final Map<FixImportsCall, CompletableFuture<FixedImports>> running = new ConcurrentHashMap<>();

    // The calls of processFile that wait for fixImports inside their read action, which a write action waits for.
    private final AtomicInteger waiting = new AtomicInteger();
    private final AtomicBoolean watchingWriteActions = new AtomicBoolean();

    PalantirJavaFormatImportOptimizer(PalantirJavaFormatFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    @Override
    public boolean supports(PsiFile file) {
        return formattingService.canFormat(file);
    }

    // Called under a read action, in the background on most paths; the Runnable runs on the EDT under a write action.
    // So the formatter runs here, waited for in a way that a write action can interrupt, and the Runnable only writes
    // its text. EmptyRunnable tells the IDE that nothing changed.
    @Override
    public Runnable processFile(PsiFile file) {
        Project project = file.getProject();
        debug(file, "asked on thread " + Thread.currentThread().getName());
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
        var start = System.nanoTime();
        try {
            fixedText = fixImports(file, formatter.get(), text);
            if (logger.isDebugEnabled()) {
                debug(
                        file,
                        String.format(
                                "waited %s ms for fixImports, length=%s, text changed: %s",
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
            if (logger.isDebugEnabled()) {
                debug(
                        file,
                        String.format(
                                "cancelled after waiting %s ms for fixImports, for a write action or by the user",
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
            }
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
     * The formatter's {@code fixImports}, on a pooled thread, so that a write action does not wait for it. The read
     * action this runs in gives way to a write action only when it checks for cancellation, which the formatter never
     * does: with "Optimize imports on the fly", every keystroke would wait for it (#253), in IntelliJ IDEA 2024.2 to
     * 2025.1; from 2025.2 on the fly runs the IDE's own optimizer, not this one. Cancelled, this throws
     * {@link ProcessCanceledException}, and a non-blocking read action, as on the fly, runs again after the write. The
     * call already started runs to its end, for whoever asks for the same text meanwhile.
     */
    private String fixImports(PsiFile file, FormatterService formatter, String text) throws FormatterException {
        var call = new FixImportsCall(formatter, text);
        var started = new AtomicBoolean();
        var fixing = running.computeIfAbsent(call, key -> {
            started.set(true);
            return CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return key.run();
                        } finally {
                            // Kept only while it runs. Should it end first, the map holds this back until
                            // computeIfAbsent has put it in.
                            running.remove(key);
                        }
                    },
                    AppExecutorUtil.getAppExecutorService());
        });
        debug(file, started.get() ? "started fixImports" : "waits for the fixImports already running on the same text");
        watchWriteActions();
        waiting.incrementAndGet();
        try {
            return switch (ProgressIndicatorUtils.awaitWithCheckCanceled(fixing)) {
                case FixedImports.Fixed fixed -> fixed.text();
                case FixedImports.Failed failed -> throw failed.exception();
            };
        } finally {
            waiting.decrementAndGet();
        }
    }

    private record FixImportsCall(FormatterService formatter, String text) {
        FixedImports run() {
            try {
                return new FixedImports.Fixed(formatter.fixImports(text));
            } catch (FormatterException e) {
                return new FixedImports.Failed(e);
            }
        }
    }

    /**
     * What {@code fixImports} gives back from its thread: awaitWithCheckCanceled would wrap a {@link FormatterException}
     * thrown there in a RuntimeException, which the optimizer takes for a fault of the plugin.
     */
    private sealed interface FixedImports {
        record Fixed(String text) implements FixedImports {}

        record Failed(FormatterException exception) implements FixedImports {}
    }

    /** With debug logging on, logs how long each write action waits for the read lock while imports are optimized. */
    private void watchWriteActions() {
        if (logger.isDebugEnabled() && watchingWriteActions.compareAndSet(false, true)) {
            var application = ApplicationManager.getApplication();
            // For as long as the IDE runs: the plugin needs a restart to unload.
            application.addApplicationListener(
                    new WriteActionWaits(waiting::get, wait -> {
                        if (logger.isDebugEnabled()) {
                            logger.debug(String.format(
                                    "Optimizing imports: a write action waited %s ms for the read lock",
                                    TimeUnit.NANOSECONDS.toMillis(wait)));
                        }
                    }),
                    application);
        }
    }

    /**
     * Measures how long a write action waits for the read lock while an import optimization waits for fixImports:
     * whether typing waits for the formatter (#253). Other read actions may hold the lock too, and their time counts
     * as well.
     */
    static final class WriteActionWaits implements ApplicationListener {
        private final IntSupplier waiting;
        private final LongConsumer report;

        // From beforeWriteActionStart to writeActionStarted, on the thread of the write action: one at a time.
        private boolean measuring;
        private long requested;

        /** waiting: how many optimizations wait for fixImports. report: given the wait in nanoseconds. */
        WriteActionWaits(IntSupplier waiting, LongConsumer report) {
            this.waiting = waiting;
            this.report = report;
        }

        @Override
        public void beforeWriteActionStart(Object action) {
            // A write action inside another holds the lock already.
            measuring = waiting.getAsInt() > 0
                    && !ApplicationManager.getApplication().isWriteAccessAllowed();
            requested = System.nanoTime();
        }

        @Override
        public void writeActionStarted(Object action) {
            if (measuring) {
                measuring = false;
                report.accept(System.nanoTime() - requested);
            }
        }
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
