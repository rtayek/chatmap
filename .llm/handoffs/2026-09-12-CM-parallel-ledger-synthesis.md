---
id: CM-HANDOFF-SYNTHESIS-2026-09-12
lifecycle: working
status: active
provenance: parallel-ledger-experiment-synthesis
---

# Synthesis Report: Parallel Subagent Lifecycle Recording

## 1. Context

This report synthesizes the findings from three parallel subagents (Lifecycle Evidence Worker, Operations Evidence Worker, and Independent Verifier). They concurrently analyzed the ChatMap worker lifecycle model (`WorkerLifecycleService`, `schema.sql`, etc.) at commit `22761162ea4b0e93a658bcc9d516b6b606e72235` to determine if it accurately records parallel subagent runs.
The actual subagents were executed in independent worktrees, and their execution structure was recorded via CLI emulation into a new integration test (`ParallelLedgerExperimentTest`).

## 2. Unanimous Agreements & Confirmed Limitations

All three independent workers identified identical structural gaps in the current relational model when applied to concurrent parallel execution:

- **Missing Fan-In (N:1 Join) Support:** `WorkerAssignment` enforces a single `predecessorSessionId`. While 1:N fan-out is cleanly supported, joining multiple parallel siblings into a single synthesis task is structurally impossible without a join table. Coordinators must stuff child session IDs into unstructured `contextAndFiles` text strings.
- **Sequential Timestamps Misrepresent Concurrency:** The service generates timestamps via `clock.instant().toString()` at the time of database insertion. External concurrent executions recorded post-hoc appear entirely sequential.
- **Lack of Parallel Run/Batch Identity:** There is no `runId`, `cohortId`, or namespace in the schema. Concurrent sibling workers are linked only by sharing a predecessor session.
- **Procedural-Only Isolation:** ChatMap's model is purely a passive deterministic audit ledger. Worktree boundaries, process isolation, timeouts, and sandboxing rely 100% on external orchestration conventions (procedural safeguards) rather than code enforcement.

## 3. Critical Defects Discovered

Both the Operations Worker (W2) and Independent Verifier (W3) independently discovered a critical failure-logging defect:

- **Diagnostic Erasure on Failure:** `WorkerLifecycleService.transition()` explicitly forces `actualDecision` to `null` unless the next state is exactly `WAITING_FOR_DECISION`. Consequently, transitioning a session to `FAILED` or `CANCELLED` drops the error reason, question, and partial work, permanently writing `NULL` to the SQLite event audit trail.

The Independent Verifier (W3) found an additional defect:

- **Retry Masking in Traversal:** `WorkerLifecycleRepository.findSessionByAssignment` uses `ORDER BY id LIMIT 1`. If an assignment is retried (multiple sessions for one assignment), `chainFrom()` will only fetch the first failed session and ignore the successful retry, breaking chain traversal.

## 4. Contradictions Identified

The Independent Verifier flagged a major contradiction in recent handoff documents:

- **Conflation of Services:** Previous handoffs (`2026-09-11-CM-parallel-agent-pilot-results.md`) cited "isolated git worktrees" and "preservation of dirty worktrees" as capabilities available for worker execution. In reality, these are properties of `HandoffOrchestratorService`, which processes inbox tasks *sequentially* and has zero integration with `WorkerLifecycleService` or parallel runs.

## 5. Disagreements / Unverified Claims

There were no factual disagreements between the workers. Their findings were highly complementary:
- W1 focused on schema deficiencies (DAG flattening, lack of join nodes).
- W2 focused on operational gaps (timeouts, SQLite locking, failure reasons).
- W3 successfully corroborated W2's code-level defect finding and identified the `LIMIT 1` traversal bug.

## 6. Recommended Next Actions

1. **Fix `WorkerLifecycleService.java`:** Allow `transition()` to preserve `reason` and `partialWork` for `FAILED` and `CANCELLED` states.
2. **Fix `chainFrom` Traversal:** Remove `LIMIT 1` or change to `ORDER BY id DESC` in `findSessionByAssignment`.
3. **Formalize Synthesis:** Adopt a standard JSON block inside `contextAndFiles` for fan-in, until an `assignmentPredecessors` join table is introduced to the schema.
4. **Use Artifacts for Failures:** Until the transition bug is fixed, coordinators must write a `WorkerArtifact` with the error stack trace before transitioning a failed worker to `FAILED`.
