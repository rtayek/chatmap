# Semantic Extraction Handoff

## Purpose

This handoff captures the durable design ideas for using AI chats as raw material that can be compressed into stable project knowledge.

The central idea is that chats are transient working sessions. Useful knowledge should be extracted from them into durable files such as Markdown, structured metadata, or other project-owned artifacts.

## Core Thesis

AI chat history is valuable, but raw chat logs are a poor long-term knowledge store.

A useful chat manager should help convert long conversations into:

- stable decisions
- architectural state
- constraints
- unresolved questions
- reusable patterns
- next actions
- project handoff documents
- durable Markdown files

The goal is not to preserve every token. The goal is to preserve meaning.

## User Context

The user has many chats spread across multiple LLMs and projects.

The user wants to manage chats as project knowledge rather than treating them as disposable transcripts.

Important preferences:

- prefer Markdown as a durable project format
- prefer semantic compression over raw chronology
- prefer stable decisions and active models over conversational noise
- prefer paste-safe shell commands
- prefer Java and structured design
- use code as documentation where possible
- tests act as functional specification

## Semantic Compression Principle

A good extraction should preserve:

- current project purpose
- stable architectural decisions
- active constraints
- domain model
- important naming conventions
- reusable patterns
- unresolved questions
- next implementation steps

A good extraction should discard:

- repeated explanations
- chronology unless important
- conversational filler
- dead-end reasoning
- temporary commands after they are no longer relevant
- implementation trivia recoverable from Git
- speculation that did not become a decision

When uncertain, prefer omission unless the item changes future work.

## Chat Manager Concept

A chat manager is a workflow or tool that turns long AI conversations into durable project state.

Responsibilities:

1. Import or ingest chat material.
2. Identify durable semantic content.
3. Separate signal from transcript noise.
4. Write or update canonical project files.
5. Keep handoffs concise enough to be useful.
6. Preserve raw chat only when needed for traceability.
7. Support export to Markdown.

The chat manager should not merely summarize. It should extract state.

## Suggested Canonical Files

Different projects may use different filenames, but the recurring useful files are:

- `README.md`
- `design.md`
- `architecture.md`
- `handoff.md`
- `working-context.md`
- `todo.md`
- `decisions.md`
- `patterns.md`

The files should not all duplicate each other.

Suggested split:

### `design.md`

Concise current design.

Contains:

- purpose
- MVP scope
- architecture
- data model
- key design rules
- technology choices
- build order

### `handoff.md`

Detailed context for another AI or developer.

Contains:

- current project state
- stable decisions
- constraints
- non-goals
- known risks
- next tasks
- references to important files

### `working-context.md`

Short-lived current work state.

Contains:

- current branch/task
- recent changes
- current failing tests or issues
- immediate next action

### `decisions.md`

Durable decisions only.

Contains:

- decision
- rationale
- consequences
- date or context if useful

### `patterns.md`

Reusable implementation/design patterns.

Contains:

- naming conventions
- testing patterns
- repository/service boundaries
- UI state rules
- import/export contracts

## Extraction Template

Use this structure when extracting a long chat:

```md
# Chat Extraction

## Project

Name and one-sentence purpose.

## Current State

What is true now.

## Stable Decisions

Decisions that should survive the chat.

## Architecture

Current system shape.

## Data Model

Important entities and relationships.

## Constraints

Rules and boundaries.

## Non-Goals

What should not be built yet.

## Open Questions

Things not yet settled.

## Next Actions

Concrete next steps.

## Suggested File Updates

Which canonical files should be changed and how.
```

## ChatMap-Specific Relevance

The ChatMap project is itself an implementation of these ideas.

ChatMap’s MVP purpose:

> Turn exported or pasted AI chats into organized, searchable, exportable project knowledge.

Core ChatMap workflow:

```text
Import → Normalize → Store → Search → Organize → Export
```

ChatMap should eventually support semantic extraction as one of its main values.

The first implementation does not need AI. It can start with deterministic import, search, organization, and Markdown export.

Later, LLM-assisted extraction can produce higher-level handoffs, but the non-AI workflow should remain useful.

## Recommended Semantic Extraction Modes

ChatMap should eventually support multiple export modes.

### Raw Transcript Export

Preserves full conversation text.

Use when:

- exact wording matters
- debugging
- legal/audit needs
- later reprocessing

### Clean Markdown Export

Makes a chat readable outside the AI platform.

Use when:

- saving a useful discussion
- sharing with another person
- committing to a repo

### Deterministic Handoff Export

No LLM required.

Uses metadata and message structure.

Includes:

- project name
- chat titles
- source platform
- dates
- tags
- first user message
- last assistant message
- selected notes

### Semantic Handoff Export

LLM-assisted.

Extracts:

- decisions
- constraints
- active architecture
- unresolved questions
- next actions
- suggested canonical file updates

This should come later, after the deterministic pipeline works.

## MVP Advice

Do not start by trying to solve all chat management.

Build a boring pipeline first:

1. Import a chat.
2. Store it.
3. Search it.
4. Assign it to a project.
5. Tag it.
6. Export it as Markdown.
7. Export a deterministic handoff.

After that works, add smarter semantic extraction.

## Important Design Warnings

### Do not treat raw chats as canonical project state

Chats are work sessions. They are not documentation.

### Do not over-preserve chronology

Chronology is usually less important than final decisions and active constraints.

### Do not depend on one AI platform format

Each platform export may differ.

Normalize into an internal model.

### Do not require AI for basic value

A useful manager can search, organize, and export without an LLM.

### Do not build the pretty canvas first

Visual organization is useful only after import, storage, search, and export work.

## Possible Future Data Structures

Besides Markdown, ChatMap may eventually use structured files.

Candidates:

- JSON for interchange
- SQLite for local state
- Markdown for human-readable project memory
- YAML front matter for metadata
- JSONL for message streams
- graph/tree structures for chat relationships

Important principle:

- Markdown is for people.
- JSON is for machines.
- SQLite is for local querying.
- Git is for durable project history.

## Possible Semantic Unit Model

A semantic extraction system could identify units such as:

```text
Decision
Constraint
Question
Task
Pattern
Fact
Risk
Reference
Artifact
```

Each unit could have:

```text
id
type
text
sourceChatId
sourceMessageIds
projectId
tags
createdAt
confidence
```

This would allow extracted knowledge to outlive the original chat.

## Relationship to `.md` Files

The project is not only about Markdown files, but Markdown is the simplest durable user-facing format.

Markdown is valuable because it is:

- readable
- editable
- Git-friendly
- tool-neutral
- easy to export
- easy to review

Structured metadata can come later.

A practical design is:

- SQLite stores the working database.
- Markdown exports preserve human-readable knowledge.
- JSON preserves machine-readable interchange.
- Git stores canonical project memory.

## Next Implementation Direction

For ChatMap, the practical direction is:

1. Stabilize basic import/search/export UI.
2. Keep `design.md` concise.
3. Keep `handoff.md` detailed.
4. Add project/tag UI only after basic UI is stable.
5. Add deterministic handoff export before AI extraction.
6. Add LLM-assisted semantic extraction only after deterministic handoffs are useful.

## Short Summary

The durable insight is:

> Chats are transient reasoning traces. Projects need extracted semantic state.

ChatMap should help turn messy AI conversation history into structured, searchable, exportable knowledge.

Build the boring pipeline first. Add semantic intelligence after the pipeline works.
