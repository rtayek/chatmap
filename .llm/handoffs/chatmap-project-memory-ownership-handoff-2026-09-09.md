---
id: CM-HANDOFF-PROJECT-MEMORY-OWNERSHIP-2026-09-09
lifecycle: working
status: retired
provenance: chat-session-2026-09-09
---

# ChatMap Project-Memory Ownership Handoff

## Purpose

Record the boundary between ChatMap and the higher-level System project for
project memory, metadata, and discovery.

## Settled Direction

ChatMap owns and tests its project-memory facilities, including:

- The distinction between durable semantic knowledge and working state.
- YAML front matter used as document-level metadata.
- The JSON manifest used for repository-level control and validation.
- Discovery through `.llm/index.md`.
- Validation and experiments that determine whether this organization works.

The System project may later generalize practices that ChatMap has demonstrated
to be useful across multiple projects. It should not prematurely dictate
ChatMap's internal organization.

## Current Discovery Chain

`CLAUDE.md` tells Claude to read `AGENTS.md`.

`AGENTS.md` contains the general agent behavior rules and tells agents to read
`.llm/index.md`.

`.llm/index.md` routes agents to `.llm/human.md`, `.llm/persona.md`, and the
authoritative ChatMap project documents.

## Validation Result

The read-only discovery test was completed with Claude Code, Codex, and
Anti-Gravity. All three produced substantive agreement on ChatMap's purpose,
project-memory ownership, the System boundary, current work, undecided
questions, and the `.chatmap-local/` exclusion.

Anti-Gravity reported `AGENTS.md` as its first project-guidance file and then
followed `.llm/index.md`. Codex recovered the correct material but reported
reading `.llm/index.md` before `AGENTS.md`. Claude described the intended
chain but omitted the requested actual reading order. The experiment therefore
validates semantic discovery across all three clients, but does not fully prove
the exact automatic startup order for every client.

The test also exposed stale operational documentation: the completed metadata
audit was still listed as active in `.llm/working-context.md`.

## Follow-up Completed

- The ownership boundary is recorded in `.llm/design.md`.
- The discovery-test result and completed metadata audit are recorded in
  `.llm/working-context.md`.
- Project-memory implementation and validation remain in ChatMap.
- System may generalize the proven pattern later, without becoming the authority
  for unproven ChatMap design choices.

## Not Yet Decided

- Whether every project should use the same YAML fields.
- Whether every project needs a JSON manifest.
- Which validation rules are general enough to move into System.
- Whether the current ChatMap arrangement should become a reusable template.
