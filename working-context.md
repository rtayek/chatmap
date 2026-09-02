# ChatMap Working Context

**Updated:** 2026-09-02
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
- The full Gradle quality pipeline passed after the A2A merge. The consolidated
  A2A server and same-task continuation client also passed their runtime check.
  Live provider tests remain intentionally opt-in.
- The bounded A2A experiment proved Agent Card discovery, completed, failed,
  input-required, and same-task continuation behavior. Its source now lives in
  `chatmap.a2a.experiment` on `master`, clearly separated from ChatMap's
  production packages and without database or UI integration. The temporary
  A2A and worker-lifecycle worktrees and branches have been removed; the primary
  ChatMap worktree is the only active worktree.

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
2. Compare the unique commits on
   `fix/chatgpt-json-import-identity` and
   `fix/chatgpt-json-import-identity-v2` with current `master` before deciding
   whether either remote branch can be deleted.
3. Design a bounded A2A recorder adapter that explicitly translates externally
   visible task states, messages, history, and artifacts into ChatMap's existing
   worker-lifecycle ledger. A2A SDK types must not replace ChatMap domain types.
   Treat QUEUED versus SUBMITTED and WAITING_FOR_DECISION versus INPUT_REQUIRED
   as deliberate, potentially lossy mappings.
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

Define the smallest A2A recorder adapter and verify whether the existing
worker-lifecycle ledger can represent task identity, context identity, caller
relationships, decision provenance, status messages, history, and artifacts
without a schema change. Do not build a general orchestrator, and do not infer
current work from the age or filename of a handoff.
