# Codex Handoff: Move ChatMap Data into the Project

## Goal

Move ChatMap's complete local data directory from the Windows home directory into an ignored directory inside the ChatMap repository so local Codex and other LLM tools can find it easily.

Target layout:

```text
<chatmap-repository>/
├── .chatmap-local/
│   ├── chatmap.db
│   ├── chatmap.db.backup-chatgpt-archive-20260805-232103
│   └── reports/
└── ...
```

Keep this simple. Ray is not currently concerned about elaborate security controls. Do not add encryption, permissions machinery, secret management, or complicated privacy features. Basic protection against accidental Git inclusion is required.

## Current Data

Current directory:

- Windows: `C:\Users\ray\.chatmap`
- Git Bash: `/c/Users/ray/.chatmap`

Observed contents:

```text
chatmap.db
chatmap.db.backup-chatgpt-archive-20260805-232103
reports/
└── keyword-inventory-20260805-235130/
    ├── chat-keywords.csv
    ├── keyword-counts.csv
    └── keyword-summary.md
```

Observed sizes:

- active database: approximately 20 MB
- pre-archive backup: approximately 112 KB
- reports: approximately 1.5 MB

Expected database counts:

- chats: 309
- messages: 8,311
- imported ChatGPT chats: 301

Preserve every existing file and directory.

## Git Restrictions

Install the ignore rule before moving any data.

Add this exact repository-root-anchored rule to `.gitignore`:

```gitignore
# Private local ChatMap runtime data
/.chatmap-local/
```

Verify with `git check-ignore -v` that the target database and reports are ignored.

Do not force-add, stage, commit, or push anything from `.chatmap-local`.

Code and documentation edits are authorized, but do **not** commit or push them. Stop for Ray's review.

## Configurable Data Directory

ChatMap currently constructs paths beneath `${user.home}/.chatmap`. Centralize that logic before moving the directory.

Add one small path/configuration class rather than repeating environment checks throughout the codebase.

Required resolution behavior:

1. If environment variable `CHATMAP_HOME` is present and nonblank, use it.
2. Otherwise continue using `${user.home}/.chatmap`.

Normalize the selected path to an absolute path. Create the directory only in code paths that genuinely need writable application data; pure path resolution must not have side effects.

Make resolution testable without mutating the process environment—for example, keep a package-private resolver that accepts an environment map and user-home path.

Search the entire repository for hard-coded `.chatmap` paths and route all production database/report paths through the centralized resolver. At minimum inspect:

- JavaFX application/database startup
- archive-import CLI
- summarize/tag CLI
- report or export utilities
- tests and documentation

Do not change the database filename or schema.

## Local Project Configuration

Set the active value to the repository's absolute `.chatmap-local` path.

Use the project's existing local environment convention if one already exists. Ray uses Git Bash and may have `direnv`; inspect rather than overwriting an existing `.envrc` or related configuration.

For Git Bash, the effective value should resemble:

```bash
export CHATMAP_HOME='/c/Users/ray/eclipse-workspace/<actual-project-directory>/.chatmap-local'
```

Determine the actual repository directory; do not guess it from the shell prompt.

Eclipse must use the same value. If a safe existing Eclipse launch configuration can be updated locally, do so without embedding machine-specific data in a committed file. Otherwise provide Ray with the exact one-time Eclipse Run Configuration environment-variable setting.

Do not use `setx` or alter global/user environment settings unless Ray explicitly approves that broader change.

## Move Procedure

The move must be verified and reversible.

1. Confirm the repository root and current Git status.
2. Confirm no ChatMap/Java process is using the database (`jps -lv` plus any relevant process check).
3. Record the source tree, file sizes, and SHA-256 hashes for all regular files.
4. Add and verify the `.gitignore` rule.
5. Implement and test `CHATMAP_HOME` resolution.
6. Confirm the target `.chatmap-local` directory does not already contain unrelated data. Stop if it does.
7. Move the complete `C:\Users\ray\.chatmap` directory to `<repo>\.chatmap-local` without changing its internal contents.
8. Recompute file sizes and SHA-256 hashes at the destination and compare them with the recorded source values.
9. Confirm the original `C:\Users\ray\.chatmap` path is absent. If the application recreates it during verification, stop and determine which code path ignored `CHATMAP_HOME`.
10. Open the relocated database through ChatMap and confirm the expected aggregate counts.

Do not delete any file as part of cleanup. A same-volume move is reversible by moving the directory back if verification fails.

## Tests

Add focused tests for:

- nonblank `CHATMAP_HOME` override
- blank or whitespace-only override falling back to `${user.home}/.chatmap`
- absent override falling back to `${user.home}/.chatmap`
- normalized absolute result
- no filesystem creation during pure resolution

Run:

```bash
./gradlew compileTestJava
./gradlew test
./gradlew eclipse
git diff --check
```

Eclipse should also run the complete test suite successfully.

## Verification

After the move, verify:

- ChatMap reports or demonstrably uses `<repo>/.chatmap-local/chatmap.db`
- database counts remain 309 chats and 8,311 messages unless another legitimate import occurred
- backup and all three keyword-report files exist
- source and destination hashes match
- search and selection still work
- `git check-ignore -v .chatmap-local/chatmap.db` identifies the intended anchored rule
- `git status --short --branch` does not show `.chatmap-local` contents
- the old home-directory `.chatmap` was not silently recreated

If Eclipse cannot be configured automatically, stop before claiming the move is complete and give Ray the short exact UI steps needed to set `CHATMAP_HOME`.

## Minimal Documentation

Add a short note to the existing README stating:

- default data location is `${user.home}/.chatmap`
- `CHATMAP_HOME` overrides it
- this checkout uses the ignored `.chatmap-local` directory for local runtime data
- `.chatmap-local` must never be committed

Do not create another general design document.

## Completion Report

Stop without committing or pushing and report:

- actual repository root
- old and new data paths
- local environment configuration used
- whether Eclipse still needs a manual setting
- files moved and aggregate sizes
- hash-verification result
- database counts from the relocated database
- test commands and results
- files changed in the repository
- `git check-ignore -v` result
- `git diff --check`
- `git status --short --branch`
- confirmation that the old `.chatmap` directory is absent

