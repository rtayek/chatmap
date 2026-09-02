# ChatMap Independent Code-Review Handoff

> **Status: completed.** The review found malformed Ollama response handling
> and an ignored archive-staging Git result. Ollama validation was repaired in
> `031aba9`; the staging result remains a low-severity follow-up recorded in
> `working-context.md`.

**Prepared:** 2026-09-01  
**Repository:** `rtayek/chatmap`  
**Intended reviewer:** Claude Code  
**Mode:** read-only review; do not modify code

## Purpose

Perform a fresh, independent review of current ChatMap `master`. Determine
which previously reported problems still reproduce, identify important new
defects, and recommend one bounded repair for separate implementation.

The expected starting revision is
`1c8ae90d7d7ab71423daf657b60b9a32809cc2eb`. Verify the remote branch and
current CI before relying on that revision.

## First Steps

1. Fetch the remote and inspect `origin/master`.
2. Report the exact commit, branch, working-tree status, and current CI state.
3. Stop if the checkout is dirty or differs unexpectedly; do not discard work.
4. Read these current project documents:
   - `first-principles.md`
   - `design.md`
   - `implementation-notes.md`
   - `working-context.md`
5. Treat files under `handoffs/` as task transfers or history, not current
   authority unless `working-context.md` names one.

## Review Assignment

Review the current implementation for correctness, data safety, failure
handling, and architectural boundary violations. Tests are the functional
specification; inspect them alongside production code.

Prioritize:

- import identity, idempotency, provider provenance, and latest-chat selection;
- transaction atomicity and concurrency;
- preservation of dirty worktrees and partial worker output on every failure;
- propagation of Git, subprocess, storage, and provider failures;
- structured CLI and Ollama response validation;
- process timeout, interruption, and output-reader failure behavior;
- durable worker-lifecycle persistence and invalid-transition rejection;
- violations of the boundaries stated in `design.md`.

Recheck the archived findings in
`handoffs/archive/CHATMAP-HANDOFF-2026-08-23.md`, but treat each finding as a
hypothesis. Do not repeat it unless it still reproduces in current code.

Also look for important defects not mentioned in that handoff. Avoid cosmetic
style commentary, speculative framework proposals, and broad redesigns.

## Verification

Use the Gradle wrapper as authoritative:

```sh
./gradlew test
./gradlew check
```

If a command cannot run, report the exact blocker. Do not claim a test passed
unless it was actually executed.

Add small local diagnostic experiments only when needed to confirm a finding.
Do not retain generated files or alter tracked files.

## Constraints

- Do not edit, format, commit, push, merge, or delete anything.
- Do not create a repair branch or pull request.
- Do not redesign ChatMap as a general orchestrator.
- Do not implement A2A, semantic extraction, embeddings, or UI redesign.
- Do not treat the completed shell relay as ChatMap code.
- Preserve all user data and unrelated work.
- Prefer Java and the existing architecture; do not introduce Python tooling.
- Gradle command-line behavior is authoritative; do not require Buildship.

## Required Report

Lead with findings, ordered by severity. For every finding provide:

1. severity;
2. observable incorrect behavior or concrete risk;
3. exact file and code location;
4. evidence or reproduction path;
5. missing or inadequate test coverage;
6. the smallest reasonable repair.

Then report:

- archived findings that no longer reproduce;
- commands run and their results;
- unresolved questions requiring Ray's decision;
- exactly one recommended first repair.

If no important defect is found, say so plainly and describe the review
coverage rather than manufacturing findings.

## Definition of Done

The review is complete when another reviewer can independently verify every
reported defect and Ray can select one bounded repair without rereading the
entire repository.

Return the report to Ray. Codex will then verify the important findings,
remove duplicates or unsupported claims, and prepare the selected repair
assignment.
