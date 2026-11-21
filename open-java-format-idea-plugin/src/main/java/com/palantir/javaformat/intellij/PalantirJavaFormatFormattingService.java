/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.javaformat.intellij;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;

import com.google.common.collect.Range;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.FormatterService;
import com.palantir.javaformat.java.Replacement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

class PalantirJavaFormatFormattingService extends AsyncDocumentFormattingService implements PluginAware {
    private static final Logger logger = Logger.getInstance(PalantirJavaFormatFormattingService.class);
    private final FormatterProvider formatterProvider =
            new FormatterProvider(() -> EmbeddedNativeImage.getInstance().installedPath());
    private final ImportOptimizer importOptimizer = new PalantirJavaFormatImportOptimizer(this);

    // The platform sets this right after creating the service from plugin.xml: the descriptor of the plugin that
    // declared it, which is how the plugin learns its own path and version (see FormatterProvider.getPluginDescriptor).
    // A service created with `new`, as the tests do, has to be given the descriptor itself.
    @Nullable
    private PluginDescriptor pluginDescriptor;

    @Override
    public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
        this.pluginDescriptor = pluginDescriptor;
    }

    Optional<PluginDescriptor> getPluginDescriptor() {
        return ofNullable(pluginDescriptor);
    }

    @Override
    @Nullable
    protected FormattingTask createFormattingTask(AsyncFormattingRequest request) {
        Project project = request.getContext().getProject();
        if (!isTrusted(project)) {
            logger.warn("open-java-format: skipping formatting for untrusted project: " + project.getName());
            request.onError(Notifications.GENERIC_ERROR_NOTIFICATION_GROUP, Notifications.untrustedProjectMessage());
            return null;
        }
        PendingFormat pendingFormat = PendingFormat.start(
                project,
                PsiDocumentManager.getInstance(project)
                        .getDocument(request.getContext().getContainingFile()));
        return new PalantirJavaFormatFormattingTask(request, getFormatter(project), pendingFormat);
    }

    static boolean isTrusted(Project project) {
        // TrustedProjects.isTrusted is @ApiStatus.Experimental, and deprecated since 2025.1. Its replacement,
        // com.intellij.ide.trustedProjects.TrustedProjects.isProjectTrusted, is public only from 2025.1, and this
        // plugin still supports 2024.2: switch to it when sinceBuild reaches 251.
        return TrustedProjects.isTrusted(project);
    }

    Optional<FormatterService> getFormatter(Project project) {
        return formatterProvider.get(project, PalantirJavaFormatSettings.getInstance(project));
    }

    @Override
    protected String getNotificationGroupId() {
        return Notifications.PARSING_ERROR_NOTIFICATION_GROUP;
    }

    @Override
    protected @NlsSafe String getName() {
        return "open-java-format";
    }

    // OPTIMIZE_IMPORTS: every way the IDE optimizes imports asks the first formatting service that has this feature and
    // can format the file for its optimizers (FormattingServiceUtil.findImportsOptimizingService), and so takes ours
    // instead of its own JavaImportOptimizer.
    @Override
    public Set<Feature> getFeatures() {
        return Set.of(Feature.FORMAT_FRAGMENTS, Feature.OPTIMIZE_IMPORTS);
    }

    @Override
    public Set<ImportOptimizer> getImportOptimizers(PsiFile file) {
        return Set.of(importOptimizer);
    }

    @Override
    public boolean canFormat(PsiFile file) {
        return JavaFileType.INSTANCE.equals(file.getFileType())
                && PalantirJavaFormatSettings.getInstance(file.getProject()).isEnabled();
    }

    /**
     * A formatting request of this service whose text is not in its document yet, kept on the document.
     *
     * <p>The formatter runs in the background, and the platform applies its text later, only if the document has not
     * changed since the request started: IntelliJ IDEA registers no {@code DocumentMerger} to do anything else.
     * Reformat with "Optimize imports" starts the formatter and the import optimizer on the same document without
     * waiting for the first, so whichever wrote second would lose: the formatted text, or the imports. While a request
     * is formatting, the optimizer hands its work over to it, and the request fixes the imports of the text it
     * formatted, as the command line does. Once the request has its text, the optimizer leaves the document alone
     * until the text is applied.
     */
    static final class PendingFormat {
        private static final Key<PendingFormat> KEY = Key.create("open-java-format.pendingFormat");

        private enum State {
            FORMATTING,
            FORMATTING_AND_FIXING_IMPORTS,
            // Until the document next changes: with the text, or with an edit that makes the platform drop it.
            UNAPPLIED,
            DONE
        }

        private final AtomicReference<State> state = new AtomicReference<>(State.FORMATTING);
        private final Project project;

        @Nullable
        private final Document document;

        private PendingFormat(Project project, @Nullable Document document) {
            this.project = project;
            this.document = document;
        }

        static PendingFormat start(Project project, @Nullable Document document) {
            var pendingFormat = new PendingFormat(project, document);
            if (document != null) {
                document.putUserData(KEY, pendingFormat);
            }
            return pendingFormat;
        }

        @Nullable
        static PendingFormat of(Document document) {
            return document.getUserData(KEY);
        }

        /** Asks the request to fix the imports too: false if it has finished formatting already. */
        boolean handOverImports() {
            return state.compareAndSet(State.FORMATTING, State.FORMATTING_AND_FIXING_IMPORTS);
        }

        boolean isUnapplied() {
            return state.get() == State.UNAPPLIED;
        }

        /** Ends the formatting, and says whether the imports were handed over meanwhile. */
        private boolean finishFormatting() {
            return state.getAndSet(State.UNAPPLIED) == State.FORMATTING_AND_FIXING_IMPORTS;
        }

        private void finish(boolean changesDocument) {
            if (!changesDocument || document == null) {
                state.set(State.DONE);
                return;
            }
            var untilChanged = Disposer.newDisposable("open-java-format: until the formatted text is applied");
            if (!Disposer.tryRegister(project, untilChanged)) {
                state.set(State.DONE);
                return;
            }
            // Registered before the platform applies the text, which it does only once the task has returned.
            document.addDocumentListener(
                    new DocumentListener() {
                        @Override
                        public void documentChanged(DocumentEvent event) {
                            state.set(State.DONE);
                            Disposer.dispose(untilChanged);
                        }
                    },
                    untilChanged);
        }
    }

    private static final class PalantirJavaFormatFormattingTask implements FormattingTask {
        private final AsyncFormattingRequest request;
        private final Optional<FormatterService> formatterService;
        private final PendingFormat pendingFormat;

        PalantirJavaFormatFormattingTask(
                AsyncFormattingRequest request,
                Optional<FormatterService> formatterService,
                PendingFormat pendingFormat) {
            this.request = request;
            this.formatterService = formatterService;
            this.pendingFormat = pendingFormat;
        }

        @Override
        public void run() {
            var changesDocument = false;
            try {
                changesDocument = format();
            } finally {
                pendingFormat.finish(changesDocument);
            }
        }

        /** Formats, and says whether the text it gave back changes the document. */
        private boolean format() {
            if (formatterService.isEmpty()) {
                request.onError(
                        Notifications.GENERIC_ERROR_NOTIFICATION_GROUP,
                        "Failed to format file because formatterService is not configured");
                return false;
            }

            String preFormatText = request.getDocumentText();

            try {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format(
                            "Received request to format file=%s, length=%s with ranges=%s",
                            ofNullable(request.getIOFile())
                                    .map(file -> file.toPath().toString())
                                    .orElse("null"),
                            preFormatText.length(),
                            request.getFormattingRanges()));
                }

                // Note: I attempted to implement this using getFormatReplacements and elide an update when
                // there were no replacements. There is a bug in the underlying formatter where it _always_
                // returns a change. Thus we must perform a content aware diff / branching.
                String formattedText = applyReplacements(
                        preFormatText, formatterService.get().getFormatReplacements(preFormatText, toRanges(request)));
                if (pendingFormat.finishFormatting()) {
                    formattedText = formatterService.get().fixImports(formattedText);
                }

                // The Javadoc of this API says that you should set it to null when the document is unchanged
                // We should not be trying to format a document that is already formatted
                if (preFormatText.equals(formattedText)) {
                    request.onTextReady(null);
                    return false;
                }
                request.onTextReady(formattedText);
                return true;
            } catch (FormatterException e) {
                // A syntax error in the file, not a fault of the plugin: an error would go to its error reports.
                logger.warn(
                        String.format(
                                "Failed to format file %s",
                                request.getContext().getContainingFile().getName()),
                        e);
                request.onError(
                        Notifications.PARSING_ERROR_TITLE,
                        Notifications.parsingErrorMessage(
                                request.getContext().getContainingFile().getName()));
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (RuntimeException e) {
                // Typically the formatter's JVM failing to start or exiting with an error. Left alone, the IDE
                // swallows it and Reformat looks as if it did nothing. An error rather than a warning, so that it goes
                // to the plugin's error reports on the JetBrains Marketplace (see errorHandler in plugin.xml).
                logger.error("open-java-format could not run the formatter", e);
                request.onError(
                        Notifications.GENERIC_ERROR_NOTIFICATION_GROUP,
                        Notifications.formatterFailedMessage(
                                request.getContext().getContainingFile().getName()));
            }
            return false;
        }

        static String applyReplacements(String input, Collection<Replacement> replacementsCollection) {
            List<Replacement> replacements = new ArrayList<>(replacementsCollection);
            replacements.sort(comparing((Replacement r) -> r.getReplaceRange().lowerEndpoint())
                    .reversed());
            StringBuilder writer = new StringBuilder(input);
            for (Replacement replacement : replacements) {
                writer.replace(
                        replacement.getReplaceRange().lowerEndpoint(),
                        replacement.getReplaceRange().upperEndpoint(),
                        replacement.getReplacementString());
            }
            return writer.toString();
        }

        private static Collection<Range<Integer>> toRanges(AsyncFormattingRequest request) {
            if (isWholeFile(request)) {
                // The IDE sometimes passes invalid ranges when the file is unsaved before invoking the
                // formatter. So this is a workaround for that issue.
                return List.of(Range.closedOpen(0, request.getDocumentText().length()));
            }
            return request.getFormattingRanges().stream()
                    .map(textRange -> Range.closedOpen(textRange.getStartOffset(), textRange.getEndOffset()))
                    .toList();
        }

        private static boolean isWholeFile(AsyncFormattingRequest request) {
            List<TextRange> ranges = request.getFormattingRanges();
            return ranges.size() == 1
                    && ranges.get(0).getStartOffset() == 0
                    // using greater than or equal because ranges are sometimes passed inaccurately
                    && ranges.get(0).getEndOffset() >= request.getDocumentText().length();
        }

        @Override
        public boolean isRunUnderProgress() {
            return true;
        }

        @Override
        public boolean cancel() {
            return false;
        }
    }
}
