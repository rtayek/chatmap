# A2A Experiment Continuation Handoff (2026-09-07)

**Status:** Bounded model-backed worker increment complete. Deciding next increment.

**Source project:** `rtayek/chatmap`, `master` branch, HEAD `d376eba`.

**Supersedes:** `handoffs/a2a-experiment-continuation-handoff-2026-09-03.md` — that handoff's open decision about adding a model-backed worker has been resolved. Treat this file as current and archive the 09-03 handoff.

## What happened since the 09-03 handoff

Starting from `470b5ab` ("Enforce A2A experiment boundary"), the A2A-relevant commits were:

- `d79c99a` — **Add bounded Ollama-backed A2A worker.** A real local Ollama-backed worker was added alongside the deterministic worker. The deterministic worker remains the default; model-backed operation is selected explicitly through environment settings.
- `975c995` — **Record real model-backed A2A tasks.** Added a separate model-recording client that stores the returned task snapshot and local-model text artifact through the existing isolated lifecycle recorder.
- `6c6ab71` — **Record verified model-backed A2A persistence.** Verified that a separate process could reopen the database and retrieve the same task identity, completed state, snapshot, and model result.
- `148547b` — **Add deterministic A2A semantic probe.** Added an exact four-field response contract evaluated by Java.
- `b9c8827` — **Add Ollama A2A server launcher.** Added a foreground Bourne-shell launcher for the proven local model configuration.
- `5dda6e0` — **Record successful structured semantic probe.** Recorded one successful live run against local `qwen2.5:7b`.
- `d376eba` — **Document A2A trust boundary and operation.** Updated the README, design, and implementation notes.

Other intervening commits included a read-only worker-lifecycle record CLI, Windows test-cleanup work, merges, shell-script additions, and a topology handoff. They are not all part of the model-backed A2A increment.

## Established findings

### Existing recorder and domain model held up unchanged

The model-backed worker did not require changes to `A2aTaskRecorder` or the ChatMap domain model. `ModelRecordingClient` exercised the existing recorder against real model output. The lifecycle recorder, schema, UI, and production dependency direction remained unchanged.

This satisfies the original constraint: test the existing boundary rather than silently expanding the domain model to accommodate the experiment.

### Persistence worked, but persistence is not semantic acceptance

Local `qwen2.5:7b` completed an A2A task. ChatMap stored two lifecycle events and two artifacts in an isolated ChatMap home. A separate process reopened that database and displayed the same task identity, completed state, task snapshot, and model result.

The answer was durably preserved but was contextually over-specific. This established transport and persistence, not semantic relevance or correctness.

### The semantic probe is deliberately narrow

The probe supplies four facts and requires exactly four ordered `key=value` lines with predetermined values. Java rejects output that is missing, additional, malformed, reordered, or factually incorrect.

The successful live run demonstrates one bounded case of instruction following. It does not establish general semantic reliability, truthfulness, or the model's ability to certify its own output. External agent output remains evidence until evaluated against an explicit acceptance contract or reviewed by a human.

## Current source of truth

At the time of this handoff, the working tree was reported clean and `master` was up to date with `origin/master` at `d376eba`.

Read `working-context.md` and reconcile it with this handoff and the current code. Current verified code and repository state take precedence over stale documentation. Do not assume that either document remains current without checking Git first.

## Open decision

The other Active Agenda item from the 09-03 handoff remains open:

> Caller-chain escalation and decision provenance work in the ledger.

The model-backed worker commits did not implement this. It remains the next candidate increment unless Ray chooses another priority.

Questions for that increment include:

- How does a worker report an unresolved decision to its caller?
- Which caller resolved, rejected, or propagated the decision?
- What evidence and authority supported that action?
- How is the entire escalation path preserved in the durable ledger?
- When does an unresolved decision ultimately require human action?

Do not build a general scheduler or agent hierarchy merely to answer these questions. Start with one bounded, persisted caller-chain case.

## Related but explicitly separate

The worker-topology and organizational-simulation idea remains parked. See `handoffs/a2a-topology-handoff-2026-09-03.md` if present, or ask Ray.

That work may eventually examine capability directories, task DAGs, decision-accountability layers, and an ombudsman-style escalation channel. It is not part of the current A2A increment and must not be folded into ChatMap without an explicit decision.

## First response in the new chat

1. Fetch the repository and confirm the current `master` commit, working-tree state, and `working-context.md` agenda.
2. Confirm that the completed model-backed increment remains intact and that no newer commit has changed its conclusions.
3. Ask Ray to choose between:
   - closing the bounded model-backed worker increment and starting one persisted caller-chain escalation case; or
   - selecting another ChatMap priority.

Keep the next step small, observable, and reversible.
