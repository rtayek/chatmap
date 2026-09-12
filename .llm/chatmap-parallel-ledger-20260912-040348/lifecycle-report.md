Lifecycle evidence report

Start UTC: 2026-09-12 04:06:06
End UTC: 2026-09-12 04:07:23
Pinned revision: 1bb642d390059e955a548148a4bb3b546f1c3d2f

Scope: Read required guidance and three named handoffs; reviewed pinned lifecycle domain, service, repository and tests, plus external LedgerRun.java, run-contract.md, native-running-snapshot.json and ledger-working.txt. No writes, builds, direct database reads, network, extra agents or excluded-directory reads.

Verified facts:

- One actual readback defect: the coordinator assignment appears with predecessorSessionId=0 in ledger-working.txt:9. createAssignment supplies null at src/chatmap/application/service/WorkerLifecycleService.java:35. WorkerLifecycleRepository.readAssignment reads predecessor, then id, then calls wasNull; consequently null detection applies to id instead of predecessor (src/chatmap/infrastructure/persistence/sqlite/WorkerLifecycleRepository.java:315-320). The existing reopen test checks task, state and event count, but not nullable predecessor (tst/chatmap/infrastructure/persistence/sqlite/WorkerLifecycleRepositoryTest.java:23-47). This misrepresents the root assignment; it does not establish corrupt SQL storage.

- The working snapshot shows four sessions: coordinator and three distinct native worker identities, all WORKING, with the three assignments referencing predecessor 1 (ledger-working.txt:7-9,40-41,64-65,88-89). The native snapshot independently records all three runtime tasks running at observation time (native-running-snapshot.json:2-23).

- Recorded timestamps describe ledger operations, not exact runtime boundaries. My STARTED time was 04:06:06; my ledger WORKING event is 04:06:39.185391600 (ledger-working.txt:62). Recording after spawn is explicit in run-contract.md:20. WorkerLifecycleService uses its clock for event timestamps (src/chatmap/application/service/WorkerLifecycleService.java:69-74,184-186).

- Sibling traversal is supported: successor creation requires an existing predecessor, without requiring its completion; repository retrieval returns all matching assignments, and chainFrom recursively follows their sessions (src/chatmap/application/service/WorkerLifecycleService.java:40-47,109-124; src/chatmap/infrastructure/persistence/sqlite/WorkerLifecycleRepository.java:228-240). WorkerAssignment has one predecessor field, so synthesis fan-in remains textual context (src/chatmap/domain/WorkerAssignment.java:6-15; run-contract.md:23-24).

- Individual state transitions atomically insert an event and update session state (src/chatmap/application/service/WorkerLifecycleService.java:61-75). The external harness attaches artifacts and performs transitions as separate service calls (LedgerRun.java:43-46); an interrupted command can therefore preserve an artifact while leaving its session WORKING.

- Final verification is explicitly success-specific: synthesis permits FAILED/CANCELLED workers, while verify requires five COMPLETED sessions and exactly two transitions each (LedgerRun.java:51-56,82-90). The domain also supports WAITING_FOR_DECISION and RETIRED (src/chatmap/domain/WorkerLifecycleState.java:8-26). No failure exercise is claimed by the contract.

- Artifacts store locations and descriptions, not report bytes (src/chatmap/domain/WorkerArtifact.java:6-12). The harness stores SHA-256 in description, but verify checks existence without recomputing hashes (LedgerRun.java:91-92,122-125). It makes no storeHandoff call; this run records report artifacts, not structured WorkerSemanticHandoff records.

Recommendations:

- Should fix separately: nullable predecessor reconstruction, with a reopen assertion. No repair performed.
- Should retain in synthesis: runtime/recording timestamp distinction, textual fan-in limitation and success-only verification.
- Should verify report hashes against stored descriptions before final acceptance.

Uncertainty: Final reports, synthesis and terminal reopen snapshot did not exist in the reviewed evidence. Existing tests were inspected, not executed. Semantic correctness and failure recovery remain unproven.

Next action: Finish the authorized run and review the final reopened chain, artifact hashes and terminal evidence while explicitly retaining the root-predecessor defect.

Final git status observed: modified ParallelLedgerExperimentTest.java; untracked ParallelLedgerRecordHarness.java. This worker changed neither file.

