# ChatMap Design

## Purpose

ChatMap is a local desktop application for managing AI chat histories.

The MVP turns exported or pasted chats into organized, searchable, exportable project knowledge.

Core workflow:

```text
Import → Normalize → Store → Search → Organize → Export
```

## MVP Scope

The MVP supports:

* importing plain text, Markdown, pasted conversations, and later ChatGPT JSON
* storing chats in SQLite
* searching message text with SQLite FTS5
* organizing chats with projects and tags
* exporting chats and handoffs as Markdown

The MVP does not require AI, Python, cloud sync, browser automation, or a visual canvas.

## Architecture

```text
Java desktop app
│
├── ui
│   └── JavaFX screens and later card canvas
│
├── service
│   ├── import orchestration
│   ├── project/tag management
│   ├── search
│   └── export orchestration
│
├── importer
│   ├── PlainTextImporter
│   ├── MarkdownImporter
│   └── ChatGptJsonImporter
│
├── exporter
│   ├── MarkdownExporter
│   └── HandoffExporter
│
├── storage
│   ├── repositories
│   └── SQLite schema
│
└── ai
    └── optional future LLM integration
```

## Design Rules

* UI does not parse files.
* UI does not talk SQL.
* Importers do not write to storage.
* Repositories do not know source file formats.
* Exporters do not query the database directly.
* AI is optional and not required for MVP behavior.

## Technology Choices

* Language: Java
* UI: JavaFX
* Storage: SQLite
* Search: SQLite FTS5
* Tests: JUnit with temporary SQLite databases
* AI: optional later, Java client first
* Python: optional later only if Java SDKs become limiting

## Naming Rules

Use standard Java type naming and lower camel case everywhere else.

```text
Java classes, records, interfaces:
UpperCamelCase

Java methods, fields, parameters, locals, constants:
lowerCamelCase

Database tables and columns:
lowerCamelCase
```

Examples:

```text
PlainTextImporter
MarkdownExporter
SearchRepository

importText
fallbackTitle
messageText
rawJson

projects
chats
messages
messageFts
chatTags

projectId
chatId
createdAt
updatedAt
importedAt
```

No underscores or spaces in Java identifiers or database identifiers.

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
```

A chat belongs to zero or one project in the MVP.

### Message

```text
Message
- id
- chatId
- role
- messageText
- sequence
- timestamp
- rawJson
```

`messageText` is the normalized searchable text.

`rawJson` preserves the original source payload when available.

### Tag

```text
Tag
- id
- name
```

### ChatTag

```text
ChatTag
- chatId
- tagId
```

## Storage

SQLite is the durable local store.

Main tables:

```text
projects
chats
messages
messageFts
tags
chatTags
```

`messages` stores durable message rows.

`messageFts` is an external-content FTS5 table synchronized by triggers in `schema.sql`.

Repository tests must verify that insert, update, and delete operations keep FTS search correct.

## Import

All importers produce normalized chat data.

Initial import behavior:

```text
Plain text → one Chat → one Message
Markdown   → one Chat → one Message
Pasted text → PlainTextImporter path
ChatGPT JSON → flattened Messages with rawJson preserved
```

Importers do not persist data directly. Services pass imported data to repositories.

## Export

Markdown export is core.

Exporters receive fully hydrated export models from `ExportService`.

Export targets:

* single chat
* selected chats
* project
* deterministic no-LLM handoff

The no-LLM handoff is structured extraction, not semantic compression.

It includes project metadata, chat list, tags, dates, source platform, first/last messages, and optional notes.

## Search

Search uses SQLite FTS5 for message text.

`SearchRepository` owns queries involving:

* message text
* chat title
* project filter
* tag filter
* archived filter

Initial ranking:

```text
1. exact title matches
2. message matches
```

## Build Order

```text
1. Domain model
2. SQLite schema with FTS5 and triggers
3. Repository tests
4. PlainTextImporter
5. MarkdownExporter
6. MarkdownImporter
7. Project/tag management
8. No-LLM handoff export
9. ChatGPT JSON importer
10. Simple JavaFX list/detail UI
11. Simple card canvas
12. Optional Java LLM integration
13. Optional Python helper
```

## Non-Goals for MVP

Do not build first:

* live ChatGPT, Claude, or Gemini sync
* browser automation
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
2. Store it in SQLite.
3. Search its message text.
4. Assign it to a project.
5. Add tags.
6. Export clean Markdown.
7. Export a deterministic project handoff.
```
