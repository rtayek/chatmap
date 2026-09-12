Operations review complete. No writes, builds, network, direct database access, excluded-path reads, or additional agents.

Scope: required guidance and three named handoffs; external run contract, recorder, available runtime/ledger snapshots; pinned path resolution, lifecycle, transaction, and persistence code.

Pinned code revision: `1bb642d390059e955a548148a4bb3b546f1c3d2f`.
External evidence below is relative to `C:/Users/ray/Downloads/chatmap-parallel-ledger-20260912-040348/`.

Verified facts:

- Isolation is explicit database routing plus procedural worker restrictions. `LedgerRun.java:13-25` selects `<run>/home/chatmap.db`, checks that path, creates a new home for initialization, and otherwise requires an existing database. Pinned `src/chatmap/app/bootstrap/ChatMapPaths.java:77-85` prioritizes explicit home before environment or fallback homes. `ledger-working.txt:6` reports the expected isolated path. Worker restrictions are explicitly instructions, not a sandbox (`run-contract.md:11-13`).
- The coordinator snapshot reports lifecycle, operations, and verifier simultaneously running at approximately `04:06:38 UTC`, with fan-out start `04:05:50 UTC`: 48 seconds to that observation (`native-running-snapshot.json:2-23`). This supports overlapping native task lifetimes, not simultaneous CPU work or speedup. I inspected the preserved coordinator snapshot, not its originating tool result.
- Ledger recording follows execution. Operations began at `04:06:16 UTC`; session 3 was recorded WORKING at `04:06:39.929486400Z` (`ledger-working.txt:64,86`). Pinned `src/chatmap/application/service/WorkerLifecycleService.java:69-74,184-185` timestamps the recording operation. These timestamps cannot independently establish actual dispatch or worker duration.
- Artifacts are external files referenced by location. The recorder stores SHA-256 in their descriptions (`LedgerRun.java:122-125`); pinned `src/chatmap/application/service/WorkerLifecycleService.java:78-82` stores strings without file-content validation. The final verifier checks existence but does not recompute hashes (`LedgerRun.java:91-92`). My read-only hashes for the operations contract and native snapshot match `ledger-working.txt:87,39`.
- Failure handling is partly structural. Synthesis permits COMPLETED, FAILED, or CANCELLED workers (`LedgerRun.java:50-56`), but final verification requires every session COMPLETED (`LedgerRun.java:83-90`). This fits the current successful-run contract; it does not validate a partial-failure run.
- Each lifecycle transition is transactional, but recorder commands contain several separately committed service calls. A failure after assignment creation or artifact insertion can leave partial recording (`LedgerRun.java:36-46`; pinned `src/chatmap/application/service/WorkerLifecycleService.java:32-47,61-82`; `src/chatmap/infrastructure/persistence/sqlite/TransactionRunner.java:40-55`). Single-writer operation is coordinator policy, not a cross-process lock.
- Current checkout differs from the review pin. At `04:07:24 UTC`, HEAD was `f006df5dafbdedebacf2183693739f3bc33aaa76`; status showed modified `tst/chatmap/application/service/ParallelLedgerExperimentTest.java` and untracked `tst/chatmap/infrastructure/persistence/sqlite/ParallelLedgerRecordHarness.java`. All my code evidence came from the pinned revision.

Recommendations:

- Preserve native start/end observations separately from ledger recording times.
- Recompute artifact hashes during final evidence review.
- Identify the recorder's actual compiled-code provenance before describing runtime behavior as tested at the pinned commit.

Uncertainty: terminal reports, synthesis, final reopen verification, total fan-in duration, duplication, and disagreements were unavailable at review completion. No deliberate failure or sequential comparison was performed.

Next action: coordinator completes terminal recording and synthesis, then preserves reopen output, artifact-hash checks, timing evidence, and runtime-code provenance.

Started UTC: `2026-09-12 04:06:16`.
Ended UTC: `2026-09-12 04:07:35`.

