---
id: CM-DESIGN-01
lifecycle: durable
status: active
provenance: git-history
---
# ChatMap Design

## Purpose

ChatMap is a local desktop application for managing AI chat histories.

The MVP turns imported chats into organized, searchable, exportable project knowledge.

Core workflow:

```text
Import → Normalize → Store → Search → Organize → Export
```

The deterministic MVP provides the substrate. ChatMap's longer-term purpose is
to extract durable semantic knowledge from conversations and keep that
knowledge current as projects evolve, while preserving the provenance and
history of every accepted knowledge object. See `first-principles.md` for the
stable principles governing that work.

## Project Memory and Instruction Ownership

ChatMap owns and tests its project-memory model inside this repository. That
includes the distinction between durable knowledge and working state,
document-local YAML metadata, durable project documents, working context, and
the validation experiments used to determine whether those facilities work.

The governing instruction entry point is a separate concern. Dotmdfiles owns
the standardized shared portion of `AGENTS.md`; ChatMap owns its project-context
portion and its durable design documents. The System project records
cross-project architectural decisions and the project registry. None of those
projects becomes the authority for ChatMap's internal application design.

The former mandatory chain through `.llm/index.md` is being retired because
clients do not reliably follow multi-file instruction chains. The target entry
path is `CLAUDE.md` to a self-contained `AGENTS.md`, whose project-context
section names exact project documents when they are required. `.llm/index.md`
is transitional and may be removed only after the consolidated `AGENTS.md` has
been deployed and the manifest and validator have been updated in the same
change.

Substantial project knowledge may remain under `.llm/`. Working context and
handoffs do not silently override governing instructions or durable decisions.

## Coordination Boundary

ChatMap's core is the durable ledger and status system. Optional coordination
facilities may select workers, submit assignments, validate responses, route
results, and pause for human decisions, but they must record their work through
the same durable model. Manual operation remains a supported workflow.

A2A is the selected protocol for standards-based agent-to-agent work. MCP is
complementary for connecting applications to tools and data; it is not an
alternative agent-to-agent protocol. ACP has been absorbed into A2A, and ANP is
deferred. The bounded implementation is contained in the
`chatmap.a2a.experiment` package on `master`. It uses ChatMap's application
services and an isolated temporary ChatMap home to record externally visible
A2A work in the worker-lifecycle ledger. It does not touch normal ChatMap data
or the UI, and it is not a general orchestrator framework. Production packages
must not depend on the experimental package.

A2A is an adapter boundary, not ChatMap's domain model. Explicit translation
maps A2A messages, tasks, statuses, history, and artifacts to ChatMap
assignments, lifecycle events, and durable artifacts. The mappings may be
lossy: QUEUED is not identical to SUBMITTED, WAITING_FOR_DECISION is broader
than INPUT_REQUIRED, and A2A same-task continuation is not a ChatMap successor
assignment. A2A SDK types must remain outside the ChatMap domain layer.

Successful transport, task completion, and durable storage do not establish
semantic correctness. External agent responses remain evidence until evaluated
against an explicit acceptance contract. Prefer deterministic schema and value
checks when the required meaning can be stated structurally; otherwise preserve
the response and its provenance for human review. A model must not certify its
own output as trusted knowledge merely because it followed the protocol.

Decision escalation follows the caller chain. A worker reports an unresolved
decision to its caller. Each caller either resolves it within its authority or
propagates it to its own caller. No fixed manager automatically resolves every
decision, and human review remains available when no lower caller has
authority.

Instruction and skill provenance belongs in the durable lifecycle record, but
the authoritative instruction and skill bodies remain in Git-tracked files and
skill packages. The first bounded provenance experiment must use the existing
assignment fields before any schema expansion. `contextAndFiles` can record the
governing instruction path, exact content hash, optional Git revision, skill
identity, skill path, exact content hash, and optional Git or declared version.
The existing tools and constraints fields record the execution envelope.

Content hashes identify the exact material used. Git revisions preserve source
history. Declared versions are optional human-facing labels. A normalized
assignment-to-skill relation is justified only after a bounded experiment
demonstrates a concrete query or multi-skill requirement that the existing
record cannot answer.

The completed Bourne-shell relay experiment is preserved in the `rtayek/bin`
repository on branch `archive/llm-relay`. It demonstrated deterministic
delegation, validation, escalation, and artifact preservation without creating
a permanent ChatMap subsystem.

## MVP Scope

The MVP supports:

* importing plain text, Markdown, and ChatGPT JSON files
* reading live web chats from various Large Language Models
* persisting chats in a database
* searching messages in the database
* organizing chats with projects and tags
* exporting chats and handoffs as Markdown

## Design Rules

* UI does not parse files.
* UI does not talk SQL.
* Importers do not write to storage.
* Repositories do not know source file formats.
* Exporters do not query the database directly.
* Repositories do not manage connection lifecycle; multi-repository transactions use `TransactionRunner`.
* AI is optional and not required for core MVP behavior.
* External protocol SDK types do not cross adapter boundaries into the domain model.

These rules are the actual contract. Breaking one is a design regression, not a
refactor. See `implementation-notes.md` for the package layout, naming
conventions, and library choices that currently satisfy these rules — those
can change without this file changing.

## Core Data Model

### Project

```text
Project
- id
- name
- description
- createdAt
- updatedAt
```

### Chat

```text
Chat
- id
- projectId
- source
- title
- createdAt
- updatedAt
- importedAt
- archived
- externalConversationId
- sourceUri
- contentHash
- sourceUpdatedAt
- lastImportedAt
```

A chat belongs to zero or one project in the MVP.

### Message

```text
Message
- id
- chatId
- role
- text
- sequence
- timestamp
- rawJson
```

`text` is the normalized searchable text.

`rawJson` preserves the original source payload when available.

### Tag

```text
Tag
- id
- name
```

### chatTags

The chat-to-tag association. This is a join table only; there is no `ChatTag`
domain type.

```text
chatTags
- chatId
- tagId
```

### ChatSummary

An optional, AI-generated summary for a chat. Additive only: never edits the
chat or its messages.

```text
ChatSummary
- id
- chatId
- summary
- generatedBy
- generatedAt
- contentHash
```

## Import

All importers produce normalized chat data.

This section covers format-based importers only. Live/local acquisition via
the six-source ChatProvider system (CLI-history readers for Claude Code,
Codex, and Gemini; live web-CDP readers for Claude, ChatGPT, and Gemini) is
documented in `implementation-notes.md` under "Supported Live Provider &
Automation Capabilities."

Current import behavior:

```text
Plain text → one Chat → one Message
Markdown   → one Chat → one Message
ChatGPT JSON → flattened Messages with rawJson preserved
ChatGPT archive (ZIP) → many Chats from an exported conversations file
Gemini Workspace Takeout (extracted directory) → one Chat per conversation JSON text file
```

Importers do not persist data directly. Services pass imported data to repositories.

## Export

Markdown export is core.

Exporters receive fully hydrated export models from `ExportService`.

Export targets:

* single chat
* deterministic no-LLM handoff

The no-LLM handoff is structured extraction, not semantic compression.

It includes project metadata, chat list, tags, dates, source platform, first/last messages, and optional notes.

## Storage

Chats live in a durable local store, not a cloud service — this is a design
decision, not an implementation detail: users own their data as files on
their own disk.

Main tables:

```text
projects
chats
messages
messageFts
tags
chatTags
chatSummaries
```

`chatSummaries` is empty when AI is unused.

Repository tests must verify that insert, update, and delete operations keep
search results correct.

## Search

Search is full-text over message text. See `implementation-notes.md` for the
specific engine and version.

`SearchRepository` owns queries involving:

* message text
* project filter
* tag filter
* archived filter

Results are returned in deterministic chat import order. Duplicate message matches produce one result per chat.

## Non-Goals for MVP

Not built, and out of scope for the deterministic MVP:

* cloud accounts
* multi-user collaboration
* payments
* mobile app
* advanced analytics
* complex model comparison
* sophisticated infinite canvas
* AI-required handoff generation

## MVP Success Test

The MVP succeeds when a user can:

```text
1. Import a chat.
2. Store it in the local store.
3. Search its message text.
4. Assign it to a project.
5. Add tags.
6. Export clean Markdown.
7. Export a deterministic project handoff.
```
