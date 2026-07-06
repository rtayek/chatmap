# ChatMap MVP Handoff

## Project Summary

ChatMap is a local desktop application for importing, organizing, searching, and exporting AI chat histories.

The purpose of ChatMap is to help developers manage AI conversations across multiple LLMs.

The MVP is not a universal multi-AI control center. The MVP is a practical tool that makes exported AI chats usable.

Core value:

* Import AI chat history
* Normalize chats into a clean internal model
* Store chats locally
* Organize chats into projects and tags
* Search conversations
* Export useful Markdown handoffs

## MVP Goal

Build a local desktop app that supports this workflow:

```text
Import → Normalize → Store → Organize → Search → Export
```

The first working version should prove that ChatMap can turn scattered AI conversations into organized, searchable, reusable project knowledge.

## Stable Design Decisions

### Main Application Language

Use Java for the main application.

Java owns:

* UI
* domain model
* import pipeline
* storage
* search
* project/tag organization
* Markdown export
* initial LLM integration, if needed

Rationale:

* Java fits the developer’s preferences and experience.
* Java works well with Eclipse.
* Java is appropriate for structured, long-lived desktop software.
* Java SDKs or plain HTTP calls are sufficient for MVP-level LLM features.

### Python

Python is optional and deferred.

Do not make Python part of the core MVP path.

Python may be useful later for:

* experimental AI features
* SDK features not yet convenient from Java
* prototyping prompt pipelines
* advanced model workflows

But the MVP must work without Python.

If Python is eventually used, prefer calling the exact Python executable path directly rather than relying on shell-based conda activation.

Example:

```text
C:\...\envs\chatmap-ai\python.exe python\ai_helper.py
```

### Desktop-First

The MVP should be a desktop application, not a web application.

Rationale:

* Easier local file import/export
* No cloud accounts required
* No early authentication system
* No browser automation
* Better fit for a local personal knowledge tool

### UI Toolkit

Use JavaFX for the desktop UI.

Rationale:

* Better fit for draggable cards and later canvas work
* Modern enough for a visual workspace
* Still Java
* Works with a local desktop architecture

Swing remains possible, but JavaFX is preferred because the product eventually needs movable visual cards.

### Storage

Use SQLite for local persistence.

SQLite stores:

* projects
* chats
* messages
* tags
* import metadata
* later: card positions

Use SQLite FTS5 from the initial schema for full-text search.

### Testing

Do not use DBUnit initially.

Use:

* JUnit
* temporary SQLite databases
* `schema.sql`
* plain SQL fixture files
* repository/service tests
* golden-file tests for Markdown export

DBUnit may be reconsidered later, but it should not block MVP development.

### Dependencies

Even if the first prototype does not use Gradle, the project needs explicit third-party dependencies:

```text
- SQLite JDBC driver
- JSON library, preferably Jackson
- JUnit 5 for tests
```

If managing jars manually becomes annoying, introduce Gradle early rather than letting classpath management become its own little dungeon.

## Core Functional Scope

### Import

MVP import targets:

* plain text
* Markdown files
* pasted conversations
* ChatGPT JSON export

Later import targets:

* Claude exports
* Gemini exports
* browser-captured chats
* API-based imports

Each importer should translate its source format into the normalized ChatMap model.

### Normalize

All imports become the same internal model:

```text
Project
Chat
Message
Tag
```

Source-specific structure should not leak into the rest of the app.

### Organize

MVP organization features:

* create project
* rename project
* assign chat to project
* rename chat
* tag chat
* archive chat

MVP constraint:

```text
A chat belongs to zero or one project.
Tags provide many-to-many classification.
```

Future versions may replace `Chat.project_id` with a `ProjectChat` join table if chats need to appear in multiple projects.

### Search

Use SQLite FTS5 from the beginning.

Search should support:

* message text
* chat titles
* project filter
* tag filter
* archived/not archived filter

Do not start with `LIKE '%term%'` as the main search strategy.

### Export

Markdown export is core.

MVP export targets:

* single chat
* selected chats
* project
* handoff document

Export modes:

* raw transcript
* clean Markdown
* deterministic no-LLM handoff

AI-generated semantic compression may be added later, but the MVP handoff must work offline without an LLM.

## High-Level Architecture

```text
ChatMap Desktop App
│
├── Java UI
│   ├── project/chat navigation
│   ├── chat detail view
│   ├── search controls
│   └── later: simple card canvas
│
├── Java Core
│   ├── import chats
│   ├── normalize messages
│   ├── manage projects/tags
│   ├── search
│   └── export Markdown
│
├── SQLite Storage
│   ├── projects
│   ├── chats
│   ├── messages
│   ├── messages_fts
│   ├── tags
│   ├── chat_tags
│   └── later: card_positions
│
└── Optional AI Layer
    ├── Java LLM client first
    └── optional Python helper later only if needed
```

## Core Data Model

### Project

A project groups related chats.

```text
Project
- id
- name
- description
- created_at
- updated_at
```

### Chat

A chat is one imported conversation.

```text
Chat
- id
- project_id
- source
- title
- created_at
- updated_at
- imported_at
- archived
```

A chat belongs to zero or one project in the MVP.

### Message

The MVP stores a flattened text version of each message for display, search, and Markdown export.

Each imported message should also preserve its original source representation when available.

```text
Message
- id
- chat_id
- role
- text
- sequence
- timestamp
- raw_json
```

`text` is the normalized plain-text form used by the app.

`raw_json` is optional and stores the source message payload for future reprocessing or higher-fidelity export. Plain text and Markdown imports may leave `raw_json` null.

Timestamps should be stored as UTC ISO-8601 strings when known. If the source has no per-message timestamp, leave the message timestamp null and rely on chat-level import metadata.

### Tag

```text
Tag
- id
- name
```

### ChatTag

```text
ChatTag
- chat_id
- tag_id
```

### CardPosition

`CardPosition` is planned for the later card canvas.

It may be deferred until the canvas feature is implemented.

```text
CardPosition
- id
- chat_id
- x
- y
- width
- height
- color
```

The initial MVP does not require card position storage.

## SQLite FTS5 Strategy

Use an external-content FTS5 table for message search.

The durable message text lives in the `messages` table. The searchable index lives in `messages_fts`.

The FTS table should be kept in sync using SQLite triggers defined in `schema.sql`.

Repository tests must verify:

```text
- inserted messages become searchable
- updated message text updates search results
- deleted messages disappear from search results
```

Search is not just a convenience feature. It is part of the storage design.

## Search Query Scope

`SearchRepository` owns search queries involving message text, chat metadata, project filters, tag filters, and archive status.

Search tests should cover combinations of:

```text
- message text only
- project filter
- tag filter
- archived filter
- project + tag
- project + tag + archived
```

For MVP, message body search uses FTS5.

Chat title search may initially use simple indexed matching or a separate FTS table.

Initial ranking rule:

```text
Exact title matches first.
Then message matches.
```

More sophisticated ranking is deferred.

## Suggested Component Layout

```text
domain/
  Project
  Chat
  Message
  Tag
  CardPosition

service/
  ImportService
  ChatService
  ProjectService
  TagService
  SearchService
  ExportService

storage/
  ChatRepository
  MessageRepository
  ProjectRepository
  TagRepository
  SearchRepository

importer/
  ChatImporter
  PlainTextImporter
  MarkdownImporter
  ChatGptJsonImporter

exporter/
  MarkdownExporter
  HandoffExporter

ai/
  AiService
  NoOpAiService
  JavaLlmAiService
  PythonAiService   // future only

ui/
  MainWindow
  ProjectPanel
  ChatListPanel
  ChatDetailPanel
  SearchPanel
  ChatCanvas       // later
```

## Design Rules

Keep boundaries clean:

```text
UI does not parse files.
UI does not talk SQL.
Importers do not update UI.
Storage does not know Markdown.
Exporters do not query the database directly.
AI is not required for core functionality.
```

The core app should remain useful even with no API key and no network access.

## Exporter Input Contract

Exporters do not query the database directly.

`ExportService` is responsible for loading fully hydrated export models and passing them to exporters.

Example:

```text
ExportService
  loads ProjectExportModel
    - project metadata
    - chats
    - messages
    - tags
  passes model to HandoffExporter
```

Whole-chat-in-memory export is acceptable for MVP.

## No-LLM Handoff Format

The MVP handoff exporter must work without an LLM.

A no-LLM handoff is a structured Markdown document generated from stored project and chat metadata.

It should include:

```text
- project name
- export timestamp
- chat list
- tags
- source platform
- imported dates
- archived status
- first user message per chat
- last assistant message per chat
- optional user notes
- links or references to included chats
```

This is not semantic compression. It is structured extraction.

AI-generated compression may be added later through `AiService`, but the MVP handoff must be deterministic and available offline.

## AI Integration Strategy

Initial MVP should not require an LLM.

If LLM features are added, start with Java.

Possible AI features:

* summarize chat
* create semantic handoff
* suggest tags
* classify topic
* generate project summary

Define an interface:

```java
interface AiService {
    Handoff makeHandoff(Chat chat);
    List<String> suggestTags(Chat chat);
    Summary summarize(Chat chat);
}
```

Initial implementation:

```text
NoOpAiService
```

Later implementations:

```text
JavaLlmAiService
PythonAiService
```

Python helper remains optional and deferred.

## Import Details

### Plain Text Import

Plain text import creates a chat with one or more messages depending on parsing support.

Minimum behavior:

```text
Input text → one Chat → one Message with role = unknown/user
```

Later behavior may split obvious user/assistant sections.

### Markdown Import

Markdown import preserves the source as text and may optionally infer sections.

Minimum behavior:

```text
Markdown file → one Chat → one Message containing Markdown text
```

### Pasted Conversation Import

Pasted conversations are handled by the plain-text importer.

The UI may provide a text area for paste input, but internally it should use the same path as imported `.txt` files:

```text
pasted text → PlainTextImporter → normalized Chat + Messages
```

### ChatGPT JSON Import

ChatGPT JSON import should flatten messages into normalized `Message.text`.

When possible, preserve the original message payload in `Message.raw_json`.

Importer output should be source-neutral:

```text
ChatGptJsonImporter → Normalized Chat + Messages
```

After import, the rest of the application should not need to know the source was ChatGPT.

## Initial UI Concept

Start simple.

MVP UI:

```text
Left   : projects and chat list
Center : selected chat text/details
Top    : import, search, export, archive
```

Do not start with the infinite canvas.

The visual card canvas can come after the import/store/search/export pipeline works.

Later UI:

```text
Left   : projects and filters
Center : draggable chat cards
Right  : selected chat details
Top    : import/search/export/summarize
```

## Recommended Build Order

```text
1. Domain model
2. SQLite schema with FTS5 and triggers
3. Repository tests
4. Plain text importer
5. Markdown importer
6. Markdown exporter
7. Simple JavaFX list/detail UI
8. ChatGPT JSON importer
9. Search service and search UI
10. Project/tag management
11. No-LLM handoff exporter
12. Simple card canvas
13. Optional Java LLM integration
14. Optional Python helper only if Java becomes limiting
```

## Initial Test Plan

### Repository Tests

Use temporary SQLite databases.

Cover:

```text
- create project
- create chat
- create messages
- update message text
- delete chat/messages
- assign tags
- archive chat
```

### FTS5 Tests

Cover:

```text
- inserted messages are searchable
- updated messages update search results
- deleted messages disappear from search results
- project filter works
- tag filter works
- archived filter works
```

### Importer Tests

Cover:

```text
- plain text import
- Markdown import
- ChatGPT JSON import
- missing timestamps
- role mapping
- raw_json preservation where available
```

### Exporter Tests

Use golden-file tests.

Cover:

```text
- single chat Markdown export
- project Markdown export
- no-LLM handoff export
```

## Explicit Non-Goals for MVP

Do not build these first:

* live ChatGPT/Claude/Gemini syncing
* real-time assistant handoff
* browser automation
* cloud accounts
* multi-user collaboration
* payments
* mobile app
* advanced analytics dashboard
* full topic heat map
* complex model comparison
* sophisticated infinite canvas
* AI-required handoff generation

Those may be future features. They are not required to prove the MVP.

## MVP Success Test

The MVP succeeds when this works:

```text
1. Import a chat file.
2. Store it in SQLite.
3. Search message text.
4. Assign it to a project.
5. Add tags.
6. Export it as clean Markdown.
7. Export a deterministic project handoff.
```

## Short Product Statement

ChatMap is a local desktop workspace for turning scattered AI conversations into organized, searchable, exportable project knowledge.

## Guiding Principle

Build the boring pipeline first.

```text
Import → Normalize → Store → Search → Organize → Export
```

The canvas is useful only after the data pipeline works. Pretty maps are decoration until they sit on top of real stored, searchable conversations.
