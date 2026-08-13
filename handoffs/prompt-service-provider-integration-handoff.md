# Handoff: Harden and Complete PromptService Provider Integrations

## Context

ChatMap now contains a `PromptService`, several command-backed prompt backends,
a runnable prompt CLI, and a newly injected controller entry point. At the time
of this review, GitHub `master` was at commit
`927a0537ef57bce60ef68628479e2f63a0724d32` (`Inject PromptService into
ChatMapController`).

The controller injection is a reasonable seam, but the feature is not ready for
a JavaFX prompt UI yet. The provider command contracts and conversation
persistence need to be made reliable first.

Work directly in the existing ChatMap repository. Inspect the current branch
and current code before changing anything, because newer commits may exist.
Preserve all unrelated work. Do not commit or push; Ray will review first.

## Goal

Make prompt execution reliable across the supported providers and preserve a
continued provider conversation as one continued ChatMap conversation.

Do not redesign the entire storage layer and do not build the JavaFX prompt UI
in this pass.

## Primary concerns

### 1. Provider CLIs do not share one command grammar

`StandardCliBackend` currently treats Claude, Codex, and Antigravity as though
they all support approximately the same `-p`, `--resume`, and session-listing
syntax. They do not.

- Claude supports print mode and explicit resume operations.
- Codex noninteractive execution is based on `codex exec`; continuation uses
  the `codex exec resume` family of commands.
- Antigravity supports one-shot `-p`, while continuation is exposed through
  Antigravity-specific options such as `--continue` or `--conversation`.
- There is no reason to assume that all three support
  `<binary> --list-sessions`.

Official references used during review:

- Claude CLI: <https://docs.anthropic.com/en/docs/claude-code/cli-usage>
- Codex repository and CLI behavior: <https://github.com/openai/codex>
- Antigravity CLI: <https://codelabs.developers.google.com/sdd-agy-cli>

Treat the installed CLI's `--help` output as authoritative for the installed
version. Do not blindly copy command lines from this handoff.

Refactor so each provider owns its command construction, response parsing,
session resumption, and session discovery. A shared process runner is useful;
a shared provider command grammar is not.

Represent unsupported capabilities explicitly. For example, a backend should
be able to report whether it supports:

- one-shot prompting;
- durable conversations;
- resuming a known conversation;
- enumerating conversations or sessions;
- structured output suitable for extracting a durable conversation ID.

Move session discovery out of `PromptService`'s hardcoded backend-name switch.
Do not silently swallow discovery failures. Return an explicit unsupported or
failed result, or log enough information to diagnose the provider command.

### 2. Provider conversation identity is discarded

`PromptService` can send a provider session ID, but database recording creates
a new two-message ChatMap chat for every submitted prompt. It does not retain
the provider's durable conversation ID or append a follow-up turn to the
existing ChatMap chat.

Define and implement these semantics:

1. A newly started provider conversation returns or otherwise resolves its
   durable provider conversation ID when the provider supports one.
2. ChatMap stores that identity and can locate the same ChatMap chat later.
3. A follow-up in the same provider conversation appends the user and assistant
   messages atomically to that same ChatMap chat.
4. Repeating an already-recorded result does not duplicate messages.
5. A failed provider call or failed database write does not leave a partial
   turn.
6. `PromptResult` returns enough identity information for the CLI and eventual
   UI to continue the conversation and select the corresponding ChatMap chat.

Pay special attention to reconciliation with the existing CLI-history
importers. A conversation created through `PromptService` must not later be
duplicated when the Claude Code, Codex, or Antigravity history inventory sees
the same provider session. Prefer one canonical provider identity. If prompt
provenance must be retained, store it separately rather than defeating identity
deduplication with two incompatible source values.

Use the existing `TransactionRunner` for database changes. Preserve the rule
that repositories do not own or close caller-supplied connections.

### 3. The default backend set mixes incompatible responsibilities

Claude, Codex, Antigravity, Ollama, and JShell currently appear together as
though they were interchangeable AI prompt providers.

- Ollama needs an explicit/configurable model rather than a silently fixed
  model being treated as a universal default.
- JShell executes local Java code and is not an LLM provider. Do not expose it
  as an ordinary user-selectable LLM backend. Keep it as an explicitly enabled
  harness/tool if it is still useful.
- Tool use, working-directory behavior, permissions, structured output, and
  conversation continuation differ between providers and should be expressed
  as capabilities rather than inferred from backend IDs.

Avoid adding another large framework or dependency. Existing process execution,
JSON support, storage, and transaction facilities should be sufficient.

## Recommended work sequence

1. Read the project instruction files and inspect the current Git state.
2. Run the existing tests and record the baseline.
3. Inspect the locally installed `claude`, `codex`, and `agy` help output without
   starting destructive or billable agent work.
4. Add provider-specific command construction and capability tests.
5. Replace hardcoded session discovery in `PromptService` with backend-owned
   behavior.
6. Implement durable conversation identity and atomic continuation in storage.
7. Add persistence and reconciliation tests.
8. Exercise each installed provider with the smallest safe opt-in smoke test.
   Do not make live provider calls part of the ordinary unit-test suite.
9. Leave the controller injection in place, but defer JavaFX controls until
   these backend and persistence guarantees hold.

## Required tests

At minimum, cover:

- exact command construction for fresh and resumed Claude prompts;
- exact command construction for fresh and resumed Codex prompts;
- exact supported Antigravity behavior and explicit rejection of unsupported
  behavior;
- session enumeration for providers that support it;
- clear unsupported results for providers that do not;
- provider errors, timeouts, malformed structured output, and empty responses;
- first turn creates one ChatMap chat;
- second turn with the same provider identity reuses that chat and appends two
  messages;
- repeated recording is idempotent;
- database failure rolls back the complete turn;
- later CLI-history import does not create a duplicate conversation;
- JShell is not included in the ordinary default LLM selection;
- Ollama model selection is explicit or configured.

Unit tests must use fakes or deterministic command-construction seams. Keep
live CLI tests opt-in and clearly labeled.

## Acceptance criteria

- No generic class assumes Claude, Codex, and Antigravity use the same CLI
  flags.
- `PromptService` contains no backend-name switch for session discovery.
- Unsupported session operations are explicit and diagnosable.
- A provider's durable conversation maps to one ChatMap chat across follow-up
  turns and later history reconciliation.
- Prompt recording is transactional and idempotent.
- The ordinary backend list contains LLM providers, not the JShell execution
  harness.
- Existing imports, search, summaries, and conversation reconciliation remain
  behaviorally unchanged.
- `./gradlew test` passes. Also run `./gradlew check` if it passes at baseline;
  otherwise report the pre-existing failure separately.
- `git diff --check` passes.
- No commit or push is made.

## Final report

Report:

- the installed CLI versions and verified command contracts;
- the architectural changes made;
- the conversation identity and update rules now guaranteed;
- tests added and complete test counts;
- any provider capability that remains unsupported;
- all changed files;
- final Git status;
- any live provider calls made and whether they could incur usage charges.
