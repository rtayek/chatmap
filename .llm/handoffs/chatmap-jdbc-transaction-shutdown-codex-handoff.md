# ChatMap JDBC, Transaction & Shutdown Codex Handoff
*August 7, 2026*

---

## Executive Context

**ChatMap** (`cjatmanager`) — a local Java desktop application for importing, organizing, searching, summarizing, prompting, and exporting AI chat histories across providers (ChatGPT, Claude, Gemini, CLI tools).

- **Local Path:** `C:\Users\ray\eclipse-workspace\cjatmanager`
- **Repo:** `github.com/rtayek/chatmap`
- **Stack:** Java 25, JavaFX 25.0.1, Gradle (Kotlin DSL), SQLite 3.53+ with FTS5
- **Quality & Test Commands:** `./gradlew check` (Runs 35 test classes, Checkstyle, PMD, SpotBugs, JaCoCo)

---

## Database Architecture & Connection Model

### 1. Connection Ownership & Reuse Pattern
- Repositories (`ChatRepository`, `MessageRepository`, `ProjectRepository`, `TagRepository`, `SummaryRepository`, `SearchRepository`) **do not own or close** JDBC connections. They receive a caller-supplied `Connection`.
- In the desktop application (`ChatMapApp`), a single SQLite connection is opened on startup and closed in `stop()`.
- In CLI tools (`ChatConsolidatorCli`, `ImportChatGptArchiveCli`, `SummarizeChatCli`), connection lifecycle is scoped using `try (Connection conn = ...)` blocks.
- In-memory unit tests use `Database.connectInMemory()` to pass a single shared connection across test repositories.

### 2. SQLite Connection Pragmas & Fail-Safe Initialization
All connections opened via `Database.open()` execute the following pragmas immediately:
```sql
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
```
- `foreign_keys = ON`: Enforces cascading deletes (`ON DELETE CASCADE`) for messages and FTS triggers.
- `busy_timeout = 5000`: Instructs SQLite to wait up to 5,000 ms during transient write lock contention instead of failing immediately with `SQLITE_BUSY`.
- **Fail-Safe Cleanup:** `Database.open()` uses `closeAfterFailure(conn, failure)` to guarantee that if pragma execution or schema initialization fails, the opened connection is safely closed, preserving primary failure context and attaching close errors via `failure.addSuppressed(...)`.

### 3. Transaction Management (`TransactionRunner`)
All multi-repository service transactions use `TransactionRunner.inTransaction()`:
- **Root Transactions:** If `conn.getAutoCommit()` is `true`, it sets `autoCommit = false`, executes the work, commits on success, or rolls back on exception, and restores `autoCommit = true` in a `finally` block.
- **Nested Transactions:** If `conn.getAutoCommit()` is `false`, it creates a SQLite `Savepoint`, releasing it on success or rolling back to the savepoint on error.

### 4. Application Shutdown & Thread Cleanup (`ChatMapApp.stop()`)
Upon application exit, JavaFX invokes `ChatMapApp.stop()`:
```java
@Override
public void stop() throws Exception {
    if (backgroundExecutor != null) {
        backgroundExecutor.close();
    }
    if (conn != null) {
        conn.close();
    }
}
```
- `SerializedTaskExecutor` is shut down first so background workers terminate cleanly before the shared SQLite connection is closed.

---

## Key Files for Reference

- `src/chatmap/storage/Database.java` — Connection opening, pragma configuration, and fail-safe initialization
- `src/chatmap/storage/TransactionRunner.java` — Transaction and nested savepoint manager
- `src/chatmap/config/ChatMapPaths.java` — Centralized path resolution (`transcriptsDirectory()`, `databasePath()`)
- `src/chatmap/service/PromptService.java` — Prompt execution and automatic SQLite recording
- `src/chatmap/ui/ChatMapApp.java` — Main application launcher and shutdown lifecycle
- `tst/chatmap/storage/DatabaseConnectionTest.java` — Unit tests for connection pragma setup and failure recovery
- `design.md` — Updated system architecture and data model specification

---

## Status & Verification

- **Current Build:** `./gradlew check` passes cleanly (100% green).
- **Stabilization & JDBC Refactoring:** Complete and committed to `master`.
- **Next Phase:** Ready to begin implementation of `SemanticExtractionService` (detailed in `handoffs/semantic-extraction-handoff.md`).
