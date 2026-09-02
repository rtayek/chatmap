# ChatMap Project Handoff — 2026-09-01

> **Status: superseded.** Completed work and remaining items were reconciled
> into the root `working-context.md` on 2026-09-02. This document is retained
> as historical evidence and no longer defines the active agenda.

## Executive summary

ChatMap is in a stable state: the watcher-intake work and ChatGPT identity regression fix have been merged, the full Gradle check has passed locally, and obsolete remote branches were largely removed. The most useful next work is to update the high-level documentation in small, verifiable steps and then continue hardening automatic handoff intake and worker coordination.

Do not assume repository state from this document alone. Begin by fetching and verifying `master`, the working tree, and remaining branches.

## Project intent

ChatMap preserves and organizes durable information from chats, LLM workers, handoffs, projects, and worker lifecycles. The long-term goal is not merely chat storage: it is reliable continuity between human decisions, automated workers, and later sessions.

## Recently completed

- Renamed the project and its Eclipse/Gradle references to `chatmap`.
- Removed the obsolete `cjatmanager-worker-lifecycle` worktree and stale worktree metadata.
- Merged the persisted worker-lifecycle vertical slice and soak harness.
- Merged `feature/handoff-watcher-intake`.
- Hardened `HandoffWatcher` so it can:
  - accept multiple configurable source directories;
  - scan at startup and periodically rescan;
  - wait for files to become stable;
  - retry files left at the source;
  - recognize Markdown filenames containing `handoff`, case-insensitively;
  - preserve filename collisions rather than overwrite files;
  - run once with `--once`;
  - remain independent of `chatmap.*` code.
- Verified watcher intake by moving handoff files from `Downloads` into:

  ```text
  ~/eclipse-workspace/incoming/handoff-intake
  ```

- Removed identical duplicate handoff files after comparing SHA-256 hashes.
- Fixed the SpotBugs nullability warning in `HandoffWatcher.isHandoff(Path)`.
- Ran the full Gradle `clean check` successfully. Live provider tests were skipped intentionally because they require real external providers.
- Merged the ChatGPT JSON import identity regression fix.
- Deleted most merged or obsolete remote branches.

## Important decisions

### Handoff watcher boundary

For now, `HandoffWatcher` remains in ChatMap as an incubating, independent package. It should collect files from configured local sources and place them safely in a central intake directory.

It should not decide project ownership, invoke an LLM, create assignments, update ChatMap's database, commit repositories, or deliver directly to projects. Those belong to a later processor or orchestrator.

The intended boundary is:

```text
source directories -> HandoffWatcher -> durable intake directory -> future processor/router
```

Java can watch a local checkout of the incoming Git repository; another mechanism must fetch or synchronize that repository.

### Saving useful work

The shared persona guidance now favors creating a downloadable, dated file whenever more than a couple of lines resemble reusable content. This includes scripts, programs, configuration, reports, documents, and substantial prose. The aim is to preserve useful history instead of leaving it only in a transient conversation.

### Work style

- Prefer small, reversible changes.
- Use topic branches for nontrivial work.
- Keep handoff transport independent of ChatMap's domain and database.
- Use Bourne-shell commands for Ray's Windows/Git Bash environment; avoid PowerShell unless there is no practical alternative.
- Do not erase local runtime data such as `.chatmap-local` during repository recovery.

## Current operational details

The tested one-pass watcher shape is:

```sh
./gradlew handoffWatcher \
  -Pargs="--inbox $HOME/eclipse-workspace/incoming/handoff-intake --source C:/Users/ray/Downloads --source C:/Users/ray/eclipse-workspace/incoming --once"
```

Gradle on Windows may reinterpret MSYS paths such as `/c/Users/ray/...` as `C:\c\Users\ray\...`. Pass Windows-style paths in Gradle arguments when this occurs.

Do not configure the entire incoming repository as both a source and an undifferentiated destination. The temporary destination is its `handoff-intake` subdirectory. A more explicit queue layout can be introduced later.

## Unfinished work

### 1. Verify repository and branch state

Two historical identity-fix remote branches may still exist:

```text
origin/fix/chatgpt-json-import-identity
origin/fix/chatgpt-json-import-identity-v2
```

Confirm that their useful commits are contained in `master`, then delete them if they contain no unique history worth retaining.

### 2. Refresh high-level documentation in baby steps

Review these files against the implementation:

```text
design.md
first-principles.md
implementation-notes.md
```

`evo.md` is historical and should not be treated as immutable, but avoid rewriting its history merely to match current code.

Known drift to investigate:

- `implementation-notes.md` has an obsolete package/layout description.
- `design.md` does not fully describe prompt/provider handling, related projects, worker lifecycle, watcher intake, or orchestration.
- Documented search and resolution order may not match the current implementation.
- `first-principles.md` says source/history should be preserved, while at least one import-update path may replace prior message content rather than retaining versions.
- JDBC types appear to leak into higher layers.
- Provider/channel terminology remains inconsistent in places.

Start with one small documentation correction, verify it, commit it, and stop before expanding scope.

### 3. Harden handoff intake

- Decide whether intake should move or copy files from a Git checkout. Moving tracked files creates deletions in that repository.
- Add provenance: source path, arrival time, and preferably a content hash.
- Detect content duplicates explicitly rather than only avoiding filename collisions.
- Define queue states such as `incoming`, `needs-decision`, `processed`, and `failed`.
- Decide how the incoming Git checkout is fetched and whether intake changes are committed or acknowledged.
- Investigate any remaining Windows cleanup issues around temporary worktrees and directories.

### 4. Improve worker lifecycle and coordination

- Require a semantic handoff before normal worker retirement.
- Preserve failure reasons, partial work, and handoffs for failed or cancelled sessions.
- Decide whether a single assignment may have multiple sessions.
- Add cross-worker queries such as active work and items awaiting Ray's decision.
- Develop semantic-preservation tests; existing soak tests validate persistence and consistency, not whether a handoff retained the important meaning.

### 5. LLM relay experiment (separate `bin` repository)

A Bourne-shell prototype was built on `experiment/llm-relay` in the `bin` repository. It delegates a short request to a worker and has a supervisor classify the result as exactly `YES`, `NO`, or `MAYBE`, with malformed output treated as `INVALID`.

The branch contains unrelated commits. Do not merge it wholesale. Isolate or cherry-pick the relay implementation commit (`4989599`) onto a clean branch based on `bin/master`, then review and test it. This is related research, not yet ChatMap production code.

## Suggested next session

Run:

```sh
git switch master
git pull --ff-only
git status -sb
git branch -r
./gradlew check
```

If clean, choose exactly one next step:

1. make one small correction to `implementation-notes.md`; or
2. define and test provenance metadata for watcher intake.

The documentation correction is the lower-risk starting point. The watcher provenance work is the higher-value functional step.

## Verification cautions

- `CliLlmProvidersLiveTest` and `OllamaProviderLiveTest` are expected to skip without configured live providers; skipped live tests do not invalidate the offline build.
- Windows directory handles have repeatedly prevented Git from deleting empty directories. Before deleting lock files, confirm no Git process is active.
- Repository cleanup does not require a push unless tracked files or commits changed.
- Preserve `.chatmap-local` before recloning or replacing a checkout; it contains local database and runtime state that GitHub does not contain.
