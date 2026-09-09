# ChatMap Code-Review Fixes Handoff

Date: 2026-08-17  
Repository: `rtayek/chatmap`  
Reviewed head: `b592801f682c77e15d20b39027bb8f361da18a79`  
Last known CI: passing

## Objective

Finish the backend and handoff-orchestrator cleanup so ChatMap can safely run AI CLIs manually and later as a background process without losing output, misreporting Git failures, fragmenting provider sessions, or losing model provenance.

The implementation should remain small, explicit, and Java-first. Tests are the functional specification.

## Fixed architectural decisions

These decisions are not open for redesign during this work:

- Do not add configuration files.
- Keep the curated `ModelTarget` enum as the selectable model catalog.
- Keep `ProviderId` as an enum.
- Keep `EnumMap<ProviderId, AiProvider>` for provider wiring.
- Use shallow subclasses at the provider/protocol level only.
- Do not create a subclass for each model.
- Multiple Ollama models must share one Ollama provider implementation.
- Provider-specific command syntax, capability handling, session extraction, and output parsing belong in the provider implementation.
- A shared CLI base may contain only genuinely common process, timeout, and error mechanics.
- Preserve stable database enum values and existing imported data.
- Keep Gradle's normal `build/` directory.

## Immediate correction: remove the restored legacy class

Delete:

```text
src/chatmap/infrastructure/ai/StandardCliBackend.java
```

The file added by commit `b592801` is unused and restores an obsolete parallel `AiBackend` implementation. It also has incorrect shared command syntax, swallows session-list failures, ignores working-directory and permission/output properties, and constructs responses with false Claude metadata.

Do not wire this class into `DefaultAiBackends`. The current provider-specific classes are authoritative:

- `ClaudeCliProvider`
- `CodexCliProvider`
- `AntigravityCliProvider`
- `OllamaCliProvider`

Add an architectural test or source-level guard only if it is simple and durable. Do not build a framework merely to prevent this one file from returning.

## Work package 1: durable, bounded command output

### Current problem

`CommandRunner` bounds each captured stream at 4 MiB, but it retains only the beginning. For agent runs, the final response, summary, session identifier, or failure explanation normally appears at the end. The full output is no longer available, and normal `CommandRequest` construction also disables live console output.

### Required behavior

1. `CommandRunner` must always drain stdout and stderr concurrently so child processes cannot block.
2. In-memory output must be strictly bounded.
3. When a durable output destination is requested, the complete byte stream must be written there.
4. The bounded in-memory representation must retain the diagnostically useful ending. Keeping a small prefix plus a larger suffix is acceptable; keeping only the prefix is not.
5. Truncation must be reported only when bytes were actually omitted. Output exactly equal to the limit is not truncated.
6. Returned strings must not end with a malformed UTF-8 character caused by splitting a multibyte sequence.
7. Interactive CLI callers must explicitly request console output. Daemon/background callers must explicitly request file or logging output. `CommandRunner` should not silently choose a global console side effect.
8. Tee or file-write failures must be detectable. Do not rely on `PrintStream`, which normally swallows write failures, as the only durable-output mechanism.

### Suggested shape

Keep the design narrow. A practical implementation is:

- Add optional stdout/stderr sink paths or a small output-policy value to `CommandRequest`.
- Stream full stdout/stderr to those sinks when supplied.
- Maintain a bounded ring buffer or bounded prefix-plus-tail buffer for `CommandResult`.
- Expose whether each stream was truncated and, when applicable, its durable path.
- Let `HandoffOrchestratorService` allocate the task log destination and archive it with the task.

Do not put model/provider behavior into `CommandRunner`.

### Required tests

- Output below the limit is returned unchanged.
- Output exactly at the limit is not marked truncated.
- Output one byte over the limit is marked truncated.
- A large stream retains its final marker.
- A large stream is written completely to its configured file.
- UTF-8 text crossing the buffer boundary remains valid.
- Stdout and stderr are independently bounded and persisted.
- Tee/sink write failure becomes a clear command failure.
- Timeout still terminates the parent and descendants.
- Interactive output occurs only when explicitly requested.

## Work package 2: strict Git semantics in the handoff orchestrator

### Current problems

- Any nonzero `git show-ref` result is treated as a missing branch.
- `git status --porcelain` exit status is ignored.
- Failure-report `git add` and `git commit` results are ignored.
- An archive commit failure is returned as `Outcome.success`.
- Inbox-wide `git add -A` can stage unrelated files.

### Required behavior

1. Centralize Git exit checking in one small helper.
2. For `show-ref`, interpret only exit code `1` as "not found". Exit code `0` means found; every other result is an operational failure.
3. A nonzero `git status` must fail the task. It must never mean "no changes."
4. Check every `git add`, `commit`, `pull`, and `push` result.
5. Stage exact inbox paths: the task/archive/result/failure-report files involved in the current operation. Do not stage unrelated inbox changes.
6. Staging all changes inside a freshly isolated agent worktree is acceptable because that worktree belongs to one task.
7. Do not return `success` when the task archive could not be committed. Introduce an explicit `partialFailure`/`archivePending` outcome, or return failure with sufficient recovery information.
8. Preserve an agent worktree whenever removing it could destroy uncommitted work.
9. Every partial state must report the exact repository, branch, task file, worktree path, failed command, exit code, and stderr tail needed for recovery.

### Required tests

- `show-ref` exit 0, 1, and 128 follow three distinct paths.
- Failed `git status` cannot produce a no-change success.
- Failed inbox add, commit, and push are each reported accurately.
- Archive commit failure is not success.
- Unrelated inbox files are not staged.
- Worktree commit failure preserves the worktree.
- One failed task does not abort processing of later eligible tasks.

## Work package 3: provider session identity and continuity

### Current problem

The service can append by provider session ID, but a newly started CLI session usually does not return an ID through ChatMap's normal prompt flow. The base parser merely echoes a caller-supplied session ID. Claude extracts IDs only from structured output; Codex and Antigravity do not extract newly created IDs. `RunPromptCli` does not display the resulting ID.

### Required behavior

1. Each session-capable provider must own the documented structured-output invocation and parser for its installed CLI.
2. Verify flags against the actual installed CLI version or official documentation before hardcoding them.
3. Starting without a session ID must capture the provider-created ID when the provider exposes one.
4. Resuming with that ID must use the provider's exact syntax.
5. `PromptService` must append subsequent turns to the chat identified by provider/target/session.
6. `RunPromptCli` must print provider ID, target ID, model name, and session ID when present.
7. Parsed structured output must return clean assistant text, not raw event JSON, to ordinary prompt callers.
8. Providers that genuinely do not support sessions, such as the current one-shot Ollama CLI path, must declare that capability honestly.

### Provider notes

- Claude: structured JSON/JSONL can expose `session_id`; parse it without returning raw JSON as the assistant answer.
- Codex: use its documented `codex exec`/resume and structured-event form; capture the thread/session identifier from the appropriate event.
- Antigravity: verify the installed CLI's conversation and structured-output contract before implementing extraction.

### Required tests

- Start without ID -> provider returns ID -> result exposes ID.
- Resume using returned ID -> correct command syntax.
- Two turns with one ID append to one database chat.
- Different provider or target with the same textual session ID does not collide.
- Structured provider events become clean assistant text.
- `RunPromptCli` prints the reusable ID.

## Work package 4: WSL-safe executable selection

### Current problem

`CodexCliProvider` hardcodes `codex.cmd`, which is Windows-specific and fails in Ubuntu/WSL.

### Required behavior

- Preserve normal PATH resolution.
- Use `codex.cmd` on Windows and `codex` on Unix/WSL, or inject the executable name from bootstrap code.
- Do not add a configuration file.
- Keep executable selection separate from model selection.
- Apply the same explicit portability review to other `.cmd` assumptions.

### Required tests

- Windows platform selection produces `codex.cmd`.
- Unix/WSL selection produces `codex`.
- Codex still constructs `exec`, resume, model, sandbox, and stdin arguments correctly.

## Work package 5: persist provider and model provenance

### Current problem

`AiResponse` and `PromptResult` carry provider/target/model data, but generated database chats retain mainly `Source`. Multiple Ollama targets sharing `Source.ollamaPrompt` become indistinguishable.

### Required behavior

Persist stable nullable provenance for generated prompts:

- provider ID
- model-target ID
- provider model name
- provider session ID

An additive migration is required. Do not rewrite or invalidate existing imported chats. Existing rows may remain null where provenance cannot be reconstructed reliably.

The implementation may place these columns on generated chats or introduce a small prompt-invocation table. Prefer the smallest design that correctly represents one generated conversation and its turns. Do not create one `Source` enum value per model.

Update content hashing and refresh behavior only if these fields are semantically part of transcript identity; document and test the choice.

### Required tests

- Claude, Codex, Antigravity, and Ollama generated chats persist correct provenance.
- Two Ollama model targets remain distinguishable.
- Existing databases migrate without data loss.
- Existing imported chats remain readable.
- Session continuation preserves provenance.

## Work package 6: remove false response metadata and legacy duplication

### Required changes

1. Remove the three-argument `AiResponse` constructor that silently assigns `ProviderId.claudeCli`.
2. Migrate every caller to provide a `ModelTarget` or explicit provider/target/model values.
3. Search for remaining production uses of the legacy `AiBackend` map and anonymous target adapters.
4. Remove compatibility surfaces only after callers are migrated and tests prove the behavior. Do not perform a broad speculative rewrite.
5. Keep capability validation in one authoritative place. Avoid duplicated service-layer and CLI-base checks that can drift.

### Required tests

- No response can be constructed without accurate provider/target/model identity.
- Test doubles must supply explicit identity rather than defaulting to Claude.
- Capability rejection remains provider-specific and deterministic.

## Work package 7: session listing

### Current problem

`claude --list-sessions` is not documented in the current public Claude CLI reference. The restored `StandardCliBackend` makes this worse by issuing `--list-sessions` for every binary and swallowing failures.

### Required behavior

- Remove session listing from `StandardCliBackend` by deleting that class.
- Do not assume a universal provider session-list command.
- Prefer listing sessions already known to ChatMap from persisted provenance.
- If a provider-specific live discovery command is retained, verify it against the installed CLI and distinguish zero sessions from operational failure.

### Required tests

- Database-backed session listing returns distinct known sessions for the selected provider/target.
- Zero known sessions is distinct from provider-discovery failure.
- No universal `--list-sessions` command is constructed for Codex, Antigravity, or Ollama.

## Work package 8: ChatGPT Retry-After edge cases

### Current problem

`Double.parseDouble` accepts `NaN`; converting it to `long` yields zero, so `Retry-After: NaN` can become an immediate retry.

### Required behavior

- Parse delta-seconds as a nonnegative integer, preferably with `Long.parseLong`.
- Continue supporting a valid RFC 1123 HTTP date if required by the current endpoint.
- Reject NaN, infinity, fractions, negative values, overflow, malformed dates, and unreasonable delays.
- Retain the bounded fallback backoff.

### Required tests

- Valid integer delta-seconds.
- Valid HTTP date with a controllable clock if practical.
- NaN, infinity, fraction, negative, overflow, malformed value, past date, and excessive future date.

## Work package 9: expand Ollama targets after correctness work

Do this after provenance and command/session correctness are complete.

- Verify installed models using `ollama list`.
- Add actual supported targets to `ModelTarget`, for example the installed Qwen and GLM names.
- All targets must share `OllamaCliProvider` or a future single Ollama HTTP provider.
- Do not add model subclasses.
- Persist the exact provider model name.
- If moving to Ollama HTTP, keep transport-specific behavior inside the Ollama provider and use ChatMap-managed history rather than pretending Ollama CLI invocations provide resumable sessions.

Required test: two Ollama targets route through the same provider while producing distinct commands and persisted provenance.

## Recommended implementation order

1. Delete `StandardCliBackend`.
2. Replace prefix-only command truncation with durable file-backed output plus bounded tail capture.
3. Make every Git operation's result explicit and correct.
4. Fix platform-neutral executable selection.
5. Remove false `AiResponse` metadata defaults.
6. Persist provider/target/model/session provenance.
7. Implement and test provider-specific session extraction and continuation.
8. Replace universal/live session listing with database-backed listing where practical.
9. Harden `Retry-After` parsing.
10. Add verified Ollama Qwen/GLM targets.

Commit in small, reviewable units. Run the complete test suite after every work package.

## Verification commands

Use the repository's Gradle wrapper as authoritative:

```bash
./gradlew clean test
./gradlew check
```

Also run focused manual smoke tests on both Windows and WSL where applicable:

```text
runPrompt claude <prompt>
runPrompt codex <prompt>
runPrompt agy <prompt>
runPrompt ollama <prompt>
```

Verify that each successful session-capable run prints a reusable session ID and that a second invocation appends to the same ChatMap conversation.

For the handoff orchestrator, use a disposable test repository and confirm:

- complete agent output survives in a durable file;
- the in-memory diagnostic contains the final marker;
- a successful task commits only intended paths;
- injected Git failures produce failure or partial-failure outcomes;
- no failed operation is reported as success;
- uncommitted agent work is never destroyed.

## Completion criteria

This handoff is complete only when:

- `StandardCliBackend.java` is gone.
- Provider-specific classes are the only CLI model execution implementations.
- Long agent output is fully durable and bounded in memory.
- Git failures cannot be mistaken for absence, no changes, or success.
- Codex runs from both Windows and WSL.
- New provider sessions are captured, displayed, persisted, and resumable.
- Generated chats preserve accurate provider, target, model, and session provenance.
- No constructor silently labels non-Claude responses as Claude.
- Session listing does not depend on a universal undocumented CLI command.
- Retry-After parsing rejects non-finite and malformed values.
- At least two verified Ollama model targets share one provider and remain distinguishable.
- The complete Gradle test/check suite and platform smoke tests pass.

## Scope boundary

Do not install a system service or daemon as part of this handoff. First make one manual orchestrator run reliable, recoverable, and observable. Service installation should be a separate small handoff after these completion criteria are met.
