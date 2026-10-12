# ChatMap to OpenWorker Ownership Transition Handoff

Status: complete. This handoff closes OpenWorker as active ChatMap work and
transfers any further runtime evaluation to the separate OpenWorker project.

## Original ChatMap Question

ChatMap evaluated OpenWorker to answer a bounded architectural question:

> Can OpenWorker own task execution while ChatMap passively records durable
> continuity, provenance, lifecycle state, decisions, failures, and artifacts
> without becoming a scheduler or agent harness?

The evaluation was not an adoption exercise and did not authorize production
integration.

## What the Evaluation Established

- OpenWorker v0.2.1 is a local-first desktop execution environment with a
  Tauri/React interface and a Python sidecar.
- Its local state includes structured SQLite databases. Relevant evidence
  includes hash-chained journal entries, audit events carrying approval and
  reason data, sessions, team items, team links, and hash-chained team events.
- The SQLite state is a promising passive observation seam. ChatMap could, in
  principle, import selected records read-only and preserve source hashes and
  provenance without controlling OpenWorker.
- OpenWorker's local REST API is token-gated, and an external observer did not
  hold the token. The SQLite seam was therefore the better fit for passive
  recording in this experiment.
- OpenWorker contains an MCP client. No A2A support or external event/export
  interface suitable for this recorder role was established.
- Agent Client addresses portable CLI invocation. It does not solve observation
  of an OpenWorker run and is not a substitute for the SQLite evidence seam.
- The unsigned Windows installer and reproducible voice-input crashes are
  operational risks recorded by the evaluation.

## What Was Not Proved

The verdict remains **INCONCLUSIVE** for live execution and persistence.

- The local `qwen2.5:7b` run first looped because Ollama used a 4096-token
  context. Raising the context to 16384 stopped the loop.
- After that correction, the model fabricated source analysis, a file-write
  operation, and success. The claimed artifact was not written.
- Consequently, the experiment did not obtain a trustworthy successful task,
  a controlled failed sibling, a populated team/journal path, or a verified
  close-and-reopen persistence result.
- No OpenWorker integration was added to ChatMap. ChatMap code, schema, UI,
  database, A2A paths, and worker-lifecycle paths remained unchanged.

## Architectural Conclusion

Execution is replaceable; durable continuity is not.

OpenWorker is one possible executor or harness. Its journal, audit, session,
team, and artifact records are candidate evidence for ChatMap, not a replacement
for ChatMap's durable ledger. ChatMap continues to own accepted continuity,
provenance, history, and the distinction between observed evidence and trusted
semantic knowledge. A future adapter, if justified, should read OpenWorker
state and translate it at the boundary. It must not make ChatMap responsible
for scheduling or operating OpenWorker.

## Ownership Transition

- ChatMap retains this handoff and the bounded evaluation report as historical
  architectural evidence.
- Installation details, model configuration, runtime stability, and any further
  OpenWorker experiments now belong to the separate OpenWorker project.
- The OpenWorker project should treat the prior reports and the embedded pinned
  ChatMap checkout as historical evidence, not as current OpenWorker authority.
- ChatMap should consume a later OpenWorker result only if it changes the
  boundary conclusion or supplies evidence for a bounded passive recorder.

## Optional Final Experiment

The OpenWorker project is **paused**, with one optional experiment remaining.
It may run a capable local model such as `qwen2.5:14b` against a disposable
workspace, with no email, calendar, Slack, cloud connectors, or real project
mutation. The run should require:

1. one grounded task that reads a named input and produces a hashable artifact;
2. one deliberate failure or blocked task whose reason and partial evidence
   remain available;
3. close-and-reopen verification of the successful artifact, failed state,
   decisions, audit evidence, and any journal/team records; and
4. a read-only comparison between the observed SQLite evidence and ChatMap's
   existing lifecycle concepts.

This is an OpenWorker capability test, not a ChatMap integration test. If Ray
declines it, or if it produces no material new evidence, the OpenWorker
evaluation should be archived without further expansion.

## Source Evidence

The separate OpenWorker repository preserves the detailed material, including:

- `openworker-bounded-evaluation-report-2026-09-14.md`
- `openworker-source-based-readiness-report-2026-09-14.md`
- `install-and-inspect-evidence.md`
- `journal-schema-findings.md`
- `stability-notes.md`
- the SQLite snapshots and the pinned read-only ChatMap specimen

The bounded report remains preserved in ChatMap at
`.llm/handoffs/openworker-bounded-evaluation-report-2026-09-14.md`.
