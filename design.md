# ChatMap Design

## Purpose

ChatMap is a local desktop application for managing AI chat histories.

The MVP turns imported chats into organized, searchable, exportable project knowledge.

Core workflow:

```text
Import → Normalize → Store → Search → Organize → Export
```

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

Current import behavior:

```text
Plain text → one Chat → one Message
Markdown   → one Chat → one Message
ChatGPT JSON → flattened Messages with rawJson preserved
ChatGPT archive (ZIP) → many Chats from an exported conversations file
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
