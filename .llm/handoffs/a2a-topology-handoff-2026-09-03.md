# A2A Experiment + Worker-Topology Handoff (2026-09-03)

## Purpose

Continuation handoff for a new chat. Picks up where the existing
`handoffs/a2a-java-experiment-handoff-2026-09-02.md` left off, plus context
from a follow-on discussion that is NOT yet part of that handoff.

Read `handoffs/a2a-java-experiment-handoff-2026-09-02.md` first — it is still
the authoritative scope document for the bounded A2A experiment. This file
adds review findings and a related-but-separate idea; it does not replace
anything in the original handoff.

## Status of the A2A experiment itself

As of this writing, no code has been reported back to me (Claude web) for the
A2A experiment. Assume `working-context.md`'s Active Agenda item 3 is still
open: "Define one bounded Java A2A experiment outside ChatMap... Map the
observations back to ChatMap's worker lifecycle; do not build a general
orchestrator."

## New finding: reject row 3 of the "Existing ChatMap Component → Natural A2A
Evolution" table

A comparison table (source unclear — possibly from Codex or an external
review) proposed three mappings:

1. `WorkerLifecycleState` → A2A Task States — **legitimate**, matches the
   conceptual-mapping table already in the a2a-java-experiment handoff
   (`WAITING_FOR_DECISION` ↔ `TASK_STATE_INPUT_REQUIRED` is a close match).
2. `WorkerAssignment` / `WorkerSemanticHandoff` → A2A artifacts or message
   parts — **plausible, worth investigating** as part of the experiment's
   comparison report.
3. `HandoffOrchestratorService` → "could expose an A2A client interface to
   delegate tasks to external specialized worker agents running remotely or
   in Docker containers" — **rejected**. This is a full architectural
   commitment (network boundary, trust boundary, deployment story) disguised
   as an observation. It directly contradicts the a2a-java-experiment
   handoff's constraints: no general orchestrator/scheduler/agent harness is
   authorized, the first slice must use a deterministic fake worker (not a
   live remote agent), and coordination must never bypass the durable ledger.

Three independent reviews (Claude web, Gemini, ChatGPT) converged on
rejecting row 3. Treat this as settled unless new evidence from the actual
experiment changes the picture.

**Action for `working-context.md`:** add a note that rows 1–2 of this table
are in scope for the A2A experiment's eventual comparison report; row 3 is
out of scope pending experiment results. (Not yet written — do this as part
of the next real edit to that file, not as a standalone commit.)

## Separate, deferred idea: worker-topology simulation (NOT part of the A2A experiment)

A distinct idea came up in discussion and was explicitly deferred as "maybe a
separate project" — do not fold this into the A2A experiment or ChatMap
proper. Recorded here only so it isn't lost.

Core question: as the number of AI workers grows (a dozen → a hundred →
a thousand), what coordination/organizational structure is needed on top of
ChatMap's existing worker-lifecycle model?

Sketch discussed:

- **Capability directory** (flat, who/what can do which kind of work — maps
  loosely to A2A Agent Cards)
- **Task DAG** (per-task dependency graph — this is what
  `WorkerAssignment`/`WorkerSemanticHandoff` predecessor/successor chains
  already capture, and it stays roughly self-similar at any scale)
- **Decision/accountability layer** (where `WAITING_FOR_DECISION` lives —
  should probably stay simple/human-anchored rather than graph-shaped, since
  ambiguous accountability is the dangerous failure mode, not inefficiency)
- An **ombudsman-equivalent channel**: a cross-cutting escalation path,
  outside the normal hierarchy/DAG, for flagging "the coordination structure
  itself is broken" rather than "this task failed." Should write to the same
  durable ledger, not just reach a human's ear, so patterns are queryable.
- Requirement stated explicitly: whatever topology is chosen must be
  **observable (ledger-backed) and revisable**, not fixed at design time —
  restructuring modeled as small local perturbations (annealing-style)
  evaluated against metrics, not a full redesign each time something breaks.
  A human approves structural changes; the system doesn't self-modify
  autonomously.

Prior art found via web search, for whoever picks this up:

- **Span of control** (org theory, 1922–present): Hamilton's original 3–6
  direct-reports finding, Graicunas's 1933 mathematical treatment, Urwick's
  geographic-dispersion theory, and the 1980s flattening trend enabled by
  cheap IT (spans moved toward 1-to-10). Notably, at least one modern study
  found *wider* spans positively correlated with performance in some
  settings — the "narrower is always better" folk theory doesn't fully hold.
- **Computational/mathematical organization theory** (Kathleen Carley, CMU
  CASOS Center, early 1990s–present): agent-based simulation of
  organizational structure vs. performance. Key findings worth knowing before
  building a simulation: (a) simpler agent models suffice at macro/large-N
  scale, more cognitively detailed agents are needed at micro/small-team
  scale, for equivalent predictive accuracy; (b) design→performance
  relationships can be chaotic despite simple rules of change — don't expect
  a single clean optimum. Relevant book: Prietula, Carley & Gasser (eds.),
  *Simulating Organizations* (MIT Press, 1998).
- No literature found specifically modeling an ombudsman-style boundary
  channel in agent-based org simulations — this appears to be a genuine gap,
  not a solved problem to go read up on.

This is explicitly NOT scoped, NOT started, and NOT authorized as ChatMap
work. It's parked here so it isn't lost, not as a task to pick up
unprompted.

## What to do with this file

This is a hand-authored continuation note, not a completed/archived handoff.
Treat it as input to a new chat, not as something to implement directly.
Whoever picks it up should:

1. Read `handoffs/a2a-java-experiment-handoff-2026-09-02.md` first.
2. Read `working-context.md` for current operational state.
3. Decide whether to fold the row-3 rejection into `working-context.md` now.
4. Leave the worker-topology/simulation idea alone unless Ray explicitly
   asks to start it as its own project.
