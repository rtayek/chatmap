# Handoff: recorded native subagent experiment

CM-PARALLEL-LEDGER-01 completed on 2026-09-12 UTC with one existing readback defect documented below.

Three actual native Codex workers ran concurrently. Existing ChatMap lifecycle services durably recorded the coordinator, three siblings, separate results and synthesis in an isolated home. ChatMap did not launch workers.

## Results

| Acceptance criterion | Result | Evidence |
| --- | --- | --- |
| Coordinator assignment and session | PASS: session 1 | ledger-reopened.txt |
| Three sibling assignments | PASS: sessions 2, 3, 4 reference predecessor 1 | ledger-reopened.txt |
| Actual native concurrency | PASS: all three running at observation near 04:06:38 UTC; reported common overlap 59 seconds | native-running-snapshot.json; native-start-evidence.json; native-completed-snapshot.json |
| Distinct identities and bounded assignments | PASS | worker contracts and ledger-reopened.txt |
| Worker transitions and terminal states | PASS: each QUEUED -> WORKING -> COMPLETED | ledger-reopened.txt |
| Separate worker artifacts | PASS: three original reports persisted separately | lifecycle-report.md; operations-report.md; verifier-report.md |
| Synthesis references every worker result | PASS: exact session IDs and absolute paths checked | synthesis-contract.md; evidence-check.txt |
| Agreement, disagreement, unverified claims | PASS: retained explicitly, including differing failure-policy interpretations | synthesis.md |
| Closed and reopened database | PASS: later Java processes reopened existing database without reinitializing schema | LedgerRun.java; ledger-reopened.txt |
| chainFrom shows all work | PASS: five sessions, including three siblings and synthesis | ledger-reopened.txt |
| External execution versus recording | PASS: native runtime executes; coordinator calls existing service for recording | run-contract.md; code-provenance.md |
| Full Gradle gate if repository code changed | NOT APPLICABLE: this experiment changed no repository code | code-provenance.md |

All five sessions ended COMPLETED, with ten lifecycle events. Sixteen artifact references were hash-checked successfully. Exact report references, database integrity and synthesis-before-coordinator completion were verified.

This demonstrates the bounded recording exercise. It does not establish lossless readback, semantic correctness, failure recovery, speedup or a general orchestration capability.

## Existing defect, left unchanged

The root assignment is stored with SQL predecessor NULL, but service readback returns predecessorSessionId=0.

Cause: WorkerLifecycleRepository.readAssignment reads id between reading predecessorSessionId and testing wasNull(). The latter therefore tests the id column.

Pinned evidence:
- src/chatmap/application/service/WorkerLifecycleService.java:35
- src/chatmap/infrastructure/persistence/sqlite/WorkerLifecycleRepository.java:315-320
- ledger-working.txt:9
- evidence-check.txt:9

Two workers independently found this. The coordinator independently confirmed SQL NULL versus service 0. Sibling traversal and the required recorded structure still passed. Repair and regression coverage require a separate task; no schema change is indicated.

## Timing and limits

- Fan-out began: 04:05:50 UTC.
- Worker-reported intervals: lifecycle 04:06:06-04:07:23; operations 04:06:16-04:07:35; verifier 04:06:24-04:08:03.
- Common reported overlap: 59 seconds, supported by native running-status observation.
- Fan-out to last reported worker finish: 133 seconds.
- Fan-in from last reported worker finish through coordinator completion: 284 seconds.
- Recorded synthesis interval: 117 seconds.
- Fan-out through coordinator completion: 417 seconds (6 minutes 57 seconds).
- Coordinator completed: 2026-09-12T04:12:47.546982600Z.

Worker times are self-reported; runtime status independently establishes overlapping tasks. Ledger times are actual recording times and include persistence lag. Timing excludes setup. No sequential baseline, token accounting or CPU measurement was made.

All three deliberately repeated baseline guidance and common evidence reads. Synthesis deduplicates their findings while preserving original reports and uncertainty. No worker failed; deliberate failure and timeout experiments remain deferred. Native read-only restrictions and single-writer discipline were procedural, not an enforced tool sandbox.

## Workspace provenance

Pinned revision: 1bb642d390059e955a548148a4bb3b546f1c3d2f.
The initial checkout was clean. During execution HEAD advanced to f006df5dafbdedebacf2183693739f3bc33aaa76 and concurrent test changes appeared. Those changes were preserved; this coordinator and these workers did not make them.

All worker source citations used the pin. A fresh compile from archived pinned source produced all 25 original recorder class files byte-identically. Later commands used those archived sources/classes. No production source difference from the pin was observed during provenance checks.

Final status observation: HEAD had advanced again to 4df0d3667130f8c38c2b4b35a134fa782385d9a2. Git showed modified build.gradle.kts and tst/chatmap/application/service/ParallelLedgerExperimentTest.java, plus untracked .llm/handoffs/2026-09-12-CM-parallel-ledger-recorded-run-handoff.md and tst/chatmap/infrastructure/persistence/sqlite/ParallelLedgerRecordHarness.java. These concurrent changes are outside this experiment and were left untouched. The worktree is not clean.

No repository source, schema, configuration, dependencies or scheduler were changed by this exercise. No commit or push was performed. No process launched by this exercise remains running; all three native workers completed.

## Files and reopen instructions

Delivery directory:
C:/Users/ray/Downloads/chatmap-parallel-ledger-20260912-040348

Isolated ChatMap database:
C:/Users/ray/Downloads/chatmap-parallel-ledger-20260912-040348/home/chatmap.db

The three reports and synthesis are separate files beside this report. Contracts, native runtime observations, initial/final service snapshots, verification output, recorder source, checker source, archived pinned source and compiled classes are retained.

From Git Bash in C:/Users/ray/eclipse-workspace/chatmap, using existing repository dependencies:

```sh
runDir=C:/Users/ray/Downloads/chatmap-parallel-ledger-20260912-040348
java --enable-native-access=ALL-UNNAMED \
  -cp "$runDir/pinned-classes;$runDir/pinned/src;lib/*" \
  LedgerRun "$runDir" verify
java --enable-native-access=ALL-UNNAMED \
  -cp "$runDir/evidence-classes;$runDir/pinned-classes;$runDir/pinned/src;lib/*" \
  EvidenceCheck "$runDir"
```

These commands reopen and inspect the existing isolated database. They do not launch agents. The absolute artifact locations in SQLite refer to the original delivery directory; moving a copy does not rewrite those locations.

Existing multiple-SLF4J-provider warnings occurred; both final verification processes exited 0. One initial compile of EvidenceCheck.java failed because description() returns String rather than Optional; it was corrected in the external checker, recompiled, and passed. No project code repair was involved.

Guidance read: .llm/index.md first; AGENTS.md; CLAUDE.md; index-routed human.md, persona.md, first-principles.md, design.md, evo.md, working-context.md; requested handoff and its two specified prior-evidence handoffs. Relevant implementation notes, lifecycle demo, service tests and persistence tests were also inspected.
