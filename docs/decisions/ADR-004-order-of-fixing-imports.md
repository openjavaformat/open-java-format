# ADR-004: The order of fixing imports stays as it is, until #224 decides it

## Status
Proposed

## Date
2026-11-14

## Context

The whole-file entry removes unused imports, sorts them and lays out the code ([ADR-001](ADR-001-one-output-per-entry-point.md)), and the code does it in two orders:

| Order | Where |
|---|---|
| **After the layout:** layout, remove unused, sort, reflow | `FormatFileCallable.formatFile`: the command line, the native binary, the GitHub Action, the pre-commit hook, the playground |
| **Before the layout:** sort, remove unused, layout, reflow | `Formatter.formatSourceAndFixImports`: the Maven plugin and our Gradle Spotless step; Spotless' own `palantirJavaFormat()` calls the same steps in the same order, without the reflow |

`ImportOrderer` moves a comment along with its import, and `RemoveUnusedImports` leaves the comment of an import it removes behind, so the order decides where that comment ends up:

- **A comment at the end of a removed import's line:** after the layout it goes with the import above it; before it, it stays where the removed import was sorted to.
- **A comment under a removed import, above the class:** after the layout it stays on the class; before it, a blank line stays between them.
- **An extraneous semicolon between imports**, which javac 21 rejects with `--release 21` and accepts with a warning below it: after the layout the file fails with `extraneous semicolon`; before it, `ImportOrderer` drops the semicolon and the file is formatted.
- **A comment inside a module name**, `import module java./* comment */ base;`: after the layout it stays on one line; before it, the line breaks after the dot.

Over 3,865 real files, as they are and with an unused import added to each, the orders give different files for 1, `CharsetEncoder` of JDK 21, and each order passes its check on what the other wrote. The 915 cases of `FormatterCasesTest` give the same output in both.

google-java-format has moved to the other order: google/google-java-format@633ce6be (2026-10-20) removes unused imports, reorders modifiers and sorts imports in one pass before the layout, on its command line too, and remaps the requested character ranges through each of its edits.

## Decision

Keep both orders as they are, so that no surface's output changes now. The difference is an accepted exception of [ADR-001](ADR-001-one-output-per-entry-point.md), and #224 decides it later, for instance when google-java-format's `Cleanups` are ported.

## Alternatives Considered

### Fix the imports after the layout everywhere, as the command line does
- Pros: the command line, and with it what the GitHub Action and the pre-commit hook check, does not change; ranges stay offsets into the input.
- Cons: the Maven plugin and the Spotless step change in the cases above; an extraneous semicolon between imports fails them as it fails the command line; porting from google-java-format gets harder.
- Not now: it was the first commit of #220, reverted.

### Fix the imports before the layout everywhere, as google-java-format does
- Pros: follows google-java-format; tolerates an extraneous semicolon; matches Spotless' own step.
- Cons: the command line's output changes in the cases above, and every user of the GitHub Action sees it; ranges have to be remapped through the import edits, and `ImportOrderer` and `RemoveUnusedImports` would return replacements instead of text.
- Not now: the larger change, best made together with google-java-format's `Cleanups`.

## Consequences

- The whole-file surfaces of the two groups can write different files where a removed import has a comment, or an import is followed by an extra semicolon.
- Cases that pin one order for these seams wait for #224.
- Whichever order #224 picks, the code of the other group changes, and its users see the files in the cases above change once.
