# ChatMap — Structural Design Audit

**Date:** 2026-08-15
**Repo head audited:** `ee639ca` (current `master` is a few commits ahead; nothing since touches the areas below)
**Scope:** 4 targeted audits, run in parallel, cross-checked against each other
**Findings:** 19 total

## What this audit is

Four independent readings of the codebase, each pointed at a different
structural question, then synthesized by hand. The through-line:
`HandoffOrchestratorService` — built fast, and correct as of the last
bug-fix pass — keeps surfacing as the place where the codebase's existing
abstractions were bypassed rather than extended.

- **Root cause, not symptom.** The AI-backend duplication (§1) traces to
  one cause: the `AiRequest`/`AiBackend` port was shaped only around
  `--resume`. Fix the port, and the bypass becomes unnecessary.
- **Smaller than it looked.** Session-identity fragmentation (§2) sounded
  like new plumbing. It isn't — the exact reuse mechanism already exists
  and is already used on the import side. `PromptService` just never
  calls it.
- **The safety net has a shape.** `ArchitectureBoundaryTest` (§3) is a
  literal string-match on import lines. It catches the boundaries it was
  written to catch, and nothing shaped differently — which is exactly how
  infrastructure-flavored code ended up inside the application layer
  without tripping it.
- **One backlog item, already solved.** The pending "shared CliRunner
  helper" task (§4) turned out to already exist as `CliBootstrap`. One CLI
  class was never migrated to it.

---

## §1 Agent invocation: two command builders for the same CLIs

`StandardCliBackend` and `HandoffOrchestratorService.agentCommand()` both
build shell invocations for `claude`/`codex`/`agy` — independently, and
already diverged.

**[Critical] `HandoffOrchestratorService.java:258–264`**
`agentCommand()` bypasses the `AiBackend` abstraction entirely,
hand-building `<agent> -p [flags]` and calling `CommandExecutor` directly
instead of going through `ClaudeCliBackend`/`CodexCliBackend`/`AgyCliBackend`.

> Fix: extend `AiRequest` with the flags this caller needs (permission
> mode, output format) so there's one command-building implementation, not
> two hand-maintained ones.

**[Critical] `StandardCliBackend.java:68–83`**
`listSessions()` catches every `RuntimeException` — CLI missing,
unauthenticated, non-zero exit, timeout — and returns `List.of()`,
indistinguishable from "this agent truly has no sessions."

> Fix: separate "zero sessions" from "could not query sessions" with a
> result type or a distinguishable exception; let `PromptService` surface
> the latter instead of swallowing it.

**[Root cause] `StandardCliBackend.java:104–110`**
The port itself is the reason for the bypass above: `AiRequest`/`AiBackend`
only models the `--resume` use case, so it genuinely can't express what the
orchestrator needs.

> Fix: a small provider-agnostic capability set on `AiRequest` — session
> id, permission/sandbox mode, output format, system-prompt support — that
> each `commandFor()` interprets in its own CLI's flag names. Once this
> exists, the orchestrator becomes a caller of `AiBackend`, not a second
> implementer.

**[Caution] `OllamaCliBackend.java:24–91`**
Reimplements roughly 90% of `StandardCliBackend.askWithResult()` verbatim
— timeout/exit-code handling, error messaging — purely because its command
shape and lack of `--list-sessions` differ.

> Fix: pull the execution plumbing into a shared base both classes
> delegate to; leave only `commandFor()`/`listSessions()` as per-provider
> overrides.

**[Caution] `ClaudeCliBackend` / `CodexCliBackend` / `AgyCliBackend`**
Three ~15-line subclasses differing only in binary name and `Source`
value — unused generality, and their constructor surfaces have already
drifted from each other.

> Fix: collapse to one `StandardCliBackend` with named static factories in
> `DefaultAiBackends`.

---

## §2 Conversation identity: the fix is smaller than the handoff assumed

Every `PromptService.submit()` call creates a new chat, even across the
same provider session — but the machinery to fix this already exists
elsewhere in the codebase.

**[Confirmed] `AiResponse.java:6–9`**
No backend — claude, codex, agy, or ollama — ever returns a session id;
`AiResponse` has exactly three fields (`text`, `backendId`, `duration`). A
session id only ever flows *into* a request via `--resume`, never back out.

**[Confirmed] `PromptService.java:124–134`**
`recordInDatabase` always constructs `new Chat(0L, null, source, …)` —
external identity `null`, every time — so `ImportService` falls into
content-hash dedup, which never matches because the prompt/response text
differs each turn. N turns in one `--resume` session become N separate
chats.

**[Already built] `ChatRepository.java:201–219` · `ImportService.java:114–147`**
`findByExternalIdentity` / `persistWithExternalIdentity` already implement
"look up by `(source, externalConversationId)`, append if found" — and
`LocalCliHistoryProvider` already uses exactly this pattern for imports,
keying on the CLI tool's own session file id.

> Fix: when `sessionId` is non-blank, build the `Chat` with it as external
> identity and route through the existing lookup — this alone fixes the
> dedup. Decide append-vs-replace message semantics explicitly; the import
> path currently assumes "replace all messages," which isn't right for a
> live conversation. When no session id is passed, one-chat-per-call stays
> correct — don't invent session tracking ChatMap wasn't told about.

---

## §3 Boundary discipline: what the test can and can't see

`ArchitectureBoundaryTest` is a literal `String.contains("import chatmap.…")`
scan — five checks, nothing deeper. It's precise about what it enforces and
blind to everything shaped differently.

- `chatmap.domain` — zero deps, enforced
- `chatmap.application.service` — `HandoffOrchestratorService` does raw
  filesystem I/O and threads a process-shaped `CommandResult` through its
  API here
- `chatmap.infrastructure.*` — implements application ports
- `chatmap.presentation.{cli,ui}` — no infra imports, enforced

The boundary test checks import statements between these layers.
`java.nio.file.Files` and `CommandResult` (an application-layer type)
aren't infrastructure imports, so infrastructure-shaped work sitting in the
application layer is invisible to it.

**[High] `HandoffOrchestratorService.java` — throughout**
Direct filesystem I/O (`Files.list`/`read`/`write`/`move`/
`createDirectories`/`createTempDirectory`, recursive delete) runs
throughout an application-service class instead of behind a port.

> Fix: extract a small filesystem port the same way command execution
> already has one.

**[Medium] `HandoffOrchestratorService.java:79, 83–92`**
Constructor takes a raw `Map<String, Path> projectRegistry` — presentation
and application communicate through a generic collection instead of a
defined contract.

**[Medium] `CommandResult.java:6`**
Bakes process-shaped concepts (`exitCode`, `stdout`, `stderr`, `timedOut`)
directly into an application port, then threads it through service method
signatures extensively — an infra-flavored DTO leaking through the port's
public surface.

**[Medium] `ChatConsolidatorCli.java:113–222`**
Implements real business rules — file-type/heuristic classification of
what counts as a "chat file," directory-tree walking — inside a
presentation-layer CLI class. Zero infra imports, so it's undetected, but
it's exactly the logic an application service should own.

**[Low] `ChatConsolidatorCli.java:76–96` · `HandoffOrchestratorCli.java:21–23`**
A fat-controller `main` orchestrating a multi-step workflow directly, and a
CLI that (correctly, deliberately) skips `CliBootstrap` but ends up parsing
config close to what a composition-root class should do.

---

## §4 CLI entry points & provider consistency

Two smaller areas: whether the nine CLI classes share boilerplate
consistently, and whether the six `ChatProvider` implementations agree on
how they signal trouble.

**[Medium] `RunPromptCli.java:16–34`**
The one CLI that never migrated to `CliBootstrap.parseOrExit` — it
hand-rolls the exact catch/print/exit shape that helper exists to absorb,
while the other five `CHATMAP_HOME`-routed CLIs already use it.

> Note: this closes out the long-pending "shared CliRunner helper" backlog
> item — the helper already exists as `CliBootstrap`; only this one
> migration was missed. Rescope that task to just this fix.

**[Low] `RunPromptCli.java:67, 84–85`**
The usage string is written out literally twice instead of hoisted to one
constant, the pattern every other CLI already follows.

**[Medium] `ImportAllChatsCli.java:79`**
CLI-history providers throw `ChatProviderException` from `listChats()` on
I/O failure; web providers never do — they report unavailability through a
diagnostic string instead, throwing only from `fetch()`. The result:
`ImportAllChatsCli`'s catch around `listChats()` is effectively dead code
for all three web providers.

> Fix: pick one signal — either have web providers throw for the
> unreachable case too, or explicitly document that unavailability is
> diagnostic-only for web sources.

**[Clean] `CdpWebChatProvider.java` · `LocalCliHistoryProvider.java`**
No cross-copy duplication found across the six leaf provider classes —
discovery-result building and completeness reporting are already properly
centralized in the shared bases from the earlier "extract shared base for
CLI-history provider triplet" work.

---

## Recommended order

Grouped by effort-to-value, not by section number.

### Tier 1 — small, self-contained, machinery already exists
1. Wire `PromptService` session continuity through the existing
   identity-lookup path. (§2)
2. Migrate `RunPromptCli` to `CliBootstrap.parseOrExit`; dedupe its usage
   string. (§4)
3. Replace the `/*hack*/ true` auto-push assignment with an honest, tested
   default of `true` that still honors an explicit config override. —
   **note:** already resolved as of current `master`; verify before
   re-doing.

### Tier 2 — real design work, fixes the root cause not the symptom
4. Extend `AiRequest`/`AiBackend` with a provider-agnostic capability set
   (session id, permission mode, output format); make
   `HandoffOrchestratorService` a caller of it instead of a second
   implementer. (§1)
5. While touching that port, fix `listSessions()`'s swallowed-exception
   problem in the same pass. (§1)

### Tier 3 — cleanup, lower urgency, no active risk
6. Collapse the three near-empty CLI backend subclasses into factory
   methods. (§1)
7. Merge `OllamaCliBackend`'s duplicated execution logic into the shared
   base. (§1)
8. Extract `HandoffOrchestratorService`'s filesystem I/O behind a port;
   stop threading `CommandResult` and a raw `Map<String,Path>` through its
   public surface. (§3)
9. Move `ChatConsolidatorCli`'s file-classification logic into an
   application service. (§3)
10. Decide and document the `ChatProviderException` contract consistently
    across CLI-history and web providers. (§4)

---

## Note on supersession

This audit supersedes the Git-lifecycle framing in the prior
`ChatMap project handoff — 2026-08-13.md` and the current-state handoff
that followed it. The Git exit-code/worktree-safety work those documents
flagged as the top priority was substantially completed in `ee639ca`
before this audit's repo head — see that commit for what's already fixed
and what (worktree preservation on failed commit) is not.

**Methodology:** four Explore-agent audits run in parallel, each scoped to
one architectural question and given known context from the prior code
review and both recent handoffs, then synthesized by hand. No code was
changed as part of this audit.
