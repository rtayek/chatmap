# ChatMap

ChatMap requires Java 25. Use the Gradle wrapper for build, test, run, and Eclipse setup.

## Gradle Commands

From Windows Command Prompt or PowerShell:

```bat
gradlew.bat test
gradlew.bat run
gradlew.bat eclipse
```

From Windows Git Bash, Linux, or WSL:

```bash
./gradlew test
./gradlew run
./gradlew eclipse
```

`./gradlew eclipse` prepares the plain-Eclipse classpath without Buildship by copying dependencies to `lib/` and generating `.classpath`.

The checked-in plain-Eclipse JavaFX jars currently target Windows. `run.sh` is a Windows Git Bash helper, not a portable Unix launcher.

The Gradle build configures OpenJFX 25.0.1 and runs `chatmap.ui.ChatMapLauncher`, which starts `ChatMapApp`.

## Local Data

By default, ChatMap stores runtime data in `${user.home}/.chatmap`. Set `CHATMAP_HOME` to override that location. This checkout uses the ignored `.chatmap-local/` directory for private local runtime data; never commit `.chatmap-local/`.
