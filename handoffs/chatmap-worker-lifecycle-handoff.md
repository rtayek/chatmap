# ChatMap Worker-Lifecycle Experiment

**Date:** 2026-08-26  
**Repository:** `rtayek/chatmap`  
**Branch:** `feature/worker-lifecycle`

## Purpose

Implement the smallest end-to-end experiment showing that ChatMap can preserve continuity between independent worker sessions.

The guiding boundary is:

> Workers own execution. ChatMap owns continuity across workers and work sessions.

This is an experimental vertical slice, not a commitment to a large orchestration architecture.

## 1. Task

Inspect the existing ChatMap code and implement one persisted worker lifecycle:

```text
structured assignment
    → work session
    → lifecycle state changes
    → semantic handoff
    → retired session
    → successor assignment/session
```

Use a fake or manually controlled worker adapter for this first experiment. Do not build a general agent launcher.

Adapt the implementation to the repository’s existing architecture and naming conventions. Do not invent a parallel application structure.

## 2. Context and required concepts

### Assignment input

An assignment must preserve these five fields:

1. Task
2. Context and files
3. Available tools
4. Constraints and permissions
5. Definition of done

Also preserve explicit escalation behavior, such as:

> If blocked, stop safely, preserve partial work, and report the decision or resource required.

### Work record

ChatMap must be able to answer:

1. Who is doing what?
2. Which workers are busy or finished?
3. What did they produce?
4. Where is the result?
5. Does anything require the user’s decision?
6. Which result should be passed to another worker?

A work record therefore needs, at minimum:

- stable identity;
- worker identity or description;
- assignment;
- current lifecycle state;
- associated session;
- produced artifacts and their locations;
- decision required from the user, if any;
- semantic handoff;
- predecessor/successor relationship.

### Lifecycle states

Use a small explicit state model. Start from:

```text
QUEUED
WORKING
WAITING_FOR_DECISION
COMPLETED
FAILED
CANCELLED
RETIRED
```

Adjust the exact division between assignment state and session state if the existing design suggests a cleaner model. Preserve `WAITING_FOR_DECISION` as a state distinct from failure.

Invalid transitions should be rejected rather than silently accepted.

### Semantic handoff

A completed session’s handoff should preserve:

- work completed;
- important decisions and reasons;
- artifacts and locations;
- unresolved problems;
- required user decisions;
- recommended next action;
- the five assignment inputs for the successor, when known.

A successor must be traceable to the session or assignment that produced it.

## 3. Tools and working method

- Inspect the repository, recent handoffs, existing persistence model, services, CLIs, and tests before designing.
- Use the existing Java and SQLite conventions.
- Treat the Gradle command line and wrapper as authoritative.
- Use the existing test organization and naming style.
- Keep Eclipse support working, but do not introduce Buildship as a requirement.
- Prefer expressive code and tests over explanatory comments.
- Add a small CLI or similarly lightweight entry point that demonstrates the lifecycle without requiring a new GUI.
- Use a fake or manual worker boundary so tests remain deterministic.

## 4. Constraints and permissions

- Work only on `feature/worker-lifecycle`.
- Preserve all unrelated user changes.
- Do not modify `master`.
- Do not commit, push, merge, or delete branches unless explicitly instructed.
- Do not redesign the existing chat-import or search system.
- Do not damage, replace, or migrate the user’s existing ChatMap database destructively.
- Schema changes must be additive and safe.
- Persistence tests must use isolated temporary test data.
- Do not require OpenClaw or MyClaw.
- Do not implement provider-specific automation.
- Do not automate browser-based chats.
- Do not implement background export ingestion in this assignment.
- Do not attempt to calculate an optimal context-size threshold.
- Do not build scheduling, reminders, routing engines, permissions frameworks, or a general agent harness.
- Do not perform a broad UI redesign.

If the existing architecture makes this scope unsafe or requires a major refactor, stop and report:

- the architectural conflict;
- the smallest available alternatives;
- which decision is required from the user;
- any partial work already produced.

## 5. Definition of done

The experiment is complete when all of the following are true:

- A structured assignment containing all five required inputs can be created and persisted.
- A work session can be associated with that assignment and a worker identity.
- Valid lifecycle transitions can be recorded.
- Invalid lifecycle transitions are rejected and tested.
- `WAITING_FOR_DECISION` can record the question, reason, and preserved partial work.
- Artifacts and their locations can be attached to the work record.
- A semantic handoff can be stored for a completed session.
- The completed session can be retired without deleting its history.
- A successor assignment or session can be created and linked to its predecessor.
- The complete chain can be retrieved after reopening the persistence layer.
- A minimal CLI or equivalent demonstration exercises the vertical slice.
- Existing tests still pass.
- New focused unit and persistence tests cover the lifecycle.
- The Gradle build succeeds.
- Eclipse project generation/configuration, if currently supported by the repository, still succeeds.
- No existing user database or unrelated repository content is modified destructively.

At minimum, run:

```sh
./gradlew test
```

Also run the repository’s established verification tasks discovered during inspection, including Eclipse generation if it is part of the current workflow.

## 6. Final report

When finished, report:

1. What you implemented.
2. Files added or changed.
3. Persistence/schema changes.
4. The lifecycle and transition rules.
5. How to run the demonstration.
6. Tests and verification commands run, with results.
7. Design compromises or unresolved questions.
8. Anything requiring the user’s decision.
9. Whether you recommend continuing, revising, or discarding this experiment.

Do not expand the experiment merely because additional automation seems useful. Stop after the first complete vertical slice.
