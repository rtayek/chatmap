# ChatMap Codex Handoff: Final Eclipse/Gradle Cleanup

## Project

- Repository: `C:\Users\ray\eclipse-workspace\cjatmanager`
- Branch: `master`
- Build authority: Gradle command line
- Eclipse policy: plain Java/JDT project; do **not** use Buildship
- Java: 25

## Immediate goal

Finish the current ChatMap home/database-resolution work so it is deterministic and safe to commit. Preserve the intended `.project` changes that remove Buildship, fix the two remaining review findings below, run validation, and report the result.

Do not commit or push in this pass.

## Current verified state

The populated project-local database is:

`C:\Users\ray\eclipse-workspace\cjatmanager\.chatmap-local\chatmap.db`

Its previously verified contents are:

- 309 chats
- 8,311 messages
- 301 `chatgptJson` chats
- 7 `markdown` chats
- 1 `plainText` chat

`.chatmap-local/` and `.envrc` are intentionally ignored. Do not add, move, modify, delete, or commit the database, its backup, reports, or `.envrc`.

Recent validation reported that `./gradlew eclipse` and `./gradlew test` pass. The working tree is intentionally modified and must be preserved.

Expected tracked changes currently include:

- `.project`
- `.settings/org.eclipse.jdt.core.prefs`
- `README.md`
- `build.gradle.kts`
- `src/chatmap/cli/ImportChatGptArchiveCli.java`
- `src/chatmap/cli/SummarizeChatCli.java`
- `src/chatmap/config/ChatMapPaths.java`
- `src/chatmap/ui/ChatMapApp.java`
- `src/chatmap/ui/ChatMapController.java`
- `tst/chatmap/config/ChatMapPathsTest.java`
- `tst/chatmap/ui/ChatMapControllerTest.java`

Inspect the actual worktree before editing; preserve all unrelated user changes.

## Required fixes

### 1. Make Eclipse generation deterministic

The Gradle `eclipse` task currently adds generated comment lines like these to `.settings/org.eclipse.jdt.core.prefs`:

```text
#
#Thu Aug 06 15:13:49 PDT 2026
```

This timestamp can dirty the repository whenever `./gradlew eclipse` runs. Change the Eclipse-generation configuration so the resulting tracked preferences file is deterministic. Do not merely replace the timestamp with a new fixed date by hand; ensure rerunning the task does not regenerate changing content.

Keep the Java 25 compiler preferences intact.

### 2. Preserve Gradle task arguments exactly

`build.gradle.kts` currently contains a custom `parseTaskArgs(...)` parser. Its backslash-as-escape behavior can corrupt Windows paths such as:

`C:\Users\ray\some folder\chatgpt-export.zip`

The affected custom tasks each expect one path/chat identifier property value. Remove the custom parser and pass the complete `-Pargs` property as one unchanged argument, for example:

```kotlin
if (project.hasProperty("args")) {
    args(project.property("args").toString())
}
```

Apply this to the appropriate custom `JavaExec` tasks, including `summarizeChat` and `importChatGptArchive`. Do not regress the normal application task's built-in `--args` behavior.

Add or adjust a focused test if a practical existing test seam can verify preservation of spaces and backslashes. At minimum, validate the task configuration or invocation with a representative Windows path without importing into the real database.

## `.project` decision

Keep the intentional `.project` result that removes:

- `org.eclipse.buildship.core.gradleprojectbuilder`
- `org.eclipse.buildship.core.gradleprojectnature`

The final project should retain the standard JDT Java builder and nature. XML formatting cleanup and a deterministic resource-filter ID are acceptable. Confirm that repeated `./gradlew eclipse` does not restore Buildship or produce further changes.

## Data-location behavior to preserve

Do not undo the current shared Java path resolver. Gradle and direct Java/Eclipse launches should use the same rules and Gradle Java execution should use the repository root as its working directory.

The agreed home-resolution order is:

1. explicit `--home <directory>`, where supported
2. nonblank `CHATMAP_HOME`
3. existing `./.chatmap-local`
4. existing legacy `~/.chatmap`
5. otherwise fail clearly instead of silently creating an accidental database

The default database is `<resolved-home>/chatmap.db`. Startup should expose the resolved home/database path sufficiently for diagnosis. Preserve the tests and documentation for this behavior.

## Validation

Run from the repository root:

```bash
./gradlew eclipse
./gradlew eclipse
./gradlew test
git diff --check
git status --short --branch
git check-ignore -v .chatmap-local/chatmap.db .envrc
```

Prove Eclipse generation is idempotent: capture the relevant tracked-file diff or hashes after the first `./gradlew eclipse`, then confirm the second invocation leaves them unchanged.

Also inspect `.project` after generation and confirm it contains no Buildship builder or nature.

If testing the archive-import Gradle argument path, use a harmless fixture or configuration check. Do not run a real import against `.chatmap-local/chatmap.db` merely to test argument parsing.

## Completion report

Report:

- exact files changed
- how the generated JDT timestamp was eliminated
- how `-Pargs` is now passed
- evidence that two consecutive `./gradlew eclipse` runs are idempotent
- test results and test count
- confirmation that Buildship remains absent from `.project`
- final `git status --short --branch`
- confirmation that ignored local data was untouched

Stop without committing or pushing.
