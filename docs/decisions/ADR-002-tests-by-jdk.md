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
- `Assumptions.assumeTrue` on `Modifier.VALUE` existing: `valueClassAndRecord` in `FormatterTest` and `valueClass` in `ModifierOrdererTest`, for the value classes of JEP 401, a preview of JDK 28, whose early-access builds have the `value` modifier.

On JDK 21, where a local build runs, these 14 tests count as skipped among 1,422, and nothing says a change broke them. That is how the first push of #220 passed locally and failed CI's JDK 25 to 28 builds on `ModuleImportTest`. The case files of `FormatterCasesTest`, in `test-cases/`, have no way to say which JDK they need, so a case of newer syntax cannot be written at all.

## Decision

A test that needs a newer JDK says so on its class, and its case files live in a folder of that JDK:

- **Case files by JDK, side by side.** `test-cases/` becomes `test-cases-java21/`, for any JDK from 21; cases that need JDK 25 go to `test-cases-java25/` beside it, those that need JDK 28 to `test-cases-java28/`, and so on, one level deep.
- **Classes by JDK.** A test class that needs a newer Java carries `@EnabledForJreRange` with that version, as `min = JRE.JAVA_25` (JUnit 6.1.3 has constants up to `JRE.JAVA_28`). On an older JDK it reports as disabled; on that JDK and later it runs with the JDK 21 tests.
- **One class per JDK for the case folders, JDK 21 too.** Each names the folders its JDK parses, one, two or three of them, newest first, as `FormatterCasesJava25Test` runs `test-cases-java25` and `test-cases-java21`; each is enabled from its JDK on, `min = JRE.JAVA_21`, `JAVA_25` or `JAVA_28`, with no upper bound, so on a newer JDK the older folders run again in the newer classes. The code they share takes the folders as arguments.
- **Module imports from JDK 25.** They parse from JDK 23 as a preview, and are final in 25; CI runs no 23 or 24.
- **Preview features by the version that previews them.** Value classes are a preview of JDK 28: their formatting case goes to `test-cases-java28/`, and the `ModifierOrderer` test to a class with `@EnabledForJreRange(min = JRE.JAVA_28)`. CI's JDK 28 job runs early-access builds, which have the `value` modifier.
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

### A condition on the `value` modifier for value classes
- Pros: the tests would run on any JDK build that has the modifier, whatever its version.
- Cons: a second kind of condition, and a case file cannot carry it; the early-access builds of JDK 28, where the feature is previewed, have the modifier.
- Rejected: preview features go by the version, as the rest.

## Consequences

- `FormatterCasesTest` keeps the code that runs the cases; `FormatterCasesJava21Test`, `FormatterCasesJava25Test` and `FormatterCasesJava28Test` run them. A new JDK folder means a new class that names it and the older ones. The comments that name `test-cases/`, in `Level.java` and `RemoveUnusedImportsTest`, change with it.
- The `ModuleImport` pair of `FormatterIntegrationTest` becomes a case in `test-cases-java25/`; `ModuleImportTest` and the module-import test of `RemoveUnusedImportsTest` carry `@EnabledForJreRange(min = JRE.JAVA_25)`.
- The value-class test of `FormatterTest` becomes a case in `test-cases-java28/`; the one of `ModifierOrdererTest` moves to a class with `@EnabledForJreRange(min = JRE.JAVA_28)`.
- On JDK 23 and 24 the module-import tests no longer run.
- Before a push, the tests run on JDK 21 and 25 locally: `./gradlew test -PjavaRuntime=25 -PtestkitGradleVersion=9.3.1`; a change to the JDK 28 tests also on an early-access build of 28, with `-PjavaRuntime=28 -Porg.gradle.java.installations.paths=<its home>`.
- Comparing the native binary, which parses JDK 25 code whatever the test JVM, with an in-process surface on `test-cases-java25/` needs a test JVM of 25 or later.
