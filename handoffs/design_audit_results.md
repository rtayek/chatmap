# ChatMap Design & Architecture Audit
*August 14, 2026*

## 1. Architectural Health (Hexagonal Structure)
The recent hexagonal restructuring (domain -> application -> infrastructure / presentation) is fully successful. 
- **`ArchitectureBoundaryTest`**: I ran `./gradlew test` and the boundary tests pass flawlessly. This means the boundaries aren't just documented conventions; they are actively enforced at the source level. 
- **Persistence Layer**: DB interactions correctly sit behind interfaces in `chatmap.application.port.persistence`, and SQLite adapters correctly implement them without leaking JDBC logic upwards.
- **Transactions**: Service-level transactions using the `TransactionRunner` abstraction are working correctly across the board.

## 2. Completed Fixes from Recent Handoffs
I audited the codebase against the recent `2026-08-review-findings-handoff` and can confirm several major items have **already been fixed**:
- **Item 1**: Plain text and Markdown imports now correctly deduplicate (the `isProvider()` gate was dropped).
- **Item 4**: `ExportService.loadProjectHandoff` is now safely wrapped in a transactional lock (`chats.transactions().inTransaction(...)`), making it a true consistent snapshot.
- **Item 6**: `findMostRecent()` correctly filters out archived chats with `WHERE archived = 0`.
- **Handoff Orchestrator Git Lifecycle**: The unchecked Git operations that could lead to data loss or false successes have been fully wrapped with exit code checks and safe recovery paths.

## 3. High-Priority Design Smells Remaining

### A. Provider-Specific Invocation Adapters
The `StandardCliBackend` and `HandoffOrchestratorService` currently share subprocess execution but blur the lines on command generation. As we discussed, migrating to `CliInvocationAdapter` or adding abstract command-generation methods to the base class will cleanly decouple the CLI argument strings (e.g., `--dangerously-skip-permissions` for Claude vs `codex exec -`) from the generic execution runner.

### B. The `ChatMapApp` God Class (Item 8)
`ChatMapApp.java` is still doing too much (widget construction, dialog prompts, and background task orchestration).
**Recommendation**: Continue extracting UI concerns. Pull dialogs into a `ChatMapDialogs` helper class and rely more heavily on `BackgroundActionRunner` instead of inline `runInBackground` calls.

### C. Conflated Background Execution (Item 3)
Currently, `SerializedTaskExecutor` is handling fast DB operations alongside slow, external I/O (like live CDP web fetches and CLI runs). 
**Recommendation**: Split this into a DB-serialized executor and an external I/O pool to prevent slow agent runs from head-of-line blocking the UI's database requests.

### D. Generic `Exception` Handling in Providers (Item 9)
`ChatProvider` implementations currently throw the root `Exception` type, forcing 29+ broad `catch (Exception e)` blocks across the app. This masks real bugs (like NPEs).
**Recommendation**: Introduce a checked `ChatProviderException` and update the 9 provider signatures to throw it instead.

### E. Session Identity Loss
`PromptService` does not persist provider session IDs. 
**Recommendation**: Extend `AiResponse` and `PromptResult` to carry the provider session identity and store it as the chat's `externalConversationId` so that subsequent prompts append atomically rather than creating duplicate chat records.

---

**Summary**: The core domain and application boundaries are rock solid. The remaining tech debt is largely isolated to the presentation/infrastructure layers (UI bloat, generic exceptions, and execution thread-pooling).
