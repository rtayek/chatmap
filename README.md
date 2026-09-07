# ChatMap

ChatMap requires Java 25. Use the Gradle wrapper for build, test, run, and Eclipse setup.

## Project Documents

- `first-principles.md` — stable purpose and knowledge principles
- `design.md` — durable design decisions and boundaries
- `implementation-notes.md` — current implementation and technology details
- `working-context.md` — current state, closed experiments, and next work

Files under `handoffs/` are task transfers, preliminary work, research, or
history. They are not current project authority unless `working-context.md`
explicitly names one as an active assignment.

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

## Bounded A2A Experiment

Start the local Ollama-backed A2A worker in one terminal:

```sh
./start-a2a-ollama.sh
```

Keep that terminal running. From another terminal, record one model-backed task
in an isolated temporary ChatMap home or run the fixed structured semantic
probe:

```sh
./gradlew a2aModelRecord
./gradlew a2aSemanticProbe --console=plain
```

Stop the A2A server with `Ctrl+C`. These commands do not use the normal
ChatMap database or UI. See `handoffs/a2a-experiment-runbook.md` for the
complete deterministic, continuation, recording, and inspection commands.

The CLI LLM provider tests are mocked during normal builds. To run the opt-in
live smoke tests against the locally installed and authenticated providers:

```bat
gradlew.bat test --tests chatmap.infrastructure.llm.*LiveTest -PliveLlm=true
```

Set `-PliveOllamaTarget=ollama-qwen257b` to use another curated
Ollama target when running the Ollama live test directly.

`./gradlew eclipse` prepares the plain-Eclipse classpath without Buildship by copying dependencies to `lib/` and generating `.classpath`.

The checked-in plain-Eclipse JavaFX jars currently target Windows. `run.sh` is a Windows Git Bash helper, not a portable Unix launcher.

The Gradle build configures OpenJFX 25.0.1 and runs `chatmap.presentation.ui.ChatMapLauncher`,
which starts `ChatMapApp`.

## Local Data

ChatMap home contains local runtime data such as `chatmap.db`, backups, reports, and future generated indexes. The default database is `chatmap.db` inside the selected ChatMap home.

Home resolution order is: `--home <directory>`, nonblank `CHATMAP_HOME`, existing `./.chatmap-local`, then existing `${user.home}/.chatmap`. If none exists, ChatMap fails instead of creating an unexpected empty database.

This checkout uses the ignored `.chatmap-local/` directory for private local runtime data. Never commit `.chatmap-local/`. The normal launch command is `./gradlew run`.
