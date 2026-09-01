# ChatMap Implementation Notes

This file tracks the current implementation of `design.md`. It changes
freely as libraries, versions, and package layout evolve. `design.md` should
never need to change because of anything in here.

## Architecture

```text
src/chatmap
├── app
│   ├── application bootstrap and composition roots
│   ├── service graph and optional-integration wiring
│   └── serialized background execution, path resolution, and logging bootstrap
├── application
│   ├── model    — import and export transfer models
│   ├── port     — command, export, handoff, import, LLM, persistence, and provider interfaces
│   ├── service  — use cases and orchestration
│   └── support  — shared application logging and locking helpers
├── domain
│   └── chats, projects, tags, prompt routing, and worker-lifecycle model types
├── infrastructure
│   ├── command      — subprocess execution
│   ├── exporter     — Markdown chat and handoff formatters
│   ├── handoff      — filesystem handoff storage
│   ├── importer     — plain-text, Markdown, ChatGPT JSON, and archive readers
│   ├── llm          — Claude, Codex, Antigravity, Ollama, and JShell adapters
│   ├── persistence  — SQLite repositories, transactions, and schema
│   └── provider     — CLI-history and live-web/CDP chat providers
└── presentation
    ├── cli  — import, inventory, prompt, handoff, and lifecycle entry points
    └── ui   — JavaFX application, controller, view construction, and UI state
```

The standalone `handoff.HandoffWatcher` remains outside the main `chatmap`
package. It is a transport utility that collects stable handoff files into an
inbox; it does not interpret or route their contents.

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
8. CLI-history acquisition for Claude Code, Codex, and Gemini, plus live-web/CDP
   acquisition for Claude, ChatGPT, and Gemini
9. Optional LLM prompt execution, deterministic routing, summaries, and tags
10. File-based handoff orchestration and collection utilities
11. Durable worker assignments, sessions, lifecycle events, artifacts, semantic
    handoffs, retirement, and successor chains
```

## Supported Live Provider & Automation Capabilities

These provider integrations and browser automation tools are supported in the codebase:

* live web readers for Claude, ChatGPT, and Gemini (`Source` values
  `claudeWeb`, `chatGptWeb`, `geminiWeb`)
* browser automation for those readers (Chrome DevTools Protocol)
* CLI-history readers (Claude Code, Codex, Gemini)
* prompt execution against local CLIs, with automatic chat recording
* Claude-generated chat summaries (`chatSummaries`)
