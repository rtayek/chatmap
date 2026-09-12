# Synthesis: native subagent practice and lifecycle recording

Task: CM-PARALLEL-LEDGER-01
Review pin: 1bb642d390059e955a548148a4bb3b546f1c3d2f
Execution: Codex native subagents; recording: existing ChatMap WorkerLifecycleService.
Synthesis author: coordinator /root, session 5.

## Inputs and structure

| Session | Native identity | Result |
| --- | --- | --- |
| 2 | /root/lifecycle | lifecycle-report.md |
| 3 | /root/operations | operations-report.md |
| 4 | /root/verifier | verifier-report.md |

The three worker assignments share coordinator session 1 as predecessor. Synthesis session 5 also follows coordinator 1; its contextAndFiles explicitly names every worker session and absolute report path (synthesis-contract.md). The model has one predecessor, so these references describe fan-in without creating a multi-parent relationship. Completion order is workers, synthesis, coordinator; final readback is preserved separately.

## Agreement

All three reports support overlapping native runtime work, distinct worker identities, and a service-based ledger with recording-time timestamps. Native task status was running for all three at the observation near 04:06:38 UTC (native-running-snapshot.json). This is actual concurrent task execution, with useful independent review, not a sequential emulation or a CPU-utilization measurement.

All three identify artifact validation and success-path limits. WorkerArtifact stores external file locations, not report bytes; the recorder places SHA-256 in descriptions (src/chatmap/domain/WorkerArtifact.java:6-12; LedgerRun.java:122-125). Reports remain individually identifiable. Final verification supplements the initial structural checker with recomputed hashes and exact report references in EvidenceCheck.java.

Lifecycle and verifier independently found the same existing readback defect: createAssignment supplies a null predecessor, but WorkerLifecycleRepository.readAssignment reads id before calling wasNull(), and reconstructs the root predecessor as 0. Evidence: src/chatmap/application/service/WorkerLifecycleService.java:35; src/chatmap/infrastructure/persistence/sqlite/WorkerLifecycleRepository.java:315-320; ledger-working.txt:9. No repair was made. The final evidence checker compares the actual SQL root value with service readback; its result must accompany any success claim. This run does not establish lossless lifecycle round-tripping.

Existing service traversal returns siblings via successor assignments (WorkerLifecycleService.java:109-124; WorkerLifecycleRepository.java:228-240). Transitions record events and update state transactionally (WorkerLifecycleService.java:61-75). Assignment creation, artifact attachment and transitions remain separate service operations; a crash between them can leave partial recording.

## Disagreement

There is no substantive contradiction between the factual findings. A difference in interpretation is retained: the verifier calls acceptance of FAILED/CANCELLED workers by synthesis versus all-COMPLETED verification a failure-policy mismatch. Lifecycle and operations describe the same behavior as appropriate to the explicitly successful first run. Both observations remain relevant: this checker fits this run, but cannot certify the later partial-failure experiment. No worker was relabeled to make checks pass.

The workers could not certify final synthesis or persistence because those artifacts did not yet exist. Their original uncertainty is preserved in the individual reports. Coordinator checks after the reports resolve some of these questions; the reports were not rewritten to imply that workers performed those later checks.

## Timing

Source: native-start-evidence.json and native-completed-snapshot.json. Worker-reported UTC bounds are distinct from ledger event times.

| Worker | Start UTC | End UTC | Seconds |
| --- | --- | --- | --- |
| lifecycle | 04:06:06 | 04:07:23 | 77 |
| operations | 04:06:16 | 04:07:35 | 79 |
| verifier | 04:06:24 | 04:08:03 | 99 |

All three reported intervals overlap for 59 seconds, from 04:06:24 through 04:07:23. Native running status observed within that interval independently supports task overlap. Three sequential spawn tool calls dispatched them without waiting for any completion; no artificial worker barrier was used.

Fan-out began at 04:05:50 UTC. The all-running observation was 48 seconds later. First worker start to last reported worker end was 117 seconds. Fan-out start to last reported worker end was 133 seconds. Total reported worker time was 255 seconds, including tool/guidance overhead; this is not a sequential baseline or a speedup measurement.

All worker sessions had been recorded terminal by 04:09:59 UTC. Recording lag includes coordinator persistence, source-provenance checks and report transcription. Synthesis recording began near 04:10:49 UTC. evidence-check.txt will report exact recorded synthesis and fan-in durations through coordinator completion. Setup time before fan-out is excluded.

## Duplicated work

All three read required baseline guidance, the three specified handoffs, and common run evidence. This was intentional so each could independently assess the same contract. Their reports repeat at least five common themes: concurrency evidence, recording-time timestamps, external artifact integrity, failure/verification scope, and limitations of provisional evidence. Lifecycle and verifier independently duplicated the root-predecessor diagnosis. Operations supplied distinct explicit-home and compiled-provenance analysis.

The coordinator deduplicated the synthesis while retaining the three original reports. Exact duplicated tool calls, token cost and CPU time were not instrumented, so no quantitative savings are claimed. Worker-reported total durations cannot isolate duplicated work.

## Isolation and provenance

Workers were instructed to remain read-only; this runtime exposes no per-worker restricted-tool sandbox. Single-writer ledger access was a coordinator procedure. The database path is explicitly selected under this experiment's home directory; normal ChatMap homes and .chatmap-local were not read or written by this exercise.

Live HEAD advanced during the run to f006df5dafbdedebacf2183693739f3bc33aaa76, with concurrent test-file changes observed. These changes were preserved and are not attributed to this experiment. Every worker used pinned Git source for citations. A fresh compile from archived pinned source produced all 25 original recorder class files byte-identically; subsequent commands use the archived source/classpath (code-provenance.md). Source files, schema, configuration, dependencies, A2A and production orchestration were not changed by this exercise.

## Missing or failed workers

None. Native completion status was observed for all three, and all returned useful separate reports. No deliberate timeout, cancellation, crash, missing-worker case or partial-failure recovery was exercised.

## Unverified claims and limits

- No sequential control run: speedup is unverified.
- No CPU or token telemetry: simultaneous CPU use and cost savings are unverified.
- Agreement does not establish semantic correctness or preservation across handoffs.
- No structured WorkerSemanticHandoff records were requested by the acceptance criteria or created; this run uses result artifacts.
- No automatic capture of native runtime events, crash recovery or general scheduler was introduced.
- Runtime identity is the canonical native task name exposed by the collaboration tool; no hidden runtime UUID is claimed.
- Native event evidence is coordinator-preserved tool output and worker messages, not a signed automatic export.
- Final chain, file hashes, exact references, integrity and ordering are checked after this synthesis is stored. Read final-report.md, ledger-reopened.txt and evidence-check.txt for those outcomes.

## Next action

Treat this as one bounded concurrency-and-recording result with an unresolved existing root readback defect. A separate authorized repair should capture predecessor wasNull() before reading id and add a root-predecessor reopen assertion. The later failure-injection and sequential-comparison experiments remain deferred.

