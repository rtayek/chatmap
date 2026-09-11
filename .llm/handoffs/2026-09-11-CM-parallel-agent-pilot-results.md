---
id: CM-HANDOFF-PARALLEL-PILOT-2026-09-11
lifecycle: working
status: active
provenance: three-concurrent-read-only-workers-at-f31277e
---

# Parallel Agent Pilot Results

## Purpose

Record the first small, genuinely concurrent multi-worker pilot performed
against ChatMap, using selected ideas from
`msitarzewski/agency-agents` without adopting its large persona catalog or
NEXUS process.

This handoff is evidence from an external agent runtime. It is not proof that
ChatMap itself can launch parallel workers.

## Baseline and Boundaries

- Repository: `rtayek/chatmap`
- Baseline commit: `f31277eefc31f46be9173925902116b40d7d669b`
- Date: 2026-09-11
- Three workers were dispatched before the coordinator waited for results.
- All workers were read-only.
- All workers used the same immutable Git commit.
- No worker read `.chatmap-local/`.
- No builds, tests, formatting, code changes, or repository writes were
  performed by the workers.
- The coordinator synthesized the reports and wrote this handoff.

## Parallel Assignments

### Worker 1: Lifecycle mapper

Examined the domain model, persistence, services, and tests to determine
whether the existing ledger can represent sibling workers and a later
synthesis step.

### Worker 2: Parallel-safety reviewer

Examined the handoff orchestrator, Git worktree support, process execution,
failure behavior, and tests to determine safe operating rules for concurrent
workers.

### Worker 3: Agency Agents adapter

Compared Agency Agents' coordination material with ChatMap and identified the
smallest useful subset to adapt.

## Combined Findings

### Existing ChatMap support

The existing ledger can represent a basic parallel experiment without a schema
change:

- Multiple `WorkerAssignment` rows may share the same
  `predecessorSessionId`.
- Each sibling can have its own `WorkerSession`, worker identity, lifecycle
  events, artifacts, and semantic handoff.
- `findSuccessorAssignments()` returns all successors.
- `chainFrom()` recursively retrieves the resulting branch structure.
- Ledger access through one repository connection is synchronized, although
  this serializes database access rather than launching work concurrently.

Relevant evidence:

- `src/chatmap/domain/WorkerAssignment.java`
- `src/chatmap/domain/WorkerSession.java`
- `src/chatmap/domain/WorkerArtifact.java`
- `src/chatmap/domain/WorkerSemanticHandoff.java`
- `src/chatmap/application/service/WorkerLifecycleService.java`
- `src/chatmap/application/port/persistence/WorkerLifecycleStore.java`
- `src/chatmap/infrastructure/persistence/sqlite/WorkerLifecycleRepository.java`
- `src/chatmap/infrastructure/persistence/sqlite/schema.sql`

### Current limitations

ChatMap does not currently provide:

- a parallel scheduler or executor
- an explicit fan-out run or batch identifier
- multi-parent dependencies for fan-in
- a join or barrier state
- explicit caller identity or decision-provenance identity
- an active cancellation API wired to lifecycle state
- parallel-worker tests
- integration between `HandoffOrchestratorService` and
  `WorkerLifecycleService`

An assignment has at most one predecessor session. Therefore a synthesis
assignment cannot structurally point to three parent workers. For a bounded
pilot, its context can name the three child session IDs and artifact paths
without pretending the schema supports a true multi-parent join.

### Existing operational safety

ChatMap already has useful pieces for later writing-worker experiments:

- one isolated worktree per orchestrated handoff task
- command timeout and descendant-process termination
- durable stdout and stderr capture
- preservation of dirty worktrees after failure or timeout
- continued processing after one task fails
- lifecycle states including `FAILED` and `CANCELLED`

However, `HandoffOrchestratorService` currently processes tasks sequentially.
There is no inbox lease or lock, so multiple orchestrator instances must not
process the same inbox concurrently.

Relevant evidence:

- `src/chatmap/application/service/HandoffOrchestratorService.java`
- `src/chatmap/application/service/GitWorkspaceManager.java`
- `src/chatmap/infrastructure/command/ProcessRunner.java`
- `tst/chatmap/application/service/HandoffOrchestratorServiceTest.java`
- `tst/chatmap/application/service/HandoffOrchestratorServiceScenarioTest.java`
- `tst/chatmap/application/service/GitWorkspaceManagerTest.java`
- `tst/chatmap/infrastructure/command/ProcessRunnerTest.java`

## Agency Agents Assessment

Agency Agents is useful as a source of role contracts and coordination
patterns, not as ChatMap's runtime. Its companion desktop application
explicitly describes itself as an installer rather than an agent runtime.

Useful ideas retained:

- coordinator fan-out followed by fan-in synthesis
- narrow specialist roles
- explicit inputs, constraints, deliverables, and definition of done
- evidence-based review
- builder and critic separation
- bounded retries and visible failure
- preservation of disagreements rather than forced consensus
- least-privilege tools

Ideas rejected or deferred:

- installing the full persona catalog
- personality-heavy prompts
- the full seven-phase NEXUS structure
- mesh deliberation and consensus loops
- uncalibrated confidence scores
- production retry, cost, and circuit-breaker machinery
- simultaneous writers in one working tree
- schema changes or broader A2A work before evidence requires them

External references:

- `msitarzewski/agency-agents/README.md`
- `strategy/nexus-strategy.md`
- `strategy/coordination/agent-activation-prompts.md`
- `strategy/coordination/handoff-templates.md`
- `engineering/engineering-multi-agent-systems-architect.md`
- `msitarzewski/agency-agents-app/README.md`

## What This Pilot Established

- Three independent workers can inspect the same pinned ChatMap revision
  concurrently when an external runtime provides the concurrency.
- Narrow, non-overlapping roles produced complementary reports suitable for
  coordinator synthesis.
- The reports agreed that the existing lifecycle model is sufficient for a
  no-schema pilot.
- The reports agreed that parallel writing requires separate branches and
  worktrees.
- The reports agreed that Agency Agents should be mined for small coordination
  patterns rather than adopted wholesale.

## What This Pilot Did Not Establish

- ChatMap did not launch the workers.
- ChatMap did not record these worker sessions in its SQLite ledger.
- Wall-clock improvement over sequential review was not measured.
- Worker isolation was procedural rather than enforced by ChatMap.
- The individual raw worker reports were returned to the coordinator but were
  not persisted as separate ChatMap artifacts.
- No failure, timeout, cancellation, conflicting result, or writing-worker case
  was exercised.
- No semantic correctness claim follows merely from three workers agreeing.

## Minimal Reusable Assignment Contract

Every parallel worker should receive:

- role and bounded task
- pinned repository commit
- required context paths
- permitted tools
- explicit exclusions
- output format
- evidence requirement
- definition of done
- failure-reporting rule

For read-only review, each report should contain:

1. scope examined
2. verified facts with file evidence
3. recommendations labeled separately
4. uncertainty or disagreement
5. one proposed next action

The coordinator must deduplicate findings, preserve disagreements, name missing
workers, and avoid inventing evidence.

## Recommended Next Experiment

Use the existing lifecycle service in an isolated ChatMap home to record:

1. one coordinator assignment and working session
2. three successor assignments sharing the coordinator session
3. three distinct worker sessions and result artifacts
4. one synthesis assignment whose context names all three results
5. completion of the coordinator after synthesis
6. database reopen and `chainFrom()` verification

The actual workers may still be launched by an external runtime. This would
test whether ChatMap can durably describe a real parallel run before adding a
scheduler, new schema, or production orchestration behavior.

For any later writing pilot:

- give every worker a unique branch and worktree
- pin every worktree to the same baseline commit
- assign all Git integration to one coordinator
- merge sequentially
- never run two inbox orchestrators at once
