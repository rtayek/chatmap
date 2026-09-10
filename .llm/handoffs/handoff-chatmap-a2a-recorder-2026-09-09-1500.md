# Handoff: A2A Recorder

From: chatmap (planning session)
Date: 2026-09-09 15:00
Repo: github.com/rtayek/chatmap
Local clone: C:\Users\ray\eclipse-workspace\cjatmanager

## Context

The experiment/a2a branch (Linux Foundation A2A protocol) is already merged
to master, with a working server, two clients, and a round-trip task
lifecycle confirmed including INPUT_REQUIRED continuation. See
a2a-experiment-findings.md for the full write-up.

The findings doc recommends building a recorder next: a component that
converts externally-visible A2A exchanges into ChatMap ledger entries.

## Task

Build the A2A recorder.

## Constraints

- ChatMap is a continuity layer, not an orchestrator. The recorder observes
  and logs externally-visible A2A exchanges into the ledger; it does not
  drive, route, or interpret task content. Same posture as HandoffWatcher,
  which collects handoff files into an inbox without interpreting them.
- Follow existing worker-lifecycle architecture (canonical per
  implementation-notes.md).
- TDD/DDD preferred.
- Naming: kebab-case files, lowerCamelCase identifiers, no underscores in
  string identifiers, no PascalCase, fields at bottom of class.
- Java 25 / Gradle / JavaFX / SQLite (FTS5).

## Decision

- Recorder is read-only with respect to A2A traffic: it records, it does not
  act as an orchestrator or router.

## Open

- Exact ledger entry schema for A2A exchanges (map from A2A task states to
  ChatMap ledger fields) is not yet defined -- propose one and flag for
  review rather than assuming.
- Where the recorder taps into the A2A server/client (middleware vs.
  explicit logging calls) is undecided.

## Output

Provide the result as a downloadable .md file per the standing rule (no
exceptions): handoff-<from-project>[-to-<to-project>]-<YYYY-MM-DD-HHMM>.md,
plain ASCII only.
