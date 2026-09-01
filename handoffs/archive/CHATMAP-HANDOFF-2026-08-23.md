# ChatMap Project Handoff

**Prepared:** 2026-08-23  
**Repository:** <https://github.com/rtayek/chatmap>  
**Purpose:** Resume ChatMap development in a fresh chat without carrying the full conversation history.

## First action in the new chat

Fetch or inspect the current remote `master` branch and CI status before relying on the code-review findings below. The last revision reviewed in this chat was:

```text
2f034bda781481d98e5b041ee889c21c9f2cdee3
```

Ray may have committed additional changes since that review. Treat every finding as **last-known state**, not proof that the defect still exists.

## Project direction

ChatMap is a Java desktop application for aggregating, importing, indexing, searching, and eventually summarizing conversations from multiple LLM providers and command-line tools. It uses SQLite/FTS5 and is intended to support both cloud providers and local models such as Ollama/Qwen.

The broader operational goal is to accept handoff or job files through an incoming GitHub repository, pull them to the PC, execute the requested work, and preserve the results. This may eventually run as a daemon or background service. A simple manually invoked process is acceptable initially.

The incoming repository may contain a `.tests/` folder with Markdown test cases or templates. The runner should be able to copy those files into the relevant workspace and run a repeatable set of tests on demand. Do not overbuild the daemon before the manual workflow and failure handling are reliable.

## Fixed backend architecture decisions

Use **provider** terminology consistently.

- `ModelTarget` is a curated enum representing selectable model targets.
- `ProviderId` is an enum identifying providers.
- Use an `EnumMap` or similarly direct map from provider identifiers to provider implementations.
- Do not introduce configuration files for model/backend setup yet.
- Do not create a registry framework merely to replace a straightforward map.
- Keep provider and protocol classes shallow and focused.
- Do not create one subclass per model.
- Multiple Ollama model targets should share one Ollama provider implementation.
- Subclassing is useful only when implementations have genuinely different behavior or protocols; it should not encode ordinary model data.
- The current enum-and-map approach is deliberately simple and is expected to remain adequate while the supported target set is curated in code.

This structure should allow expansion from six models to many without turning the central orchestration code into a large conditional statement.

## Provider direction

- Local Ollama models such as Qwen should use the Ollama HTTP API through a shared `OllamaProvider`.
- CLI-based providers remain valid where a provider is naturally accessed through a CLI.
- Provider implementations should hide transport details from application services.
- Model selection belongs in `ModelTarget`; transport and execution behavior belong in the provider.

## Last reviewed changes

The last review covered the merge of PR #2, “Feature/provider terminology,” at commit `2f034bd`. The notable commits were:

- `296b410` — Align LLM provider terminology.
- `59aa9de` — Split `HandoffOrchestratorService` responsibilities into `GitWorkspaceManager` and `HandoffInboxManager`.
- `65298b5` — Make `ChatMapRuntime` delegate composition to `ApplicationBootstrap`.
- `2f034bd` — Merge PR #2.

Positive changes observed:

- The provider terminology is clearer and more consistent.
- Ollama uses HTTP rather than shelling out to a local executable.
- Splitting Git workspace and inbox responsibilities improved cohesion.
- `ChatMapRuntime` now delegates composition to `ApplicationBootstrap`.
- `DefaultLlmProviders` expresses the default provider wiring more clearly.

## Last-known code-review findings

### 1. Blocker: CI was failing

The PR #2 CI run failed with 480 tests executed, 4 failed, and 4 skipped:

- `HandoffOrchestratorServiceScenarioTest`, around line 252.
- `OtherCliBackendsTest`, around lines 45, 60, and 109.

The tests hard-coded `codex.cmd`, while `CodexCliProvider` correctly selects `codex` on Linux. Update the tests to express platform-aware behavior instead of assuming the Windows executable name.

CI run last inspected:

<https://github.com/rtayek/chatmap/actions/runs/32084826015>

Also consider protecting `master` so a pull request with failing required checks cannot be merged.

### 2. High: Dirty worktree contents may be destroyed

`HandoffOrchestratorService` preserves a worktree only when commit fails. Other failures after an agent may have edited files—including agent nonzero exit, timeout, Git status failure, Git add failure, or another pre-commit error—can reach `finally`, where the worktree is forcibly removed.

`GitWorkspaceManager.removeWorktree` also appeared to ignore the result of Git worktree removal and then delete the directory directly.

Required behavior:

- Never destroy a worktree that may contain uncommitted agent changes.
- Determine cleanliness before cleanup.
- Preserve the worktree on any uncertain or failed post-edit path.
- Report its location and recovery instructions.
- Test agent failure, timeout, Git failure, commit failure, and cleanup failure explicitly.

One existing test appeared to expect cleanup after agent failure. That expectation should be reconsidered because it conflicts with preserving potentially valuable edits.

### 3. High: Database migrations are not transactional

`Database.applyMigrations` performs destructive duplicate merges and other migration steps without a transaction. Autocommit may leave a partially migrated database after failure.

Required behavior:

- Run a migration as one transaction.
- Roll back every step on failure.
- Record the schema version only after successful completion.
- Add a fault-injection test proving that data and schema version remain unchanged after a mid-migration failure.

### 4. Medium: Archive staging failure may be ignored

`HandoffOrchestratorService` called `gitManager.gitAddPaths` for archive-related paths but discarded the returned result before attempting the commit.

Required behavior:

- Check every Git operation result.
- Do not commit or report success after staging fails.
- Preserve the worktree and return a useful diagnostic.

### 5. Medium: Structured CLI output can accept an empty answer

`StructuredCliOutput` may treat syntactically valid JSON as success even when it contains no recognized assistant text, returning an empty string.

Required behavior:

- Reject valid-but-unrecognized or empty structured output.
- Include enough diagnostic context to identify the unsupported response shape.
- Add tests for empty content, unknown fields, arrays without assistant text, and malformed JSON.

### 6. Medium: Ollama response parsing is fragile

The new Ollama HTTP parser assumes `message.content` exists, is non-null, and is a primitive string. A malformed HTTP 200 response can therefore throw an uncaught runtime exception.

Required tests and behavior:

- Non-2xx HTTP response.
- Malformed JSON.
- Missing or null `message`.
- Missing, null, or non-string `content`.
- Timeout/interruption.
- Unsupported request shape.
- Convert these cases into the provider's normal failure representation rather than leaking parser exceptions.

### 7. Medium: Process output reader failure can masquerade as timeout

`ProcessRunner` appeared to observe output-reader failures only after waiting for the process. If an output sink fails, the reader may stop draining the pipe, the child may block, and the process may eventually be reported as a timeout instead of an I/O failure.

Required behavior:

- Observe reader failure while the process is running.
- Terminate the child promptly when output capture fails.
- Preserve the original reader/sink exception.
- Add a test using a deliberately failing output sink and a child that writes enough data to fill the pipe.

## Suggested repair order

1. Fetch current `master`; identify which findings still reproduce.
2. Make CI green by fixing platform-specific Codex executable expectations.
3. Protect all potentially dirty worktrees on every failure path.
4. Make database migrations atomic and add rollback tests.
5. Stop ignoring Git staging failures.
6. Harden structured CLI and Ollama response parsing.
7. Fix concurrent process-output failure handling.
8. Run the full Gradle test suite on Linux and, when practical, Windows.
9. Commit and push only after the worktree is clean and CI passes.

## Testing approach

Ray treats tests as the functional specification. Prefer behavior-oriented tests with expressive names and minimal explanatory comments.

Important scenario coverage:

- Windows versus Linux CLI executable selection.
- Every orchestration failure point after a worktree may have changed.
- Worktree preservation and recovery information.
- Migration rollback after an injected failure.
- Git command failure propagation.
- Empty, malformed, and unexpected provider responses.
- Process timeout versus output-reader failure.
- Ollama HTTP success, protocol errors, parsing errors, and timeout.

The Gradle command line is authoritative. Avoid introducing Buildship-dependent behavior. Use the standard Gradle `build/` directory.

## Working preferences

- Java-first; avoid Python except where genuinely needed for ML tooling.
- Code and tests are documentation.
- Prefer expressive names and minimal comments.
- Prefer package-private visibility unless a public boundary requires more.
- Keep designs simple, deterministic, testable, and modular.
- Avoid configuration frameworks until real variability requires them.
- Use concise handoffs and semantic compression rather than preserving entire chat transcripts.
- Canonical durable project documents are `architecture.md`, `design.md`, `patterns.md`, and `working-context.md`.

## Conversation and checkpoint workflow

This long chat is being retired. For active development, use a fresh chat and a rolling `working-context.md` containing:

- Current branch and commit.
- Meaningful changes since the previous checkpoint.
- Tests run and failures.
- Unresolved findings and decisions.
- The next one or two actions.

A **Daily Project Checkpoint** scheduled task was created in this old chat. It produces a concise daily checkpoint but does not commit, push, or modify code. Because it is attached to this chat, it should eventually be recreated in the new project chat and the old task should then be paused or deleted.

A one-time reminder was scheduled for one week after 2026-08-23 to move or recreate that daily checkpoint.

## Suggested opening request for the new chat

> Continue the ChatMap project using the attached handoff. First inspect the current GitHub `master` branch and current CI results. Compare the current code against every last-known review finding in the handoff, report only findings that still reproduce, and identify any new regressions. Do not modify code until I ask.

