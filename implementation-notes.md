# ChatMap Implementation Notes

This file tracks the current implementation of `design.md`. It changes
freely as libraries, versions, and package layout evolve. `design.md` should
never need to change because of anything in here.

## Architecture

```text
Java desktop app
│
├── app
│   └── composition root (ChatMapRuntime, service wiring)
│
├── domain
│   └── model types (Chat, Message, Project, Tag, ChatSummary, Source, ...)
│
├── ui
│   └── JavaFX
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

## Technology Choices

* Language & Runtime: Java 25 (Gradle 9.1.0, Kotlin DSL)
* UI: JavaFX 25.0.1 (javafx.controls)
* Storage: SQLite (sqlite-jdbc 3.53.2.0)
* Search: SQLite FTS5
* Browser Automation: Chrome DevTools Protocol
* Quality Assurance: JUnit 5, JaCoCo, Checkstyle, PMD, SpotBugs (`./gradlew check`)

## Storage Details

`messages` stores durable message rows.

`messageFts` is an external-content FTS5 table synchronized by triggers in `schema.sql`.

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
