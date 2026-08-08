# ChatMap Handoff
*August 7, 2026*

---

## Project Identity

**ChatMap** (`cjatmanager`) — a local Java desktop application for importing, organizing, searching, summarizing, and exporting AI chat histories across providers (ChatGPT, Claude, Gemini, CLI tools).

- **Local path:** `C:\Users\ray\eclipse-workspace\cjatmanager`
- **Repo:** `github.com/rtayek/chatmap`
- **Stack:** Java 21, JavaFX 21.0.6, Gradle (Kotlin DSL), SQLite with FTS5
- **Run:** `./gradlew run` · **Test:** `./gradlew test`

**Note:** `build.gradle.kts` specifies Java 21 / JavaFX 21.0.6. The README and GitHub Actions CI still say Java 25. This inconsistency is unresolved and is task 1 below.

**MyClaw** was partially merged into ChatMap. Backend execution pieces were ported, but the session runtime, socket transport, runnable prompt CLI, prompt UI, transcript subsystem, and several tools were not.

---

## Architecture

### Packages
- `chatmap.domain` — `Chat`, `Message`, `Project`, `Tag`, `ChatSummary`, `SearchResult`, `SearchOptions`, `Source`
- `chatmap.storage` — `ChatRepository`, `MessageRepository`, `ProjectRepository`, `TagRepository`, `SummaryRepository`, `SearchRepository`, `Database`, `TransactionRunner`
- `chatmap.importer` — `PlainTextImporter`, `MarkdownImporter`, `ChatGptJsonImporter`, `ChatGptArchiveImporter`, `RolePrefixedTranscriptParser`, `ImportedChat`
- `chatmap.service` — `ImportService`, `ExportService`, `SearchService`, `SummaryService`, `ProjectService`, `TagService`, `LiveChatFetchService`, `ChatGptArchiveImportService`, `PromptService`, `ChatContentHasher`, `BackendDescriptor`, `PromptResult`
- `chatmap.backend` — `AiBackend`, `ClaudeCliClient`, `ClaudeCliBackend`, `CommandBackedAiBackend`, `CommandRunner`, `PromptProfile`, `SummaryClient`, `DefaultChatProviders`, web/CDP adapters (Claude, ChatGPT, Gemini), CLI history providers (Claude Code, Codex, Gemini)
- `chatmap.exporter` — `MarkdownExporter`, `HandoffExporter`, `ChatExportModel`, `ProjectHandoffModel`
- `chatmap.cli` — `ChatConsolidatorCli`, `SummarizeChatCli`, `ImportChatGptArchiveCli`
- `chatmap.config` — `ChatMapPaths`
- `chatmap.ui` — `ChatMapApp`, `ChatMapController`, `ChatMapLauncher`, `ChatListState`, `SerializedTaskExecutor`, `FontSizeState`

### Key design rules
- Single shared SQLite connection; repositories take `Connection` from caller, never own it
- Use `TransactionRunner.inTransaction()` for all service-level DB transactions — never roll your own `setAutoCommit/commit/rollback`
- FTS5 external-content table (`messageFts`) maintained by triggers; `Database.java` uses a line-by-line parser to handle trigger bodies
- `Source` enum uses `lowerCamelCase` `dbValue` strings (e.g. `chatgptJson`, `claudeWeb`) — these are stable DB values, never change them once data exists
- All string identifiers use `lowerCamelCase` (e.g. `plainText`, `markdown`, `chatgptJson`)
- Tests use `Database.connectInMemory()` for isolation

---

## Integration Status

### Working and integrated
- JavaFX desktop UI
- Full import pipeline: plain text, markdown, ChatGPT JSON, ChatGPT archive ZIP
- Live chat fetch via CDP/Playwright: Claude web, ChatGPT web, Gemini web
- CLI history import: Claude Code, Codex, Gemini CLI
- SQLite storage with FTS5 full-text search
- `SummaryService` — LLM summarization + tagging via `ClaudeCliClient`; uses `TransactionRunner`
- `ChatConsolidatorCli` — scans workspace, imports all chats, generates project handoff markdown
- Export: per-chat markdown, project handoff markdown
- 32 test classes with coverage across all layers
- Static analysis configured in Gradle: Checkstyle, PMD, SpotBugs, JaCoCo

### Ported but not integrated
- `PromptService` — multi-backend prompt submission with `PromptProfile` and session support exists in code, but nothing in the production UI, CLI, or Gradle tasks constructs or invokes it; database recording ignores session ID; transcript path is MyClaw residue (see task 3 below)

### Not yet ported from MyClaw
- Session runtime
- Socket transport
- Runnable prompt CLI
- Prompt UI
- Transcript subsystem (production wiring)
- Several tools

### Not enforced in CI
- Static analysis — GitHub Actions runs only `./gradlew test`, not `./gradlew check`

---

## Stabilization Pass — Current Work

Six small tasks to complete before beginning semantic extraction. Do them in order.

### 1. Standardize on Java 25 / JavaFX 25

`build.gradle.kts` says Java 21 / JavaFX 21.0.6. README and CI say Java 25. Bring them into alignment.

**Files:** `build.gradle.kts`, `README.md`, `.github/workflows/*.yml`  
**Verify:** `./gradlew build` passes cleanly.

---

### 2. Fix Claude multiline prompts to use stdin

`ClaudeCliClient` passes the prompt as a command-line argument, which breaks on newlines, quotes, and special characters. Switch to writing the prompt to the process's stdin stream.

**File:** `src/chatmap/backend/ClaudeCliClient.java`

```java
Process process = new ProcessBuilder(cmd).start();
try (var writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
    writer.write(prompt);
}
// then read stdout as before
```

**Verify:** A prompt containing newlines and double-quotes produces correct output.

---

### 3. Move transcript output under `CHATMAP_HOME`

`PromptService` writes transcripts to `~/.myclaw/transcripts` — hardcoded MyClaw residue. Replace with `ChatMapPaths.home().resolve("transcripts")`.

**File:** `src/chatmap/service/PromptService.java`  
**Verify:** Transcript appears under `.chatmap-local/transcripts/`, not `~/.myclaw/`.

---

### 4. Stop suppressing failures

Two silent failure points in `PromptService`:

- `recordInDatabase` catches `SQLException` and does nothing
- Transcript write failures are likely also silently caught

At minimum log to stderr (or use whatever logger the project already uses):

```java
} catch (SQLException e) {
    System.err.println("[ChatMap] Failed to record prompt in database: " + e.getMessage());
}
```

**File:** `src/chatmap/service/PromptService.java`  
**Verify:** Intentionally break the DB path and confirm the error is visible.

---

### 5. Delete confirmed dead classes

No callers anywhere in `src/`:

- `src/chatmap/JavaManagementText.java`
- `src/chatmap/domain/ChatTag.java`

**Verify:** `./gradlew build` passes. `grep -r "JavaManagementText\|ChatTag" src/` returns nothing.

---

### 6. Enforce static analysis in CI

`./gradlew check` runs Checkstyle, PMD, SpotBugs, and JaCoCo in addition to tests. CI currently only runs `./gradlew test`.

**Step 1:** Run `./gradlew check` locally and fix any violations first.  
**Step 2:** In `.github/workflows/*.yml`, change `./gradlew test` → `./gradlew check`.  
**Verify:** Green CI run with `check`.

---

## Known Smells (lower priority, post-stabilization)

- `findByTag` in `ChatRepository` inlines the column list instead of using `selectColumns()` — minor drift risk
- `ExportService` two-constructor nullable pattern — `projects`/`tags` are null in the short constructor; throws `IllegalStateException` at runtime if project export is called via it
- `PromptService.parseSource` uses fragile string matching on `backendId` to infer `Source`; should be declared by `AiBackend` or carried by `BackendDescriptor`

---

## Next After Stabilization: Semantic Extraction

**`SemanticExtractionService`** — a multi-stage LLM pipeline for compressing long chat histories into a structured working context document. Full design is in `semantic-extraction-handoff.md`.

Pipeline:
1. **Extraction** — pull key decisions, open questions, facts & entities from the conversation
2. **Semantic triage** — score each item for relevance, drop stale or resolved items
3. **Canonicalization** — deduplicate, merge, normalize format
4. **Output** — structured JSON/markdown working context document
5. **Injection** — paste into a fresh chat as system prompt + first turn

Storage approach is intentionally deferred until service output is visible in practice.

---

## Key Reference Files

- `semantic-extraction-handoff.md` — authoritative design for extraction pipeline
- `design.md` — architectural principles
- `first-principles.md` — guiding constraints
- `architecture_and_refactoring_analysis.md` — recent static analysis (likely from Claude Code session)
- `myclaw_to_chatmap_merge_plan.md` — MyClaw merge history