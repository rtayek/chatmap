---
id: CM-EVO-01
lifecycle: durable
status: active
provenance: git-history
---
# ChatMap Evolution

This document records adopted structural changes in ChatMap. Git history and
archived handoffs preserve detailed chronology and rejected proposals.
`first-principles.md` and `design.md` remain authoritative when this summary
is incomplete.

## Deterministic Conversation Substrate

ChatMap began with a deterministic local workflow:

```text
Import -> Normalize -> Store -> Search -> Organize -> Export
```

SQLite, FTS5, explicit repositories, and JavaFX provide a useful application
without requiring an LLM.

## Semantic-Knowledge Direction

The project purpose expanded from managing transcripts to mining conversations
for durable, connected, reviewable knowledge. Raw conversations remain preserved
as evidence. Semantic extraction, provenance, human acceptance, and keeping
accepted knowledge current are the longer-term direction; they are not yet a
completed subsystem.

## Optional Model and Provider Integration

CLI-history readers, live-web acquisition, and optional LLM prompting were added
around the deterministic core. Model output remains optional and must not become
trusted knowledge merely because transport or execution succeeded.

## Durable Work Continuity

The worker-lifecycle vertical slice added durable assignments, sessions,
transitions, artifacts, semantic handoffs, retirement, and successor chains.
The lifecycle and soak tests prove structural persistence, not preservation of
meaning across handoffs.

## Bounded A2A Adapter

A bounded A2A experiment proved Agent Card discovery, task states, same-task
continuation, artifacts, a local Ollama-backed worker, and projection into the
existing lifecycle ledger through an isolated ChatMap home. A2A remains an
adapter boundary. ChatMap is a durable ledger and status system, not a general
orchestrator. Successful A2A completion does not establish semantic correctness.

## Project-Context Metadata Pilot

Project guidance and handoffs were organized under `.llm/`. The pilot separates:

- Markdown bodies: project knowledge and working state
- YAML front matter: metadata intrinsic to one document
- `manifest.json`: repository-wide discovery and validation rules

The existing semantic bodies remain authoritative. The metadata layer must not
compress or replace them. Client-specific entry points remain outside this
pilot.
