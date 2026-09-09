# Codex Handoff: Unify ChatMap Home and Database Resolution

## Goal

Finish the project-local data migration by giving every ChatMap entry point one predictable way to locate:

1. the ChatMap application home directory; and
2. the selected SQLite database.

Gradle, direct Java, Eclipse, and CLI entry points must use the same Java resolver. Normal operation from the repository must not require an Eclipse run configuration or `CHATMAP_HOME`.

Do not move or rename the real database again during this task.

## Current Local State

Repository root:

`C:\Users\ray\eclipse-workspace\cjatmanager`

Current project-local home:

`C:\Users\ray\eclipse-workspace\cjatmanager\.chatmap-local`

It contains:

- `chatmap.db` — approximately 20 MB
- `chatmap.db.backup-chatgpt-archive-20260805-232103`
- `reports/keyword-inventory-20260805-235130/`

Verified database counts:

- chats: 309
- messages: 8,311
- imported ChatGPT chats: 301

The previous `${user.home}/.chatmap` directory is absent. An accidental empty fallback database was created by a direct Eclipse launch and then removed.

`./gradlew run` currently works because direnv exports:

```bash
CHATMAP_HOME=C:/Users/ray/eclipse-workspace/cjatmanager/.chatmap-local
```

The local `.envrc` and `.chatmap-local/` are ignored.

## Existing Uncommitted Work

Build on the current worktree; do not discard it.

Previously reported repository changes:

Modified:

- `.gitignore`
- `README.md`
- `src/chatmap/cli/ImportChatGptArchiveCli.java`
- `src/chatmap/cli/SummarizeChatCli.java`
- `src/chatmap/ui/ChatMapApp.java`

Added:

- `src/chatmap/config/ChatMapPaths.java`
- `tst/chatmap/config/ChatMapPathsTest.java`

Before this refinement, 166 tests passed.

Do not commit or push. Stop for Ray's review.

## Required Concepts

Keep these concepts separate:

### ChatMap home

The application-owned directory containing configuration, the default database, backups, reports, embeddings, and other local generated material.

### Selected database

The exact SQLite database currently opened by ChatMap. It defaults to `chatmap.db` inside ChatMap home, but configuration may later select a database elsewhere.

Do not introduce a second “database home” environment variable. An exact database file path is clearer.

## ChatMap Home Resolution

Use this precedence consistently:

1. Explicit command-line `--home <directory>` when the entry point supports arguments.
2. Nonblank `CHATMAP_HOME` environment variable.
3. Existing `./.chatmap-local` beneath the current working directory.
4. Existing legacy `${user.home}/.chatmap`.
5. If none exists, fail clearly and require an explicit location rather than silently creating a database somewhere unexpected.

Requirements:

- Resolve the selected home to a normalized absolute path.
- Treat blank and whitespace-only values as absent.
- Pure resolution must not create directories or files.
- `./.chatmap-local` means `Path.of("").toAbsolutePath().normalize().resolve(".chatmap-local")`.
- Select the current-directory home only when that directory already exists.
- Select the legacy home only when it already exists.
- An explicitly supplied `--home` or `CHATMAP_HOME` may designate a not-yet-created directory because the user deliberately selected it; writable startup may create that selected home.
- Error messages must include the candidate locations and explain `--home` and `CHATMAP_HOME`.

Do not search arbitrary sibling directories or silently walk the entire filesystem.

## Database Resolution

For this task, preserve the current physical database location:

`<ChatMap home>/chatmap.db`

Add a clean separation in `ChatMapPaths` between:

- `homeDirectory()`
- `databasePath()`

Optionally support an exact command-line `--database <file>` override if this can be added uniformly without complicating the entry points. If implemented, it has precedence over the home default and must be normalized to an absolute path.

Do not move `chatmap.db` into a new `databases/` directory yet.

Do not silently create a database when home discovery found nothing. Database initialization is allowed only after a home was selected explicitly or an existing ChatMap home was discovered.

Reserve a future `config.properties` setting such as `database=...`, but do not implement a broad configuration framework unless it remains genuinely small. Document deferred behavior instead of overbuilding it.

## One Resolver for Every Entry Point

Route all production paths through `ChatMapPaths`. Inspect the repository with `rg` and eliminate hard-coded production constructions of `${user.home}/.chatmap`.

At minimum cover:

- `ChatMapApp` / `ChatMapLauncher`
- `ImportChatGptArchiveCli`
- `SummarizeChatCli`
- Gradle `run`
- Gradle JavaExec tasks for archive import and summarization

Gradle must not reimplement home/database precedence in Kotlin. It should launch Java with the repository root as the working directory, allowing Java's resolver to discover `./.chatmap-local` when the environment variable is absent.

Make the working directory explicit for relevant Gradle `JavaExec` tasks if it is not already guaranteed.

Direct Eclipse launch should work from the project directory without an environment-variable launch setting. Workspace launch configuration variables may remain, but the application must not depend on them.

## Argument Handling

Add a small, shared argument parser only if necessary. Do not add a command-line framework.

Requirements:

- `--home <directory>` must not interfere with each CLI's existing positional arguments.
- Paths containing spaces must work.
- Do not split Gradle argument strings using a naive `split(" ")` implementation.
- Unknown options and missing option values must produce clear usage errors.

If uniform `--home` support for JavaFX would cause disproportionate complexity, prioritize environment/current-directory discovery and document the limitation. Do not duplicate parsing logic carelessly.

## Startup Diagnostics

Make the selected paths visible.

At startup, print concise diagnostics to standard output:

```text
ChatMap home: <absolute path>
ChatMap database: <absolute path>
```

After the initial list loads, report the chat count through the existing status mechanism or console without exposing chat content.

This diagnostic is required so a wrong empty database cannot remain mysterious.

## Tests

Keep path resolution testable without mutating actual process environment or global system properties. Use injected environment maps and candidate paths where appropriate.

Cover at least:

- explicit `--home` wins over environment and defaults
- nonblank `CHATMAP_HOME` wins over current and legacy directories
- blank environment value is ignored
- existing current-directory `.chatmap-local` wins over legacy home
- missing current-directory home falls back to existing legacy home
- no candidate produces a clear failure and creates nothing
- normalized absolute results
- default database is `<selected-home>/chatmap.db`
- exact database override, if implemented
- paths containing spaces
- pure resolution has no filesystem side effects

Use temporary directories for tests. Never point tests at the real database.

## Required Runtime Verification

After tests pass, verify both paths use the same real database:

### Gradle without the environment variable

```bash
env -u CHATMAP_HOME ./gradlew run
```

Expected:

- selects `<repo>/.chatmap-local`
- displays the populated chat list
- does not recreate `${user.home}/.chatmap`

### Direct Eclipse/Java launch

Launch `chatmap.ui.ChatMapLauncher` from the project directory without depending on an environment-variable launch setting.

Expected:

- startup output names the project-local home and database
- the populated chat list appears
- no fallback database is created under the user home

If Eclipse's working directory is not the project directory, report the observed `user.dir` and correct the local launch working directory. Do not add machine-specific absolute paths to tracked files.

## Validation

Run:

```bash
./gradlew compileTestJava
./gradlew test
./gradlew eclipse
git diff --check
git status --short --branch
git check-ignore -v .chatmap-local/chatmap.db
```

Eclipse should run the complete test suite successfully.

Verify after all launches:

- `.chatmap-local/chatmap.db` still has 309 chats and 8,311 messages unless a legitimate import changed the counts
- database file size remains plausible
- backup and reports remain present
- `${user.home}/.chatmap` remains absent
- ignored data does not appear in Git status

## Documentation

Keep README changes brief. Document:

- what ChatMap home contains
- home resolution precedence
- default database path within the selected home
- `CHATMAP_HOME` and optional `--home`
- project-local `.chatmap-local` is ignored and must never be committed
- `./gradlew run` is the normal launch command

Do not create another general design document.

## Deferred Work

Do not implement these unless required by the small resolver design:

- named database registry
- database switching UI
- moving the database into `databases/main.db`
- embeddings or vector storage
- encryption or elaborate privacy controls
- automatic database merging

## Completion Report

Stop without committing or pushing and report:

- final home and database resolution precedence
- classes and Gradle tasks changed
- startup diagnostic output from Gradle and Eclipse/direct Java
- behavior of `env -u CHATMAP_HOME ./gradlew run`
- observed Eclipse `user.dir`
- database counts from both launch paths
- confirmation `${user.home}/.chatmap` was not recreated
- exact test commands and results
- `git diff --check`
- `git status --short --branch`
- remaining limitations or deferred work

