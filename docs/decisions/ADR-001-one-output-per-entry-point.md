# ADR-001: Two entry points, and one output for each on every surface

## Status
Proposed

## Date
2026-11-14

## Context

open-java-format runs on many surfaces: the command line and its native binary, the GitHub Action and the pre-commit hook (both run the native binary on whole files), the playground, the Maven plugin, our Gradle Spotless step, Spotless' own `palantirJavaFormat()` step, `formatDiff` (on the JVM or with the native binary), IntelliJ IDEA (Java-based or native) and Eclipse.

Each of them reaches the formatter through code of its own, and they had drifted apart (#219):

- `FormatFileCallable.formatFile`, behind the command line, lays out the code, then removes unused imports, sorts them and reflows long strings. `Formatter.formatSourceAndFixImports`, behind the Maven plugin and the Spotless step, fixes the imports first. Where a removed import has a comment, the two write different files: 1 of 3,865 real files.
- Spotless' own step calls `ImportOrderer.reorderImports`, `RemoveUnusedImports.removeUnusedImports` and `Formatter.formatSource`, and never reflows long strings: 94 of the 3,865 files differ.
- `formatDiff` on the JVM goes through `Formatter.getFormatReplacements` and never reflows; with the native binary, and in IntelliJ IDEA, `--output-replacements` reflows, but every long string in the file once one line in the ranges needs it.
- `--lines` and `--offset` printing text format the ranges, then fix the imports and reflow the whole file.
- Tests pinned the pieces, not the surfaces: the 915 cases of `FormatterCasesTest` went through `formatSourceAndFixImports`, and give the same output in both import orders, so none of this showed.

A file that one surface formats then fails the check of another, and nothing says which output is right.

## Decision

There are two entry points, and every surface goes through one of them:

| Entry | Does | Never does |
|---|---|---|
| **Whole file** | lays out the code, removes unused imports and sorts them, reflows long strings | — |
| **Part of a file** (character or line ranges) | lays out the code the ranges cover, reflows the long strings in that code | touch the imports, or code outside what formatting the ranges rewrites |

For the same formatter version and input, every surface of an entry gives the same result: the same bytes, or the same diagnostics, for a whole file; the same replacements for a part of one. The command line is the reference for the whole file, since the GitHub Action and the pre-commit hook check against it.

- IntelliJ IDEA's Reformat is the part-of-a-file entry, also over a whole file, and leaves the imports alone. Optimize imports stays the IDE's own until the plugin's optimizer replaces it ([ADR-003](ADR-003-imports-in-intellij-idea.md), #222).
- `--lines`, `--offset` and `--character-ranges` printing text are the part-of-a-file entry, and do not fix imports.
- Eclipse is a surface of the part-of-a-file entry.

Accepted exceptions, each documented:

- **The order of fixing imports** stays as it is until #224 is decided: after the layout on the command line, before it in `formatSourceAndFixImports` and in Spotless' own step ([ADR-004](ADR-004-order-of-fixing-imports.md)).
- **Spotless' own step does not reflow long strings.** `Formatter.formatSource` keeps its meaning; the docs site's Spotless page says so.
- **Syntax of the runtime.** The formatter parses with the javac of the JVM it runs on; the native binary has GraalVM 25's. Code that needs a newer Java than the runtime fails there. The contract holds for code the runtime can parse, and tests that need a newer JDK run in classes of their own, with case files in a folder per JDK ([ADR-002](ADR-002-tests-by-jdk.md)).
- **A file that does not parse** keeps today's behaviour: in-process services throw, out-of-process ones return no replacements.

## Alternatives Considered

### Leave each surface as it is
- Pros: no change for anyone.
- Cons: a file formatted on one surface fails another's check; no output is the right one.
- Rejected: the point of a formatter shared by the build, the IDE and CI is that they agree.

### One entry: IntelliJ IDEA formats a whole file with imports, in one pass
- Pros: Reformat of a whole file would give the command line's bytes with no IDE action in between.
- Cons: Reformat would change imports even when Optimize imports is off.
- Rejected: imports in IntelliJ IDEA go through the IDE's Optimize imports, replaced later by ours ([ADR-003](ADR-003-imports-in-intellij-idea.md)).

### Make `Formatter.formatSource` reflow long strings, for Spotless' own step
- Pros: the only way from our side to give Spotless' step the command line's long strings.
- Cons: it changes what a public method does, for every caller.
- Rejected: Spotless' step stays an accepted exception.

### Let the part-of-a-file entry fix imports, as `--lines` does today
- Pros: no change for users of `--lines`.
- Cons: a range would change code outside it.
- Rejected: a range touches only the code it covers.

### Decide the import order now
- Pros: removes the last exception for whole files.
- Cons: either order changes the output of some surface; google-java-format has just moved to fixing imports before the layout.
- Deferred to #224 ([ADR-004](ADR-004-order-of-fixing-imports.md)).

## Consequences

- Each change that brings a surface to its entry is a pull request of its own, with a check through that surface which fails before the change: the in-process service returns what `--output-replacements` prints; the part-of-a-file entry reflows only in the ranges; `--lines` and `--offset` print what the replacements give; Eclipse reflows like the others; the out-of-process `fixImports` passes `--fix-imports-only` (#221).
- Tests that pin what users get go through a surface, `Main` or a `FormatterService`, not through `ImportOrderer`, `RemoveUnusedImports` or `StringWrapper` on their own (#225).
- Users of `--lines`, `--offset` and `--character-ranges` as text see imports left alone and long strings reflowed only in the ranges.
- The exceptions stay until their own decisions: #224 for the import order; Spotless' step for as long as `formatSource` keeps its meaning.
