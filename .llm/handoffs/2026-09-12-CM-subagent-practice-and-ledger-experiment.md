---
id: CM-HANDOFF-SUBAGENT-PRACTICE-2026-09-12
lifecycle: working
status: active
provenance: chat-planning-after-parallel-pilot
---

# Handoff: Subagent Practice and Lifecycle Recording

## Metadata

- From: current System and ChatMap planning chat
- To: next ChatMap implementation session
- Task reference: CM-PARALLEL-LEDGER-01
- Priority: high
- Date: 2026-09-12
- Baseline when written: `22761162ea4b0e93a658bcc9d516b6b606e72235`

## Purpose

Start using native subagents regularly so the project gains practical
experience directing them, while testing whether ChatMap can preserve a
durable account of a real parallel run.

The immediate objective is not to build a general agent runtime. Codex,
Claude Code, Gemini, Antigravity, and similar tools increasingly provide
their own subagent execution. ChatMap should first learn to record what those
runtimes do.

## Prior Evidence

Read these related handoffs before starting:

- `2026-09-11-CM-parallel-agent-pilot-results.md`
- `2026-09-11-CM-worktree-and-handoff-structure-ideas.md`

The first pilot successfully dispatched three read-only workers concurrently
against one pinned ChatMap commit. It established that narrow roles can
produce complementary evidence. It did not record those workers in ChatMap's
SQLite lifecycle ledger.

## Current State

ChatMap already provides:

- `WorkerAssignment`
- `WorkerSession`
- lifecycle events and terminal states
- `WorkerArtifact`
- `WorkerSemanticHandoff`
- predecessor and successor traversal
- SQLite persistence and database reopen checks

Multiple assignments can share one predecessor session, which is sufficient
to represent three sibling workers in a bounded experiment. The model does
not explicitly represent a multi-parent fan-in, scheduler, join barrier, or
parallel run identifier.

## Deliverable Request

Run one real, bounded parallel-subagent exercise and record its structure in
an isolated ChatMap home using the existing worker-lifecycle model.

Use an external runtime to launch the workers. ChatMap remains the ledger and
does not need to become the launcher for this experiment.

## Proposed Run

### Coordinator

Create one coordinator assignment and session, then transition it to
`WORKING`.

The coordinator owns:

- the pinned repository commit
- assignment boundaries
- final synthesis
- lifecycle recording
- identification of missing or failed workers

### Parallel workers

Launch three native subagents concurrently against the same pinned commit.
Keep all three read-only and prohibit builds, formatting, or generated shared
state during this first recorded run.

Suggested roles:

1. Lifecycle evidence worker
   - inspect whether the recorded lifecycle matches the actual run
   - identify missing or misleading state

2. Operations evidence worker
   - examine isolation, timing, failure handling, and artifact paths
   - distinguish procedural safeguards from enforced safeguards

3. Independent verifier
   - check the other two assignments' evidence requirements
   - report contradictions, unsupported claims, and omissions

Each worker receives:

- one bounded task
- the same exact Git commit
- required context files
- permitted tools
- explicit exclusions
- required output structure
- evidence requirements
- definition of done
- failure-reporting instructions

### Synthesis

After the three workers finish, create a synthesis assignment. Because the
current model has only one predecessor per assignment, place all three child
session IDs and artifact locations in the synthesis assignment's
`contextAndFiles` field.

The synthesis must preserve disagreements rather than silently force
consensus.

Complete the coordinator only after synthesis is stored.

## Acceptance Criteria

The work is complete when all of the following are demonstrated:

- [ ] One coordinator assignment and session are stored.
- [ ] Three successor assignments share the coordinator session as predecessor.
- [ ] Three native subagents actually run concurrently.
- [ ] Each worker has a distinct identity and bounded assignment.
- [ ] Each worker's lifecycle transitions and final state are stored.
- [ ] Each worker produces a separately identifiable artifact.
- [ ] One synthesis assignment references all three worker results.
- [ ] The synthesis artifact distinguishes agreement, disagreement, and
      unverified claims.
- [ ] The database is closed and reopened successfully.
- [ ] `chainFrom()` or an equivalent existing read path shows the coordinator,
      three sibling workers, and synthesis work.
- [ ] The final report clearly distinguishes external execution from ChatMap
      recording.
- [ ] The full Gradle quality gate passes if repository code is changed.

## Evidence to Preserve

Record enough evidence to answer:

- Were the workers actually concurrent rather than merely sequential?
- How much wall-clock time did the fan-out and fan-in require?
- How much work was duplicated?
- Did independent workers disagree?
- Did the coordinator retain or erase those disagreements?
- Were all factual conclusions backed by file evidence?
- What lifecycle information was difficult or impossible to represent?
- Did any worker failure prevent useful synthesis?

Prefer timestamps and stored artifacts over retrospective claims.

## Boundaries

Do not:

- add a general scheduler
- change the database schema
- expand the A2A experiment
- install the complete Agency Agents catalog
- create personality-heavy worker prompts
- let multiple workers write in one working tree
- make `HandoffWatcher` interpret or route semantic content
- read `.chatmap-local/` except through an explicitly authorized,
  experiment-specific isolated home

If recording the real run cannot be done cleanly with existing services,
stop and report the exact limitation before proposing an architectural
change.

## Later Experiments

After the read-only recorded run succeeds:

1. Repeat with one deliberately failing or timing-out worker.
2. Compare parallel and sequential execution using the same assignments.
3. Try two writing workers in separate branches and Git worktrees.
4. Move stable reusable worker definitions into `dotmdfiles`.
5. Add runtime-specific converters only after the portable definition is
   understood.

## Recommended First Step

Inspect the existing worker-lifecycle demo, service tests, and persistence
tests. Choose the smallest isolated harness or test that can record the
coordinator, three siblings, their artifacts, and synthesis without changing
the schema or production orchestration boundary.
