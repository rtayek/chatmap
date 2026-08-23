# ChatMap Vault Isolation Handoff

## Context

ChatMap is pivoting from a monolithic multi-model harness toward modular subsystems. The immediate win is a **Router** (multi-model switching + extraction) + **Gateway** (sandbox + primitives). The **Vault** (SQLite chat storage + Markdown workspaces) already exists but is tangled with UI, orchestration, and controller logic.

**Goal:** Isolate Vault into a standalone, testable module so:
1. Router can call it cleanly
2. Vault can evolve independently
3. Future implementations (e.g., different storage backends) can swap in without touching Router

## Scope: Vault Isolation

### Phase 1: Extract Vault Module

**Create new package structure:**
```
src/main/java/chatmap/vault/
├── VaultAPI.java                    (public interface)
├── ChatRepository.java              (SQLite chats)
├── WorkspaceRepository.java         (Markdown workspaces)
├── LocalFileSystemGrounding.java    (Windows file paths)
└── internal/
    ├── SQLiteConnection.java
    └── FileSystemAdapter.java
```

**Move into Vault:**
- All SQLite access (currently scattered in controllers/models)
- Chat history storage + retrieval (raw logs from 6 providers)
- Markdown workspace read/write
- Local filesystem path mapping (grounding)
- FTS5 search capability

**Stay OUT of Vault:**
- UI rendering (no JavaFX)
- Orchestration logic (no routing decisions)
- Agent communication (no socket code)
- Prompt processing (no classification)

### Phase 2: Define VaultAPI Surface

**Minimal, clean interface:**

```java
public interface VaultAPI {
  // Chat storage
  void storeChat(Chat chat);
  Optional<Chat> getChat(String id);
  List<Chat> listChats(String provider, LocalDateRange range);
  
  // Workspace storage
  void storeMarkdown(String path, String content);
  Optional<String> readMarkdown(String path);
  List<String> listWorkspacePaths();
  
  // Local grounding
  String resolveLocalPath(String logicalPath);
  void validateSandboxBoundary(String path) throws SecurityException;
  
  // Search
  List<ChatMatch> searchChats(String query, SearchOptions opts);
}
```

**No internal methods exposed.** Only these operations.

### Phase 3: Break Circular Dependencies

**Current problem:** ChatMapController reaches into SQLite directly. `PromptService` reads chats without going through a clean boundary.

**Action items:**

1. **Audit current callers** — find every place that touches SQLite, chat files, or Markdown directly. Document the pattern.
   ```bash
   grep -r "sqlite\|Chat\|Markdown" src/main/java/chatmap --include="*.java" \
   | grep -v "vault/" | head -30
   ```

2. **Create adapter in ChatMapController:**
   ```java
   private VaultAPI vault;
   
   // Instead of: SQLiteConnection.query(...)
   // Now: vault.getChat(id)
   ```

3. **Move Vault instantiation to a factory** (or Spring bean, if using DI):
   ```java
   public class VaultFactory {
     public static VaultAPI createDefault(Path dataDir) {
       return new SQLiteVaultImpl(dataDir);
     }
   }
   ```

### Phase 4: Stub the Router (No Logic Yet)

**Create new package:**
```
src/main/java/chatmap/router/
├── RouterAPI.java          (public interface)
├── MultiModelRouter.java   (skeleton)
└── ModelTarget.java        (provider + model name)
```

**Skeleton Router:**
```java
public interface RouterAPI {
  // Dispatch a task to a model; return response
  CompletableFuture<String> send(ModelTarget target, String prompt);
  
  // Extract from one model's output, feed to another
  CompletableFuture<String> chain(
    ModelTarget sourceModel,
    String sourcePrompt,
    ModelTarget targetModel,
    ExtractionLogic extraction
  );
}
```

**For now:** Router is **empty**. Just the interface. No implementation. Router will need Vault + Gateway later, but don't wire it yet.

### Phase 5: Defer Everything Else

**Explicitly park (don't delete):**
- Prompt mode UI (depends on Router)
- Menu bar restructuring (depends on Router)
- Prompt routing + classification (belongs in Router, not core)
- Semantic extraction (agentic downstream task)
- Classifier double-counting bug (lower priority)

**Document in a `PARKED.md`** where these live and why they're waiting.

## Testing Strategy

**Vault tests (highest priority):**
```java
VaultTest.java
├── testStoreChatAndRetrieve()
├── testListChatsByProvider()
├── testSearchWithFTS5()
├── testWorkspaceReadWrite()
├── testSandboxBoundaryValidation()  // CRITICAL: no escape
└── testLocalPathResolution()
```

**No UI tests yet.** Focus on data layer correctness.

## Deliverables

1. **Vault module** — isolated, tested, with clean API
2. **Router skeleton** — empty interfaces, no logic
3. **PARKED.md** — document what's deferred and why
4. **Refactored ChatMapController** — uses VaultAPI instead of reaching into SQLite
5. **Updated build.gradle** — vault as a separate sourceset or module (optional, but preferred)

## Success Criteria

- [ ] All SQLite access goes through VaultAPI
- [ ] No circular dependencies (Vault doesn't import Router; Router can import Vault)
- [ ] Vault tests pass (100% coverage of public API)
- [ ] ChatMapController compiles and runs with new Vault calls
- [ ] Router skeleton compiles (empty)
- [ ] No UI regressions (existing UI still works, just refactored)

## Who Should Do This

**Best fit:** An agent comfortable with Java architecture + dependency injection. **Claude Code CLI** or **Codex** with narrow, structured handoff.

**Why not Antigravity:** Protobuf memory is opaque; hard to track architectural decisions. Vault isolation is highly architectural; needs clear memory of decisions.

**Why not qwen-2.5-72b:** Retired from refactor duty after prior incidents. This is a refactor. Stick to narrow tasks only.

## Estimated Scope

- **Audit + planning:** 30 min
- **Extract Vault:** 1–2 hours
- **Break deps:** 1–2 hours
- **Write tests:** 1–2 hours
- **Router skeleton:** 30 min

**Total:** ~5–7 hours of focused agent work.

## Post-Handoff

Once Vault is solid:
1. You review the new module structure
2. Run tests locally
3. Merge to main
4. **Then:** Hand off Router implementation to an agent (multi-model switching, extraction logic)

---

## Notes for Agent

- **Read first:** `evo.md` for architectural vision
- **Understand the pivot:** Vault is opt-in now; Router is the new spine
- **Be conservative:** Don't "improve" Vault while extracting it. Just isolate. Improvements come later.
- **Commit frequently:** Each phase (extract, break deps, test, stub) should be a separate, reviewable commit
- **Use `git apply`** for patches; don't rely on `git am` (known to fail in Ray's environment)
- **Test in a fresh clone:** Verify changes from a clean checkout before pushing

