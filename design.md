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
* reading live web chats from various Large Language Models.
* persisting chats in a databases.
* searching messages in the databases
* organizing chats with projects and tags
* exporting chats and handoffs as Markdown

## Architecture

```text
Java desktop app
│
├── ui
│   └── Java Swing or JavaFX
│
├── service
│   ├── import orchestration
│   ├── project/tag management
│   ├── search
│   ├── export orchestration
│   └── optional summary/tag generation
│
├── importer
│   ├── PlainTextImporter
│   ├── MarkdownImporter
│   ├── ChatGptJsonImporter
│   └── ChatGptArchiveImporter
│
├── backend
│   └── optional web, CLI-history, prompt-execution, and Claude-summary adapters
│
├── exporter
│   ├── MarkdownExporter
│   └── HandoffExporter
│
├── storage
│   ├── repositories
│   └── SQLite schema
│
├── cli
│   └── consolidate, summarize, and archive-import entry points
│
├── config
│   └── ChatMapPaths (home, database, and transcript directory resolution)
```

## Design Rules

* UI does not parse files.
* UI does not talk SQL.
* Importers do not write to storage.
* Repositories do not know source file formats.
* Exporters do not query the database directly.
* Repositories do not manage connection lifecycle; multi-repository transactions use `TransactionRunner`.
* AI is optional and not required for core MVP behavior.

## Technology Choices

* Language & Runtime: Java 25 (Gradle Kotlin DSL toolchain)
* UI: JavaFX 25.0.1
* Storage: SQLite 3.53+
* Search: SQLite FTS5
* Browser Automation: Chrome DevTools Protocol
* Quality Assurance: JUnit 5, JaCoCo, Checkstyle, PMD, SpotBugs (`./gradlew check`)

## Naming Rules

Use standard Java type naming and lower camel case everywhere else.

```text
Java classes, records, interfaces:
UpperCamelCase

Java methods, fields, parameters, locals, constants, and enums:
lowerCamelCase

Database tables: UpperCamelCase

Database columns: LowerCamelCase
```

Examples:

```text
PlainTextImporter
MarkdownExporter
SearchRepository

importText
fallbackTitle
text
rawJson

Projects
Chats
Messages
MessageFts
ChatTags

projectId
chatId
createdAt
updatedAt
importedAt
enum Foo {bar,baz}
```

No underscores or spaces in Java identifiers or database identifiers, use '-' instead.

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
chatSummaries
```

`messages` stores durable message rows.

`chatSummaries` stores optional AI-generated summaries; empty when AI is unused.

`messageFts` is an external-content FTS5 table synchronized by triggers in `schema.sql`.

Repository tests must verify that insert, update, and delete operations keep FTS search correct.

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

## Search

Search uses SQLite FTS5 for message text.

`SearchRepository` owns queries involving:

* message text
* project filter
* tag filter
* archived filter

Results are returned in deterministic chat import order. Duplicate message matches produce one result per chat.

## Current Implementation

```text
1. Domain model and SQLite storage
2. FTS5 message search with synchronization triggers
3. Plain text, Markdown, ChatGPT JSON, and ChatGPT archive (ZIP) import
4. Project and tag organization
5. Single-chat Markdown export
6. Deterministic project handoff export
7. JavaFX list/detail, import, search, and export workflow
```

## Supported Live Provider & Automation Capabilities

These provider integrations and browser automation tools are supported in the codebase:

* live web readers for Claude, ChatGPT, and Gemini (`Source` values
  `claudeWeb`, `chatGptWeb`, `geminiWeb`)
* browser automation for those readers (Chrome DevTools Protocol)
* CLI-history readers (Claude Code, Codex, Gemini)
* prompt execution against local CLIs, with automatic chat recording
* Claude-generated chat summaries (`chatSummaries`)

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
2. Store it in SQLite.
3. Search its message text.
4. Assign it to a project.
5. Add tags.
6. Export clean Markdown.
7. Export a deterministic project handoff.
```
