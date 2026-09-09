---
id: CM-CTX-01
lifecycle: working
status: active
provenance: git-history
---
# ChatMap Working Context

**Updated:** 2026-09-09
**Authority:** current operational state; update or replace this file as work changes

## Purpose

ChatMap imports, preserves, searches, organizes, and exports conversations. Its
long-term purpose is to produce durable semantic knowledge from those
conversations and keep that knowledge current while retaining provenance and
history.

## Current State

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
  Ollama response validation was repaired in `031aba9`. The ignored Git outcome
  when staging archived handoff artifacts remains a low-severity follow-up.
- Caller-owned migration transactions are now protected by a JDBC savepoint.
  Commit `63a7ad6` rolls back only migration work on failure while leaving the
  caller's surrounding transaction under caller control.
- Project guidance and handoffs now live under `.llm/`, with `index.md` as the
  repository-controlled discovery registry. A destructive context-layout trial
  was reversed without changing Java code, tests, database schema, or handoffs.
  Fresh read-only sessions in Codex, Claude Code, and Anti-Gravity all recovered
  the purpose, coordination boundary, A2A result, semantic-probe limit, active
  agenda, and local-data exclusion from the restored documents.
- The bounded metadata pilot now separates Markdown bodies, document-local YAML,
  and repository-wide JSON rules. The earlier speculative Agent OS proposal is
  preserved under `.llm/handoffs/archive/`; `evo.md` now records only adopted
  ChatMap evolution. A green Gradle check followed the initial metadata changes.
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
- Independent code review: completed. Previously reported transaction,
  worktree-preservation, structured-output, process-reader, and platform-Codex
  defects no longer reproduced.

## Active Agenda

1. Check and propagate failure from the inbox-repository `git add` that stages
   archived tasks, result files, and agent logs. A staging failure must not be
   reported as success.
2. Run a read-only metadata audit comparing the six pilot documents with
   `manifest.json`. Verify unique IDs, allowed lifecycle and status values,
   required paths, UTF-8 without BOM, and LF line endings. Do not change client
   entry points.
3. Decide whether semantic evaluation should stop at the successful bounded
   structured probe or proceed to a small corpus of independently specified
   factual contracts.
4. Preserve the caller-chain escalation model: a worker returns an unresolved
   decision to its caller; each caller resolves it within its authority or
   propagates it upward. Verify whether the existing ledger records caller
   identity and decision provenance before proposing a schema change.

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

## Next Action

Run one read-only metadata audit against `manifest.json`. If the six pilot
documents satisfy the contract and the distinctions remain useful, decide
whether a small deterministic validator is warranted. Do not expand the schema
or add `role` until the audit identifies a concrete need.
