# ADR-005: The IntelliJ plugin formats inside the IDE's JVM, and offers a JVM of its own and the native binary

## Status
Accepted

## Date
2026-11-22

## Context

The plugin started a process for every Reformat and every Optimize imports: a new JVM on the IDE's runtime, or the native binary. Optimize imports runs under a read action, and "Optimize imports on the fly" runs while typing ([ADR-003](ADR-003-imports-in-intellij-idea.md), #253).

One call on JDK 21, macOS aarch64, with 21.0.2 (#223):

| How the formatter runs | `JavaInputAstVisitor.java`, 4,026 lines | A 5-line file |
|---|---|---|
| A new JVM for each call | 0.97 s | 0.3 s |
| Native binary | 0.28 s | 0.02 s |
| In the IDE's JVM, first call | 0.65 s | |
| In the IDE's JVM, warm (median of 30) | 124 ms to format, 9 ms for `fixImports` | |

The formatter parses with javac's own classes, in packages that module `jdk.compiler` exports to nobody. A JVM started for the formatter gets `--add-exports`; the IDE's JVM is already running. google-java-format formats inside the IDE and asks its users to add six `--add-exports` lines to the IDE's VM options.

IntelliJ IDEA starts its JVM with `--add-opens=java.base/java.lang=ALL-UNNAMED`: it is among the `additionalJvmArguments` of `product-info.json` in 2024.2.6, the oldest IDE the plugin supports, and in 2026.3. With `java.lang` open, a plugin can call `Module.implAddExports`, the method `--add-exports` ends in, for one class loader's module. Tried on Temurin 21.0.12 and on the JBR 25.0.4 of 2026.3, in a JVM started with that flag alone: the formatter, in a class loader of its own, formats as the command line does, and javac stays unexported to every other class loader.

## Decision

- **Three ways to run the formatter, one of them chosen per project** on the settings page: in the IDE process, in a separate JVM, as the native binary.
- **In the IDE process is the default.** A project whose settings switch the native binary on keeps it; every other project moves from a JVM for each call into the IDE.
- **The plugin exports javac to the formatter itself**, with `Module.implAddExports`, to the formatter's class loader only. Nobody edits VM options. The settings page offers the IDE process only where `JavacAccess` finds that it can: `jdk.compiler` is there, and either javac is exported already or `java.lang` is open.
- **The formatter is loaded apart from the IDE.** Its class loader's parent is the JDK's platform class loader, which finds javac and nothing of the IDE. From the plugin come only the types the two exchange: the classes of `open-java-format-spi`, and Guava, which their signatures name. One class loader per set of formatter jars, kept across projects and calls.
- **The IDE process is the faster way, never the only one.** A file that the formatter fails on there with an `Error`, a stack overflow for one, is formatted in a separate JVM on the IDE's runtime. A formatter that does not load or link there sends every file to a separate JVM until the IDE restarts, and logs an error once, for the plugin's error reports.
- **A file with a syntax error is left alone without a message** inside the IDE too, as `FormatterCommandRunner` leaves it: a file is rarely free of them while it is typed in.
- **A separate JVM runs on a JDK the user chooses**: the IDE's runtime, which is the default and what the plugin ran on before, the project SDK, or a JDK registered in the IDE. Only Java 21 or later can be chosen.
- **What cannot run falls back, and says so in `idea.log` and under Active formatter**: the IDE process to a separate JVM, a JDK that cannot run the formatter to the IDE's runtime, a missing native binary to a separate JVM as before. No balloon for the first two.

## Alternatives Considered

### A formatter daemon, as Gradle has one
- Pros: as fast once warm; a formatter bug stays out of the IDE; it can run on a newer JDK than the IDE's.
- Cons: a server mode in `Main`, a protocol, restarts and cleanup.
- Rejected: worth it only if formatting inside the IDE turned out not to work.

### Ask users to add `--add-exports` to the IDE's VM options, as google-java-format does
- Pros: public flags only.
- Cons: every user edits a file per IDE installation, and javac is exported to every plugin.
- Rejected as the way in; a user who did add the flags gets the IDE process without `implAddExports` being called.

### Load the formatter with the plugin's class loader as the parent, as the Gradle plugin does
- Pros: a plain `URLClassLoader`.
- Cons: every class the IDE has wins over the formatter's own copy: Jackson of another version, for one, and whatever a later IDE adds.
- Rejected: the IDE's class path is not ours to know.

### Keep a JVM for each call as the default, and the IDE process as an option
- Pros: a formatter bug cannot touch the IDE until a user opts in.
- Cons: the projects that wait 0.3 s to 1 s a file, and for imports while typing, stay there unless they find the setting.
- Rejected: the time is what #223 is about.

## Consequences

- `Module.implAddExports` is not public API. If JetBrains stops opening `java.lang`, or a JDK drops the method, the IDE process is greyed out with the reason, and those projects format in a separate JVM as before.
- The javac of the IDE's runtime decides which syntax parses inside the IDE: Java 21 in 2024.2, Java 25 in 2026.3. A project with newer syntax chooses a separate JVM on a newer JDK.
- A formatter bug that throws no `Error`, such as one that never returns, now happens inside the IDE.
- A formatter from a build is loaded into the IDE's JVM where it used to be run by a JVM of its own. Both happen only in a trusted project.
- A formatter from a build that needs a newer Guava than the plugin's can fail to link inside the IDE. It then runs in a separate JVM, on its own jars alone, and so does every other project's formatter until the IDE restarts: one failure switches the IDE process off for the session.
- A project whose `.idea/open-java-format.xml` is shared keeps `useNativeImage` in it beside the new `formatterMode`, for plugins from before the modes.
