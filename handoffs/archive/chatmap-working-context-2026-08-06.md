# ChatMap Working Context
*Extracted from session — August 6, 2026*

---

## Project Identity

**ChatMap** (`cjatmanager`) — a local Java desktop application for importing, organizing, searching, and exporting AI chat histories from multiple providers. Lives at `C:\Users\ray\eclipse-workspace\cjatmanager`. Public repo: `github.com/rtayek/chatmap`.

**MyClaw** has been merged into ChatMap. All work is consolidated under `cjatmanager`.

**Build:** Eclipse IDE, JDK 25, Gradle (Kotlin DSL). Run with `./gradlew run`, test with `./gradlew test`.

---

## Current Architecture

### Packages
- `chatmap.domain` — records: `Chat`, `Message`, `Project`, `Tag`, `ChatSummary`, `SearchResult`, `SearchOptions`, `Source`
- `chatmap.storage` — `ChatRepository`, `MessageRepository`, `ProjectRepository`, `TagRepository`, `SummaryRepository`, `SearchRepository`, `Database`, `TransactionRunner`
- `chatmap.importer` — `PlainTextImporter`, `MarkdownImporter`, `ChatGptJsonImporter`, `ChatGptArchiveImporter`, `RolePrefixedTranscriptParser`, `ImportedChat`
- `chatmap.service` — `ImportService`, `ExportService`, `SearchService`, `SummaryService`, `ProjectService`, `TagService`, `LiveChatFetchService`, `ChatGptArchiveImportService`, `PromptService`, `ChatContentHasher`, `BackendDescriptor`, `PromptResult`
- `chatmap.backend` — `AiBackend`, `ClaudeCliClient`, `ClaudeCliBackend`, `CommandBackedAiBackend`, `CommandRunner`, `PromptProfile`, `SummaryClient`, `DefaultChatProviders`, plus web/CDP adapters for Claude, ChatGPT, Gemini; CLI history providers for Claude Code, Codex, Gemini
- `chatmap.exporter` — `MarkdownExporter`, `HandoffExporter`, `ChatExportModel`, `ProjectHandoffModel`
- `chatmap.cli` — `ChatConsolidatorCli`, `SummarizeChatCli`, `ImportChatGptArchiveCli`
- `chatmap.config` — `ChatMapPaths`
- `chatmap.ui` — `ChatMapApp`, `ChatMapController`, `ChatMapLauncher`, `ChatListState`, `SerializedTaskExecutor`, `FontSizeState`

### Key design decisions
- Single shared SQLite connection; all repositories take `Connection` from caller, do not own it
- `TransactionRunner` handles `setAutoCommit/commit/rollback` and savepoint nesting — use it, don't roll your own
- FTS5 external-content table (`messageFts`) maintained by triggers in `schema.sql`; `Database.java` uses line-by-line parser to handle trigger bodies
- `Source` enum uses `lowerCamelCase` `dbValue` strings (e.g. `chatgptJson`, `claudeWeb`) — stable forever, never change once real databases exist
- All string identifiers/names in code use `lowerCamelCase` convention

### Schema
Tables: `projects`, `chats`, `messages`, `tags`, `chatTags`, `chatSummaries`, virtual `messageFts`. Migrations in `Database.applyMigrations()` use `addColumnIfMissing`.

---

## What's Working

- JavaFX UI running (`ChatMapApp` / `ChatMapLauncher`)
- Full import pipeline: plain text, markdown, ChatGPT JSON, ChatGPT archive ZIP, Claude web, ChatGPT web, Gemini web, Claude Code history, Codex CLI history, Gemini CLI history
- SQLite storage with FTS5 search
- `SummaryService` — LLM summarization + tagging via `ClaudeCliClient`
- `ChatConsolidatorCli` — scans workspace, imports all chats, generates project handoff markdown
- `PromptService` — multi-backend prompt submission with session support
- Export: per-chat markdown, project handoff markdown
- Test coverage across all layers (32 test classes)
- Static analysis: Checkstyle, PMD, SpotBugs, JaCoCo all wired into Gradle

---

## Open Issues / Known Smells

1. **Delete `JavaManagementText.java`** — dead code, no callers, lives at `src/chatmap/JavaManagementText.java`
2. **Delete `ChatTag.java`** — dead code, join table handled transparently by `TagRepository`
3. **`PromptService` writes transcripts to `~/.myclaw/transcripts`** — MyClaw merge residue; should be `.chatmap-local`
4. **`PromptService.recordInDatabase` silently swallows `SQLException`** — at minimum log to stderr
5. **`PromptService.parseSource` uses fragile string matching** on `backendId` to infer `Source`; should be declared by `AiBackend` or carried by `BackendDescriptor`
6. **`findByTag` in `ChatRepository` inlines the column list** instead of using `selectColumns()` — minor drift risk
7. **`SummaryService` still manages transactions manually** — predates `TransactionRunner`, should be migrated
8. **`ExportService` two-constructor nullable pattern** — `projects`/`tags` are null in short constructor, throws `IllegalStateException` at runtime if project export called; should be two separate classes or a builder

---

## Not Yet Implemented

- `SemanticExtractionService` / `ContextCompressionService` — designed in `semantic-extraction-handoff.md`, not started in code
  - Pipeline: old chat → extraction (decisions, open questions, entities) → semantic triage (score/drop stale) → canonicalization (dedupe/normalize) → working context document → fresh chat injection
  - Storage decision deferred until service output is visible

---

## Key Files for Orientation

- `semantic-extraction-handoff.md` — authoritative design for extraction pipeline
- `design.md` — architectural principles
- `first-principles.md` — guiding constraints
- `architecture_and_refactoring_analysis.md` — recent analysis (likely from Claude Code session)
- `myclaw_to_chatmap_merge_plan.md` — merge history

---

## Conventions to Preserve

- `lowerCamelCase` for all string identifiers and `dbValue` strings
- Repositories do not own connections — caller supplies and owns
- Use `TransactionRunner.inTransaction()` for all service-level DB transactions
- Tests use `Database.connectInMemory()` for isolation
- No `bin/` in git (use `git rm -r --cached bin` if it reappears)