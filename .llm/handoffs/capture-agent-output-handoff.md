---
project: chatmap
agent: claude
branch: feature-capture-agent-output
---

# Task: Persist agent output to a file, and add stream-json output format per-agent

## Background

`HandoffOrchestratorService` runs a local CLI agent per handoff task and gets back a
`CommandResult` whose `standardOutput()` holds the agent's full stdout (the tee in
`CommandRunner.readUtf8` mirrors this to `System.out` live, but nothing writes it to disk).

Currently `recordSuccess(...)` is called WITHOUT `agentResult`, so the agent's output is
discarded on the success path. On failure, only stderr/exit-code is reported, not stdout.
The goal: never lose the agent's output again — write it to a file that syncs back through
the inbox repo — and, where the agent CLI supports it, request `stream-json` output so the
captured content is structured.

## Part 1 — Persist agent stdout to a file (PRIMARY, do this first, format-independent)

This must work regardless of output format, so it is not gated on Part 2.

1. Change the success path so `recordSuccess` receives the `CommandResult`
   (rename/extend its signature, e.g. `recordSuccess(Path inboxRepo, Path file, HandoffTask
   task, Path worktree, CommandResult agentResult)`).

2. In `recordSuccess`, before archiving the task file, write the agent's stdout next to
   where the task file will be archived, as a sibling result file. Naming: for a task file
   `test-6.md`, write `test-6.result.md` into the SAME `.archive/` subfolder the task is
   moved to, so the task and its result are archived together and both sync via the inbox
   push. Include a small header (source task name, project, agent, branch, timestamp,
   exit code) followed by the raw `agentResult.standardOutput()`.

3. `git add -A` in the inbox already runs after archiving — confirm the new result file is
   included in that commit so it pushes with `autoPush`.

4. On the FAILURE path (`recordFailure`), also append the agent's stdout to the failure
   report when a `CommandResult` is available (the "could not start command" case has no
   result — guard for null). stderr is already included; add stdout below it under a
   `## Agent Output` heading. This means changing the failure call sites that DO have a
   result to pass it through.

## Part 2 — Per-agent output format (SECONDARY)

Do NOT apply one global flag — the agents differ and some have no such flag. Extend
`agentCommand(String agent)` (currently returns `[agent, "-p"]`, plus
`--dangerously-skip-permissions` for claude) to append output-format flags per agent:

- `claude`: append `--output-format stream-json --verbose`
  (VERIFIED: stream-json REQUIRES --verbose or the CLI errors. Do not omit --verbose.)
- `codex`: do NOT add a format flag unless you can verify the exact flag from
  `codex --help` inside the worktree; if unverified, leave as `-p` only.
- `antigravity` (Google's Gemini CLI successor): same rule — verify via `--help` before
  adding anything; otherwise leave as-is.

Rationale: an unverified/wrong flag makes the agent fail to start (exactly what happened
with the bogus `frog` agent), which is worse than plain-text output. Plain text still gets
persisted by Part 1.

If claude now emits stream-json (NDJSON, one JSON object per line), that is fine to store
raw in the `.result.md` file — do NOT attempt to parse or pretty-print it in this task.
A later task can add parsing/extraction of the final assistant message if desired.

## Constraints

- Keep the existing architecture boundaries (this is `application/service`; command
  execution stays behind the `CommandExecutor` port — do not touch `CommandRunner`'s tee).
- Add/adjust unit tests in `tst/chatmap/application/service/HandoffOrchestratorServiceTest.java`:
  a success run writes a `.result.md` containing the agent's stdout; a failure run with a
  result includes stdout in the report; `agentCommand("claude")` includes
  `--output-format stream-json --verbose`; `agentCommand("codex")` does NOT include a
  format flag.
- The full pre-commit quality pipeline (`./gradlew check`) must pass — it runs checkstyle,
  pmd, spotbugs, jacoco, and tests before commit.

## Validation

`./gradlew check` passes, and the new tests above are present and green.
