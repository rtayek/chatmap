---
id: CM-PARALLEL-FAILURE-01
lifecycle: working
status: active
provenance: bounded-parallel-run-2026-09-17
---
# Bounded Parallel Failure Experiment

## Purpose

Test whether ChatMap can durably record a real parallel fan-out containing two
successful workers and one deliberately failed worker, while preserving the
successful sibling evidence and allowing synthesis to complete. ChatMap records
the externally executed work; it does not launch or schedule the workers.

## Result

**PASS.** Three native read-only subagents were dispatched concurrently against
commit `1d79172`. Two returned evidence reports. The third was given an exact
required path that did not exist and correctly returned `FAILED`, preserved its
existence check as partial work, claimed no artifact, and fabricated nothing.

The enhanced test harness recorded an isolated SQLite ledger containing:

- session 1: coordinator, `COMPLETED`
- sessions 2 and 3: successful workers, `RETIRED`, one artifact and one
  semantic handoff each
- session 4: deliberate-failure worker, `FAILED`, exact reason and partial work,
  no artifact and no semantic handoff
- session 5: synthesis, `COMPLETED`, one artifact and one semantic handoff

Closing and reopening the database returned all five sessions and four
coordinator successors. A separate Python process then opened the database
read-only and independently verified the five states, the failed worker's exact
reason and partial work, and artifact rows for both successful siblings plus
synthesis.

## Durable Regression Proof

`ParallelFailureLedgerExperimentTest` creates a file-backed database, records
the mixed outcome, closes it, reopens it, and asserts:

- the failed sibling remains `FAILED`
- its reason and partial work are exact
- it has no fabricated artifact or invalid semantic handoff
- both successful sibling artifacts and handoffs remain present
- the referenced successful report files still exist and are nonempty
- synthesis remains `COMPLETED` with its artifact and handoff
- the chain contains the coordinator, three siblings, and synthesis

The focused test passed through a direct JUnit Platform launch. The full Gradle
quality gate was not available in the execution environment and still needs to
be run by Ray.

## Harness Change

`parallelLedgerRecord` now accepts optional `-PfailedWorker=<0..3>`. Zero
preserves the original all-success behavior. A selected failed worker stores
the report's `Reason:` value and complete report body in the lifecycle failure
event, and does not create an artifact or semantic handoff for that worker.
Verification after reopen checks each worker outcome, successful report-file
existence, and completed synthesis.

## Minimum Portable Worker Contract

The experiment needed only these fields:

```markdown
# Worker assignment

- Identity/role: <stable name for this run>
- Task: <one bounded objective>
- Inputs/context: <exact files, commit, and prior evidence>
- Allowed tools: <explicit capabilities>
- Constraints/permissions: <read/write and exclusion boundaries>
- Definition of done: <observable acceptance conditions>
- Failure behavior: <stop condition and required reason/partial work>
- Expected evidence: <artifact, decision request, or explicit no-artifact failure>
```

This is a task contract, not a personality description. It maps directly onto
the existing assignment, session, event, artifact, and handoff model. No new
schema is justified by this experiment.

## Proven Parallel Procedure

1. Pin one repository revision and give every worker an independent assignment.
2. Dispatch workers concurrently through an external runtime.
3. Require each worker to return either evidence satisfying its definition of
   done or an explicit failure reason with preserved partial work.
4. Record each worker as a sibling successor of the coordinator.
5. Record successful artifacts and semantic handoffs only for completed
   workers. Record failed-worker evidence in the failure transition.
6. Synthesize every outcome, including failures and disagreements; never treat
   a missing worker artifact as success.
7. Close and reopen the database, traverse from the coordinator, and verify
   states, reasons, partial work, handoffs, artifact metadata, and actual file
   existence.
8. Use a separate process for an independent readback when the run is evidence
   for an architectural decision.

## Limits Preserved

- Fan-in remains contextual rather than structural: synthesis is a fourth
  coordinator successor and names the worker sessions in text.
- ChatMap stores artifact metadata and locations, not artifact contents; file
  existence requires an explicit check.
- The run does not prove a scheduler, concurrency timing, semantic correctness,
  or general failure recovery.
- No production schema or scheduler was added.

## Evidence Hashes

The raw execution evidence was outside the repository during the run. The
deterministic regression test and this handoff preserve the reproducible proof.

```text
chatmap.db   a8b970a7f6787dcabd2dad54d66c5861d1a9ca4735ea86807f755b6c29ae1f2f
synthesis.md 368e4c9f924ec8582c8081304631b6f7f499e32d7c615272d139ab1b2e822c2a
worker-1.md  54a10340eef933291f8b5b7552a13215dfbb76f705625d47c24d6ee93298a2a0
worker-2.md  cd65c39b7479700b742fa5f316d35d085ffe145e989efcc771450e66376b3c48
worker-3.md  681c57f42ef9287e288dd397d4b30527035a0e7e1336fa3b904a2478ef4bdba4
```

## Recommendation

Treat the failed-worker parallel experiment, the minimum worker contract, and
the parallel procedure as complete bounded evidence. Do not add a scheduler,
run identifier, or fan-in schema yet. The next ChatMap step should return to
semantic refinement: add the three worked examples and deterministic negation
check, then rerun the frozen ten-case corpus.
