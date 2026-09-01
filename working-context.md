# ChatMap Working Context

**Updated:** 2026-09-01
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

## Closed Work

- Shell LLM relay experiment: successful, tested, and archived.
- Worker-lifecycle experiment: successful and incorporated; no longer a
  separate project.
- Agent-facing Markdown pilot: completed. Keep client entry points simple;
  distinguish auto-discovered skills from ordinary operational Markdown.
- Agent-protocol survey: completed. A2A is the first protocol to test later;
  MCP is complementary; ACP is absorbed into A2A; ANP is deferred.

## Active Agenda

1. Re-verify current `master`, CI, and the last code-review findings before
   treating any old handoff as a current defect report.
2. Continue the smallest trustworthy-acquisition and persistence hardening
   work that still reproduces: identity/idempotency, provider provenance,
   latest-chat selection, transaction safety, and explicit failure handling.
3. Keep optional coordination subordinate to ChatMap's durable ledger. Do not
   start a general orchestrator framework.
4. When priority permits, design one small test that checks preservation of
   important meaning across a semantic handoff. Existing lifecycle and soak
   tests check durable structure, not meaning.

## Deferred

- A2A implementation beyond a bounded experiment
- a general scheduler, router, permissions framework, or agent harness
- full semantic-extraction implementation
- embeddings, semantic search, and broad UI redesign

## Next Action

Inspect current `master` and CI against the most recent review findings, report
only defects that still reproduce, and select one bounded repair. Do not infer
current work from the age or filename of a handoff.
