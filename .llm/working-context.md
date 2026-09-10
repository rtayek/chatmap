---
id: CM-CTX-01
lifecycle: working
status: active
provenance: git-history
---
# ChatMap Working Context

**Updated:** 2026-09-10
**Authority:** current operational state; update or replace this file as work changes

## Purpose

ChatMap imports, preserves, searches, organizes, and exports conversations. Its
long-term purpose is to produce durable semantic knowledge from those
conversations and keep that knowledge current while retaining provenance and
history.

## Current State

- Gemini Workspace Takeout directory import is implemented through
  `importGeminiTakeout`, using the existing transactional import service and
  a separate `geminiTakeoutJson` source. One real 16-turn conversation passed
  isolated import/reimport and raw-turn preservation checks; the full Gradle
  quality gate passed (618 tests, 4 skipped). Direct archive input and filename
  identity stability across separate exports remain unverified or unsupported.
  See `handoffs/handoff-chatmap-gemini-takeout-implementation-2026-09-10.md`.
- The deterministic Java/SQLite application supports multi-source acquisition,
  import, search, project/tag organization, Markdown export, optional LLM
  prompting, and handoff collection.
- The worker-lifecycle vertical slice is incorporated into ChatMap. It proves
  durable assignments, sessions, lifecycle events, artifacts, semantic
  handoffs, retirement, and successor chains. It does not prove semantic
  preservation across handoffs.
- `handoff.HandoffWatcher` is transport-only. It collects stable files; it does
  not interpret, route, commit, or update the database.
- The shell LLM relay is a completed external experiment in `rtayek/bin`, branch
  `archive/llm-relay`.
- An independent review of commit `f786093` found two remaining defects. Strict
  Ollama response validation was repaired in `031aba9`. Archive-staging failure
  propagation was repaired and regression-tested in `7832eec` and `a56d463`;
  a local full Gradle check passed after the repair.
- Caller-owned migration transactions are now protected by a JDBC savepoint.
  Commit `63a7ad6` rolls back only migration work on failure while leaving the
  caller's surrounding transaction under caller control.
- Project guidance and handoffs now live under `.llm/`, with `index.md` as the
  repository-controlled discovery registry. `CLAUDE.md` routes Claude through
  `AGENTS.md`; `AGENTS.md` routes agents through `.llm/index.md`; and the
  index routes them to human, persona, durable, working, and selected handoff
  documents. A fresh read-only discovery test in Codex, Claude Code, and
  Anti-Gravity produced substantive agreement on purpose, ownership,
  architectural boundaries, current work, undecided questions, and the
  `.chatmap-local/` exclusion. Anti-Gravity reported `AGENTS.md` first;
  Codex reported `.llm/index.md` before `AGENTS.md`; Claude did not report
  actual read order. Semantic discovery is validated, while exact automatic
  startup order is not fully proven for every client.
- Confirmed on a 2026-09-10 Claude Code cold start: the harness auto-injects
  `CLAUDE.md` and its `@AGENTS.md` reference, but does NOT automatically read
  `.llm/index.md` or the files it routes to. The AGENTS.md "MUST read
  `.llm/index.md` before doing anything" step is therefore not self-executing in
  Claude Code; the index was only read after a prompt. Candidate fix: a
  client-side startup hook (e.g. `.claude/settings.json` SessionStart) that reads
  the index, since repo Markdown alone cannot force the order.
- The bounded metadata pilot now separates Markdown bodies, document-local YAML,
  and repository-wide JSON rules. The read-only audit passed all required
  document, front-matter, unique-ID, allowed-value, UTF-8/no-BOM/LF, and
  manifest-consistency checks without changing the worktree. Three low-level
  design questions remain: duplicated document-path lists, unspecified scope
  for path and ID validation, and an unspecified front-matter format and
  enforcement procedure. The earlier speculative Agent OS proposal is preserved
  under `.llm/handoffs/archive/`; `evo.md` records only adopted ChatMap
  evolution.
- The full Gradle quality pipeline passed after the A2A merge. The consolidated
  A2A server and same-task continuation client also passed their runtime check.
  Live provider tests remain intentionally opt-in.
- The bounded A2A experiment proved Agent Card discovery, completed, failed,
  input-required, and same-task continuation behavior. Its source now lives in
  `chatmap.a2a.experiment` on `master`. A bounded recorder projects visible
  task snapshots, states, messages, history, and text artifacts into the existing
  worker-lifecycle ledger. The continuation client uses an isolated temporary
  ChatMap home; production data and the UI remain untouched. The read-only
  `workerLifecycleRecord` command successfully reopened that database and
  displayed the persisted assignment, session, four transitions, decision
  details, and three artifacts after the A2A client exited. The temporary
  A2A and worker-lifecycle worktrees and branches have been removed; the primary
  ChatMap worktree is the only active worktree.
- The model-backed A2A path was proven with local `qwen2.5:7b`. The server
  advertised an Ollama Agent Card and returned a completed `worker-result`
  artifact. The `a2aModelRecord` client then stored a real model task in an
  isolated lifecycle ledger. A separate process reopened session 1 and verified
  two lifecycle events plus the raw task snapshot and model-result artifacts.
- Manual semantic probes showed that the 7B model can obey a well-scoped
  request, but can also overstate causality, violate sentence structure, and
  reverse a supplied fact. The `a2aSemanticProbe` command then required one
  fixed four-field factual contract. Local `qwen2.5:7b` returned the exact
  ordered values with no extra prose, and Java accepted the response.

## Closed Work

- Shell LLM relay experiment: successful, tested, and archived.
- Worker-lifecycle experiment: successful and incorporated; no longer a
  separate project.
- Agent-facing Markdown pilot: completed. Keep client entry points simple;
  distinguish auto-discovered skills from ordinary operational Markdown.
- Agent-protocol survey and bounded A2A experiment: completed. A2A is the
  selected agent-to-agent wire protocol; MCP is complementary; ACP is absorbed
  into A2A; ANP is deferred.
- Project-memory discovery test: completed with substantive agreement across
  Codex, Claude Code, and Anti-Gravity.
- Metadata pilot audit: completed read-only with all checks passing and three
  low-level specification questions retained for later judgment.
- Independent code review: completed. Previously reported transaction,
  worktree-preservation, structured-output, process-reader, and platform-Codex
  defects no longer reproduced.
- Archive-staging failure propagation: repaired and regression-tested. A failed
  inbox `git add` now returns partial failure, preserves the archived artifacts
  for recovery, and does not attempt the archive commit.

## Active Agenda

1. Decide whether the metadata audit's three low-level findings justify a small
   deterministic validator. Do not expand the YAML or JSON schema without a
   concrete need.
2. Decide whether semantic evaluation should stop at the successful bounded
   structured probe or proceed to a small corpus of independently specified
   factual contracts.
3. Preserve the caller-chain escalation model: a worker returns an unresolved
   decision to its caller; each caller resolves it within its authority or
   propagates it upward. Verify whether the existing ledger records caller
   identity and decision provenance before proposing a schema change.
4. `A2aTaskRecorder` exists and is tested, but is only exercised by
   `ModelRecordingClient`, which records into an isolated temporary ChatMap
   home. No wiring exists from a real A2A exchange into the production
   home/database path, and nothing triggers recording automatically outside
   that experiment client. Decide whether to wire the existing recorder into
   the production path, or keep A2A recording bounded to the experiment for
   now.
5. Draft a Claude Code SessionStart hook in `.claude/settings.json` that reads
   `.llm/index.md` (and the files it routes to) at cold start, so the AGENTS.md
   "read the index before doing anything" step becomes self-executing instead of
   depending on a prompt. Config change: get Ray's go-ahead before editing
   settings.

## Deferred

- Handoff-watcher provenance, content-hash duplicate detection, explicit queue
  states, Git-checkout move-versus-copy behavior, and fetch/acknowledgement
  policy
- Semantic-preservation tests; existing lifecycle and soak tests validate
  durable structure rather than preservation of meaning
- Worker-lifecycle expansion such as mandatory handoffs before retirement,
  multiple sessions per assignment, and cross-worker queries
- A2A implementation beyond the bounded experiment
- a general scheduler, router, permissions framework, or agent harness
- full semantic-extraction implementation
- embeddings, semantic search, and broad UI redesign
- layer-boundary enforcement (e.g. domain cannot import infrastructure) via
  an ArchUnit test or Checkstyle ImportControl, in preference to a Gradle
  multi-project split, which Ray does not want

## Next Action

Review the metadata audit's three low-level findings and decide whether they
justify a small deterministic validator. Keep the decision evidence-based; do
not expand the YAML or JSON schema without a concrete need.
