# ChatMap Current-State Handoff

**Date:** 2026-08-15  
**Project:** ChatMap (`cjatmanager`)  
**Repository:** `https://github.com/rtayek/chatmap`  
**Usual Windows location:** `C:\\Users\\ray\\eclipse-workspace\\cjatmanager`

## Purpose

ChatMap is Ray's local Java application for collecting, reconciling, organizing,
searching, summarizing, and exporting AI conversations from multiple providers.
MyClaw functionality is being consolidated into ChatMap, including prompt execution
and the newer mobile-to-desktop handoff orchestrator.

The long-term goal is not merely chat storage. ChatMap should become a practical
working-memory and knowledge tool: ingest conversations, preserve their identities,
find relevant material, generate handoffs, and eventually perform semantic extraction
and context compression.

## Current Git State

The intended recent feature work has been merged into `master`.

- Reviewed master head: `fd01f0e39bc47b34d09cb401ff62ce2820199e4b`
- Commit: `Persist agent stdout to a result file; request stream-json for claude`
- GitHub Actions run for that commit passed.
- `feature-capture-agent-output` was identical to `master` when checked.
- `feature/handoff-orchestrator` was fully contained in `master` and one commit behind it.

Some older experimental branches still contain commits not present on `master`:

- `feature-smoke-test`
- `fix/chatgpt-json-import-identity`
- `fix/chatgpt-json-import-identity-v2`
- `test/mdlest-valid-task`

These appear to be stale alternatives or diagnostic branches. Do not merge them
wholesale without comparing their remaining commits to current `master`.

## Current Architecture

The code has moved toward explicit layers:

- `chatmap.domain` - core records, enums, and identities
- `chatmap.application.model` - application-level data models
- `chatmap.application.port` - AI, command, provider, and persistence boundaries
- `chatmap.application.service` - imports, prompting, reconciliation, orchestration,
  search, summaries, projects, and tags
- `chatmap.infrastructure.ai` - Claude, Codex, Antigravity, Ollama, and JShell backends
- `chatmap.infrastructure.command` - subprocess execution
- `chatmap.infrastructure.importer` - ChatGPT/Markdown/plain-text importers
- `chatmap.infrastructure.persistence.sqlite` - SQLite repositories and migrations
- `chatmap.infrastructure.provider` - live web and CLI-history providers
- `chatmap.presentation.cli` - command-line entry points
- `chatmap.presentation.ui` - JavaFX UI and controller
- `chatmap.app` - composition roots, runtime, paths, and bootstrap

Important architectural rules:

- SQLite repositories do not own their shared connection.
- Service-level transactions use the transaction abstraction rather than ad hoc
  commit/rollback code.
- Provider source values and other persisted enum values are stable database values.
- Gradle wrapper builds are authoritative; Eclipse is also used for running tests and
  the desktop application.
- Project-local runtime data is ignored by Git.

## Local Data and Conversation Inventory

The selected project-local home is normally:

```text
<repo>/.chatmap-local
```

`CHATMAP_HOME` may override that location. The database and generated reports live
under the selected ChatMap home.

The most recent reconciliation report recorded:

- 677 chats
- 23,119 messages
- 668 chats with external identity
- no duplicate `(source, externalConversationId)` identities
- no orphan messages
- no foreign-key violations
- no zero-message chats

Current six-source inventory/import results were:

| Source | Imported/current | No-content records | Coverage |
| --- | ---: | ---: | --- |
| Claude Code CLI | 170 | 0 | Complete |
| Codex CLI | 104 | 21 | Complete |
| Gemini CLI | 15 | 1,511 | Complete |
| Claude web | 41 | 0 | Complete for normal authenticated list |
| ChatGPT web | 310 | 0 | Complete for normal and archived authenticated lists |
| Gemini web | 23 | 0 | Complete for the verified normal sidebar list |

Total for that run: 663 already imported, 1,532 no-content records, no failures.

The large Gemini CLI no-content count appears to come mainly from `a2a-server`
records and is considered a separate environment/history-format investigation rather
than evidence of database corruption.

## Working Features

- JavaFX chat browser
- SQLite persistence and FTS5 search
- Plain text and Markdown import
- ChatGPT JSON and ZIP archive import
- Live enumeration/import for Claude, ChatGPT, and Gemini web
- History enumeration/import for Claude Code, Codex, and Gemini CLI
- Idempotent conversation reconciliation using external identities and content hashes
- Chat summaries and tags
- Markdown and project-handoff export
- Prompt execution backends and runnable prompt CLI
- Project-local transcripts and reports
- Handoff inbox polling, temporary Git worktrees, local agent execution, result capture,
  archive/failure reports, commits, and optional pushing

## Newly Merged Handoff Workflow

The handoff orchestrator:

1. Pulls a Git-backed inbox.
2. Finds Markdown task files in project subdirectories.
3. Parses agent and branch frontmatter.
4. Creates a temporary worktree for the target project.
5. Runs the requested local CLI agent with the task on standard input.
6. Commits agent changes when present.
7. Archives successful tasks and writes a sibling `.result.md` file.
8. Writes failure reports for failed agents.
9. Pushes when `autoPush` is enabled.

Ray wants runtime `autoPush` to remain enabled. Preserve that behavior while cleaning
up its configuration and implementation.

Claude currently runs with `--dangerously-skip-permissions` and stream-JSON output.
The result file currently stores raw stdout.

## Latest Code Review: Three Main Findings

### 1. Highest priority: Git failures can be reported as success

`HandoffOrchestratorService` invokes `git pull`, `add`, `commit`, and `push`, but most
callers do not inspect the returned exit code or timeout state.

Consequences include:

- an agent's edits may fail to commit;
- the task may still be archived;
- the service may return success;
- the temporary worktree may be forcibly removed, destroying uncommitted edits;
- a failed push may leave the phone/inbox without the result although the run claims
  success.

This is the immediate next task.

Recommended design:

- Add a checked Git-command helper that rejects timeout and nonzero exit status.
- Treat pull, target commit, target push, inbox commit, and inbox push as explicit
  workflow stages.
- Do not archive a task until the required target commit/push has succeeded.
- Do not forcibly delete a dirty worktree after commit failure; report its recovery
  path.
- Return an honest failure or pending-sync outcome when pushing fails.
- Add focused tests that inject failures at every Git stage.

### 2. Agent CLIs need provider-specific invocation adapters

The current abstraction assumes most agents use `<agent> -p`. That is not a valid
general protocol.

For example, Codex automation uses `codex exec`; when standard input is the complete
prompt, the appropriate form is based on `codex exec -`. Editing policy should be
explicit, such as `--sandbox workspace-write`.

The same generic assumptions also appear in `StandardCliBackend` for prompt execution,
session listing, and resume behavior.

Recommended design:

- Give each provider an adapter responsible for command construction, permissions,
  session operations, output format, and final-output parsing.
- Share only the subprocess execution mechanism.
- Add small installed-CLI smoke tests, separate from deterministic unit tests.

The Claude unrestricted-permission flag is an acknowledged risk. A Git worktree
isolates Git state but is not a filesystem or credential sandbox.

### 3. Prompt sessions lose their durable conversation identity

`PromptService` sends a session ID to the backend but does not store it with the chat.
`AiResponse` and `PromptResult` also cannot return a provider-created session identity.
Each distinct prompt/response exchange can therefore become a separate ChatMap chat
instead of extending one logical provider conversation.

Recommended design:

- Carry provider conversation/session identity through the backend response and prompt
  result.
- Store it as the chat's external identity.
- Append or reconcile messages atomically by `(source, externalConversationId)`.
- Test two prompts to one session producing one chat, while two different sessions
  remain separate.

## Smaller Follow-Ups

- Replace the current `/*hack*/ true` auto-push assignment with an explicit, tested
  default of `true`, while honoring documented configuration overrides.
- `CommandRunner` currently tees all subprocess output to the JVM console and also
  retains the entire output in memory. Long verbose agent runs should stream to a file
  or use bounded capture.
- Convert raw Claude stream-JSON into a readable final result while optionally
  retaining raw JSONL as a diagnostic sidecar.
- Investigate the 1,511 Gemini CLI no-content `a2a-server` records separately.
- Semantic extraction/context compression remains designed but not yet implemented.

## Recommended Next Work Order

1. Fix checked Git lifecycle and recovery behavior in the handoff orchestrator.
2. Introduce provider-specific agent command adapters, starting with Claude and Codex.
3. Preserve prompt/session identity in the database.
4. Improve result-file readability and bounded output capture.
5. Return to semantic extraction, keyword labeling, and knowledge organization after
   the ingestion and execution paths are trustworthy.

## Validation for the Next Change

Run at minimum:

```bash
./gradlew test
./gradlew eclipse
git diff --check
git status --short --branch
```

Also add deterministic tests for every newly handled failure path. Do not depend on a
real installed LLM CLI in the ordinary unit-test suite; keep live CLI checks as explicit
smoke tests.

## Suggested Opening Prompt for a Fresh Chat

```text
This is the current ChatMap handoff. Read it, then inspect the latest master branch on
GitHub before making recommendations. The immediate priority is the unchecked Git
lifecycle in HandoffOrchestratorService. Preserve runtime autoPush=true. First verify
that the handoff is still current, then propose the smallest safe implementation and
tests. Do not merge stale experimental branches wholesale.
```
