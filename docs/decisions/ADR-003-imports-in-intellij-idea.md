# ADR-003: In IntelliJ IDEA, Reformat leaves the imports, and Optimize imports is to become ours

## Status
Proposed

## Date
2026-11-14; revised 2026-11-24 for IntelliJ IDEA 2025.2 and later

## Context

With the plugin enabled, IntelliJ IDEA's Reformat runs open-java-format through the part-of-a-file entry ([ADR-001](ADR-001-one-output-per-entry-point.md)): `Main --character-ranges … --output-replacements`, which never touches imports. Imports are left to the IDE's Optimize imports, and that is the IDE's own `JavaImportOptimizer`, with the IDE's import layout and its thresholds for `import a.b.*;`. The command line, the GitHub Action and the build plugins then sort the imports again with `ImportOrderer`.

In the sources of IntelliJ IDEA 2024.2.6, the platform the plugin is built against:

- Every path that optimizes imports goes through `OptimizeImportsProcessor.collectOptimizers`: Optimize Imports; Reformat Code and Reformat Files with "Optimize imports"; Actions on Save; "Optimize imports" before a commit; the quick fix for missorted imports; "Optimize imports on the fly". The quick fix for an unused import, "Remove unused imports", deletes the imports it highlights itself (`RemoveAllUnusedImportsFix`). It asks `FormattingServiceUtil.findImportsOptimizingService(file)` for a service with `Feature.OPTIMIZE_IMPORTS`, and takes that service's `getImportOptimizers`.
- The plugin's `PalantirJavaFormatFormattingService.getFeatures()` returns `FORMAT_FRAGMENTS` only, and `getImportOptimizers` comes from `AbstractDocumentFormattingService`, which returns none; so `CoreFormattingService` answers with the IDE's optimizer.
- `processFile` of an optimizer runs in a background task under a read action (`OptimizeImportsProcessor.prepareTask`), so the time of one call counts.
- With "Optimize imports" on, Reformat Code on files and Reformat in the editor over the whole file run the formatter first and the optimizer after it (`AbstractLayoutCodeProcessor.getAllProcessors`, `FileInEditorProcessor`); over "only VCS-changed text" the editor runs the optimizer first.

From IntelliJ IDEA 2025.2 (build 252), two of those paths no longer reach `collectOptimizers`: "Optimize imports on the fly", and the quick fix for missorted imports. JetBrains made that fix a `ModCommandAction` (IDEA-370071, JetBrains/intellij-community@7ddc26cb5427), whose `perform` runs the IDE's own `JavaImportOptimizer` on a copy of the file; on the fly it runs in a non-blocking read action that expires once the file changes. This is read from the classes of IntelliJ IDEA 2026.3 (263.7307.200); those of 2025.1.7.2 still take the path above.

The plugin had an optimizer of its own for three weeks. google-java-format added one when it moved to the `FormattingService` API (google/google-java-format@84b2c9a2, 2023-03-02), running the formatter inside the IDE, for which users add `--add-exports` to the IDE's VM options. palantir/palantir-java-format#1078 followed it on 2024-04-26, and palantir/palantir-java-format#1089 removed the optimizer on 2024-05-21 as "slow". It called `FormatterService.fixImports`, which out of process runs `Main` without `--fix-imports-only`: a new JVM, and the whole file formatted, on every Optimize imports (#221). With the flag, one call on a small file takes about 20 ms with the native binary and 0.3 s with a new JVM.

## Decision

- **Reformat never changes imports**, over a part of a file or a whole one. It is the part-of-a-file entry.
- **Imports change only through Optimize imports.** Until the plugin's optimizer exists, that is the IDE's own.
- **The plugin's optimizer is to replace the IDE's** (#222): `getFeatures()` adds `OPTIMIZE_IMPORTS`, and `getImportOptimizers` returns an optimizer that calls `FormatterService.fixImports`. From then on it runs whenever Optimize imports is on: on every path listed above in IntelliJ IDEA 2024.2 to 2025.1, and from 2025.2 on all of them but "Optimize imports on the fly" and the quick fix for missorted imports, which the IDE keeps to its own optimizer.
- **`fixImports` out of process passes `--fix-imports-only` first** (#221).

## Alternatives Considered

### Reformat of a whole file fixes imports too, in one pass
- Pros: Reformat of a whole file would give the command line's bytes, the imports included, without Optimize imports.
- Cons: Reformat would change imports with Optimize imports off, which the IDE's users do not expect of it.
- Rejected: imports belong to Optimize imports.

### Keep the IDE's optimizer, and set its import settings to open-java-format's
- Pros: no optimizer of ours; a `CodeStyleSettingsModifier` can set the Java import settings for each file, as EditorConfig does.
- Cons: the IDE's sorting is still the IDE's, not `ImportOrderer`'s.
- Not chosen as the answer; a settings modifier may still help the imports the IDE inserts itself, through auto-import and completion, which no optimizer sees.

### An optimizer running the formatter inside the IDE, as google-java-format does
- Pros: no process per call.
- Cons: the javac packages have to be reachable from the IDE's JVM.
- Left to how the plugin runs the formatter (#223); the optimizer calls `FormatterService.fixImports` whichever way that is.

## Consequences

- Until #222, imports in IntelliJ IDEA follow the IDE's settings, and the command line or CI may sort them again.
- #222 depends on #221.
- The optimizer's time counts inside a read action: about 20 ms a call with the native binary, about 0.3 s with a new JVM, on a small file. "Optimize imports on the fly" runs while typing, in IntelliJ IDEA 2024.2 to 2025.1. The optimizer waits for the formatter in a way that a write action can interrupt, so typing does not wait for it (#253).
- From IntelliJ IDEA 2025.2, imports optimized on the fly or by the quick fix for missorted imports follow the IDE's rules, with the import layout that the plugin's `CodeStyleSettingsModifier` sets: they lose an unused `import a.b.*;`, which open-java-format keeps. An explicit Optimize Imports, Actions on Save and the command line give open-java-format's imports.
- The plugin's formatting service is asynchronous, and google-java-format's optimizer notes that it "can overwrite the results from the main formatter": the optimizer has to be checked in a running IDE.
