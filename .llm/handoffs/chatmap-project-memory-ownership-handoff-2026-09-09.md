---
id: CM-HANDOFF-PROJECT-MEMORY-OWNERSHIP-2026-09-09
lifecycle: working
status: active
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

## Next Validation

Test the current arrangement independently with:

- Claude Code
- Codex
- Anti-Gravity

Each should identify the same project purpose, architectural boundaries,
current work, and material that must not be read automatically.

Compare their answers for substantive agreement. Minor wording differences are
acceptable; missed boundaries or contradictory conclusions are not.

## Follow-up

If the discovery test succeeds:

1. Record the settled ownership boundary in `.llm/design.md`.
2. Keep the implementation and validation machinery in ChatMap.
3. Report the proven general pattern to the System project.
4. Avoid turning the System project into the authority for unproven ChatMap
   design choices.

## Not Yet Decided

- Whether every project should use the same YAML fields.
- Whether every project needs a JSON manifest.
- Which validation rules are general enough to move into System.
- Whether the current ChatMap arrangement should become a reusable template.
