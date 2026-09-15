# open-java-format Eclipse Plugin

## Installation

1. Run `./gradlew :open-java-format-eclipse-plugin:build` in the main folder,
1. Eclipse has to run on Java 21 or later, as current Eclipse packages do. The formatter reaches into
   javac, so add these options to `eclipse.ini` after `-vmargs`:
   ```
   --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
   --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
   --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
   --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
   --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
   ```
   Keep each option and its value on one line, joined by `=`: the Eclipse launcher starts the JVM
   inside its own process, and an option split over two lines does not take effect — formatting then
   fails with an `IllegalAccessError` in the workspace log.
1. Copy `open-java-format-eclipse-plugin/build/libs/open-java-format-eclipse-plugin-<version>.jar` to the `dropins` folder of your Eclipse installation,
1. Run `eclipse -clean`,
1. Pick `open-java-format` in Window → Preferences (Eclipse → Settings on macOS) → Java → Code Style →
   Formatter → Formatter implementation.
