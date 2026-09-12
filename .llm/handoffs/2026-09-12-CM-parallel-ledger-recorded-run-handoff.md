---
id: CM-HANDOFF-PARALLEL-LEDGER-RECORDED-2026-09-12
lifecycle: working
status: active
provenance: three-concurrent-read-only-subagents-recorded-at-1bb642d
---

# Handoff: Recorded Parallel Subagent Run (CM-PARALLEL-LEDGER-01)

## Summary

Executed the CM-PARALLEL-LEDGER-01 handoff. Three native subagents ran
concurrently, read-only, against one pinned commit. Their structure was recorded
into an isolated ChatMap home using only the existing worker-lifecycle service.
No schema change. No scheduler. The database was closed and reopened by a
separate process and chainFrom returned the full shape.

External runtime launched the workers. ChatMap recorded them. These are distinct.

## Baseline and Isolation

- Repository: rtayek/chatmap
- Pinned commit: 1bb642d390059e955a548148a4bb3b546f1c3d2f (working tree matched it)
- Run started (UTC): 2026-09-12T04:03:52Z
- Workers done by (UTC): 2026-09-12T04:06:50Z
- Isolated ChatMap home (scratchpad, outside the repo and outside .chatmap-local):
  scratchpad/parallel-ledger-experiment/home/chatmap.db
- Worker reports (artifact targets):
  scratchpad/parallel-ledger-experiment/reports/{worker-1,worker-2,worker-3,synthesis}.md

## External Execution (not ChatMap)

Three Explore-type subagents were dispatched in a single message so they ran
concurrently. Explore agents have no write/edit/build tools, which enforced the
read-only constraint at the tool level.

Durations reported by the runtime (overlapping):
- worker-1 lifecycle-evidence: 71.9 s
- worker-2 operations-evidence: 95.2 s
- worker-3 independent-verifier: 84.2 s
Wall-clock fan-out about 95 s (max) vs about 251 s if sequential.

## ChatMap Recording (the ledger)

Recorded via a small harness that calls only WorkerLifecycleService:
- tst/chatmap/infrastructure/persistence/sqlite/ParallelLedgerRecordHarness.java
- Gradle task: parallelLedgerRecord (-Phome -Preports -Pstarted)

Recorded structure (from the reopened chain):
- session 1 coordinator:claude-opus       state COMPLETED  successors 4
- session 2 worker-1:lifecycle-evidence   state RETIRED    events 3  artifacts 1  handoff yes
- session 3 worker-2:operations-evidence  state RETIRED    events 3  artifacts 1  handoff yes
- session 4 worker-3:independent-verifier state RETIRED    events 3  artifacts 1  handoff yes
- session 5 coordinator-synthesis         state COMPLETED  events 2  artifacts 1  handoff yes

Assignments 2, 3, 4 (the three workers) and assignment 5 (synthesis) all name
coordinator session 1 as predecessor. Each worker artifact location is the
absolute path to that worker's report file. The synthesis assignment names the
three worker session ids and artifact paths in its contextAndFiles, because the
model has only one predecessor per assignment.

## Verification

- In-process reopen: harness closed the ServiceGraph, reopened the same file
  database, and chainFrom(1) returned 5 sessions in the shape above.
- Separate-process reopen: workerLifecycleRecord -Phome=... -Psession=1 printed
  the coordinator with state COMPLETED, 2 events, and 4 successors.
- Full Gradle quality gate: ./gradlew check BUILD SUCCESSFUL (checkstyle, pmd,
  spotbugs, test). One transient Windows failure (a stale daemon held
  build/test-results); ./gradlew --stop plus clearing the directory resolved it,
  and the rerun passed cleanly.

## Evidence Answers

- Concurrent, not sequential: yes; single-message dispatch, overlapping durations.
- Fan-out/fan-in wall clock: about 95 s fan-out; recording is sub-second.
- Duplicated work: minimal by design; roles were non-overlapping. Overlap was
  the shared file set the three inspected, which is intended cross-checking.
- Did workers disagree: yes. Worker 3 flagged that "chainFrom returns all
  reachable successor sessions" is overstated (it follows only the first session
  per assignment, skips unstarted successors, has no cycle guard). Workers 1 and
  2 did not raise this. The disagreement is preserved in synthesis.md.
- Coordinator preserved disagreement: yes; synthesis.md has explicit Agreement,
  Disagreement/nuance, and Unverified sections.
- All conclusions file-backed: worker reports cite file:line evidence.
- Hard/impossible to represent: multi-parent fan-in, a parallel-run identifier,
  and true concurrency intervals (startedAt/finishedAt). See below.
- Did any worker failure block synthesis: no; all three returned.

## Limitations Confirmed (no schema change made)

- Fan-in is not structural. An assignment has one predecessorSessionId, so the
  synthesis links at most one sibling; the other two are free text only and are
  not traversed by chainFrom.
- No run/batch identifier groups the coordinator, siblings, and synthesis; they
  are grouped only by the shared predecessor.
- No startedAt/finishedAt, so the real concurrency interval of the three workers
  lives in this handoff and synthesis.md, not in the schema.
- Recording through one connection is serialized (safe, not parallel).

If a later experiment needs real fan-in provenance or run grouping, the smallest
change is a workerAssignmentInputs(assignmentId, sourceSessionId) join table plus
a runId column. Not done here; out of scope per the boundaries.

## Repository Changes (uncommitted)

- Added: tst/chatmap/infrastructure/persistence/sqlite/ParallelLedgerRecordHarness.java
- Modified: build.gradle.kts (added parallelLedgerRecord task)

Not committed and not pushed. The recorded database and reports live in the
session scratchpad, outside the repository.

## Acceptance Criteria

All met: coordinator stored; three successor assignments share the coordinator
session; three subagents ran concurrently; distinct identities and bounded
assignments; per-worker transitions and terminal state stored; per-worker
artifact; one synthesis assignment referencing all three; synthesis distinguishes
agreement, disagreement, and unverified claims; database closed and reopened
(in-process and separate process); chainFrom shows coordinator, three siblings,
and synthesis; report distinguishes external execution from ChatMap recording;
full Gradle quality gate passes.
