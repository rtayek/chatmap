# ChatMap

ChatMap requires Java 25. Use the Gradle wrapper for build, test, run, and Eclipse setup.

## Project Documents

- `first-principles.md` — stable purpose and knowledge principles
- `.llm/design.md` — durable design decisions and boundaries
- `implementation-notes.md` — current implementation and technology details
- `.llm/working-context.md` — current state, closed experiments, and next work

Files under `.llm/handoffs/` are task transfers, preliminary work, research, or
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

## Gemini Takeout Import

Extract the Takeout archive first, then pass the directory containing
`Gemini in Workspace/Conversation History/`:

```sh
./gradlew importGeminiTakeout -Pargs=tmp/Takeout
```

Use `gradlew.bat` in PowerShell or Command Prompt. Add
`-Phome=tmp/gemini-import-test` to use an isolated ChatMap database.
Without `-Phome`, the command uses the normal ChatMap home.
Quote the entire `-Pargs=...` or `-Phome=...` argument if its path contains spaces.

This command supports the Workspace JSON conversation format in extracted
`conversation_<numeric-id>.txt` files. Direct ZIP/TGZ input and other Takeout
formats are not supported. All conversation files are validated before any
are persisted; malformed files and duplicate turn indices produce an error.
Database writes are transactional per conversation, and persistence failures
are reported with a nonzero command exit status.

Repeated imports match the filename ID within the `geminiTakeoutJson` source.
Unchanged transcripts are retained; changed transcripts replace the messages
in the same chat. Empty conversations are skipped. Matching does not deduplicate
against Gemini web or CLI sources. Filename stability across separate exports
or accounts has not been established; validation used one real conversation
with 16 turns. Use separate ChatMap homes for exports from different accounts.

Titles, source timestamps, and raw turn JSON (including citations) are retained.
Response text parts are joined with blank lines. Existing import behavior
compares message roles and text: citation-only or message-timestamp-only changes
do not replace stored message payloads.

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
