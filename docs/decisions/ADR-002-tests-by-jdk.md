# ADR-002: Tests that need a newer JDK live in classes and folders of their own

## Status
Proposed

## Date
2026-11-14

## Context

The formatter parses with the javac of the JVM it runs on, so some of its tests can only pass on a newer JDK than the one the build compiles for (Java 21). CI runs the tests on JDK 21 in its build job and on 25, 26, 27 and 28-ea in its `jdk` jobs.

Today such tests skip themselves from inside, in three ways:

- `Assumptions.assumeTrue(Formatter.getRuntimeVersion() >= 23)`: the 7 tests of `ModuleImportTest`, in a `@BeforeAll`, and `moduleImportStaysAmongOrdinaryImports` in `RemoveUnusedImportsTest`.
- `FileBasedTests.VERSIONED_TESTS`, a map from a minimum version to test names, read by `assumeJavaVersionForTest`: the `ModuleImport` pair of `FormatterIntegrationTest`, 4 runs.
- `Assumptions.assumeTrue` on `Modifier.VALUE` existing: `valueClassAndRecord` in `FormatterTest` and `valueClass` in `ModifierOrdererTest`, for Project Valhalla's value classes, a preview on JDK 28.

On JDK 21, where a local build runs, these 14 tests count as skipped among 1,422, and nothing says a change broke them. That is how the first push of #220 passed locally and failed CI's JDK 25 to 28 builds on `ModuleImportTest`. The case files of `FormatterCasesTest`, in `test-cases/`, have no way to say which JDK they need, so a case of newer syntax cannot be written at all.

## Decision

A test that needs a newer JDK says so on its class, and its case files live in a folder of that JDK:

- **Case files by JDK, side by side.** `test-cases/` becomes `test-cases-java21/`, for any JDK from 21; cases that need JDK 25 go to `test-cases-java25/` beside it, and so on, one level deep. Each folder is run by a class of its own.
- **Classes by JDK.** A test class that needs a newer Java carries `@EnabledForJreRange(min = JRE.JAVA_25)` (JUnit 6.1.3 has constants up to `JRE.JAVA_28`). On JDK 21 it reports as disabled; on JDK 25 it runs with the JDK 21 tests.
- **Module imports from JDK 25.** They parse from JDK 23 as a preview, and are final in 25; CI runs no 23 or 24.
- **Preview features by what the JDK has.** The value-class tests depend on a JDK build with Valhalla, not on a version, so their class carries a class-level condition on `Modifier.VALUE` (`@EnabledIf`), not a version range.
- **No assumption on the runtime inside a test.** `assumeTrue` on the runtime and `VERSIONED_TESTS` go.

## Alternatives Considered

### Keep the assumptions inside the tests
- Pros: no change.
- Cons: three mechanisms; a skipped test inside a class that runs looks like a pass; case files cannot say what they need.
- Rejected: a test that cannot run here should not run here, and say so.

### Folders inside `test-cases/`, as `test-cases/java25/`
- Pros: one root for the cases.
- Cons: a second level of nesting under folders that already have subfolders (`imports`, `jbang`, `stringwrapper`, `testdata`); the JDK 21 cases would have to be told apart from the JDK subfolders.
- Rejected: one folder per JDK, side by side.

### Module imports from JDK 23
- Pros: the tests start where the syntax first parses.
- Cons: a preview there, and no CI job runs 23 or 24.
- Rejected: JDK 25, where they are final and CI runs.

### Version ranges for value classes too
- Pros: one kind of condition.
- Cons: a JDK 28 build without Valhalla has no `value` modifier, and the tests would fail there.
- Rejected: preview features are gated on the feature.

## Consequences

- `FormatterCasesTest` reads `test-cases-java21/`; a new class reads `test-cases-java25/`. The comments that name `test-cases/`, in `Level.java` and `RemoveUnusedImportsTest`, change with it.
- The `ModuleImport` pair of `FormatterIntegrationTest` becomes a case in `test-cases-java25/`; `ModuleImportTest` and the module-import test of `RemoveUnusedImportsTest` carry `@EnabledForJreRange(min = JRE.JAVA_25)`.
- On JDK 23 and 24 the module-import tests no longer run.
- Before a push, the tests run on JDK 21 and 25 locally: `./gradlew test -PjavaRuntime=25 -PtestkitGradleVersion=9.3.1`.
- Comparing the native binary, which parses JDK 25 code whatever the test JVM, with an in-process surface on `test-cases-java25/` needs a test JVM of 25 or later.
