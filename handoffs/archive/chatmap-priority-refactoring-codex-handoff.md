# ChatMap Codex Handoff: Priority Refactoring Passes

## Project

- Repository: `C:\Users\ray\eclipse-workspace\cjatmanager`
- GitHub: `rtayek/chatmap`
- Branch: `master`
- Reviewed GitHub head: `fda6912682a556c8e5320f1ef71b2742ac395d9c` (`Unify`)
- Java: 25
- Build authority: Gradle command line
- Eclipse policy: plain Java/JDT project without Buildship

## Immediate goal

Implement only the three approved, high-value refactoring passes:

1. Make database access and transaction ownership safe.
2. Correct reimport metadata behavior and return the real persistence outcome.
3. Remove the search N+1 queries and rank full-text results by relevance.

Keep this bounded. Do not perform a broad rewrite or unrelated cleanup.

Do not commit or push. Stop with a reviewed working tree and a completion report.

## Data safety

The populated local database is expected at:

`C:\Users\ray\eclipse-workspace\cjatmanager\.chatmap-local\chatmap.db`

Previously verified contents:

- 309 chats
- 8,311 messages
- 301 `chatgptJson` chats
- 7 `markdown` chats
- 1 `plainText` chat

`.chatmap-local/` and `.envrc` are intentionally ignored.

Rules:

- Do not add, move, delete, rewrite, import into, migrate, or otherwise modify the populated database, its backup, reports, or `.envrc`.
- Use in-memory or temporary test databases only.
- Do not run a real archive import as validation.
- Do not touch the legacy `~/.chatmap` location.
- Confirm ignored local data remains ignored and unchanged.

## Baseline

Before editing:

```bash
git status --short --branch
git rev-parse HEAD
./gradlew test
./gradlew eclipse
git diff --check
git check-ignore -v .chatmap-local/chatmap.db .envrc
```

Record the baseline test count. Preserve any existing user changes; never reset or overwrite them.

## Pass 1: Database concurrency and transaction ownership

### Problem

`ChatMapApp` currently constructs one SQLite `Connection` and shares it among all repositories. Some controller work runs on raw background threads while search, organization, selection/detail, and other UI actions can still touch the same connection and mutable controller state.

`ImportService` and `SummaryService` also duplicate `autoCommit`/commit/rollback code. Both assume all repositories use the same connection, and both commit or roll back an already-active caller transaction.

### Required result

Choose the smallest clear design that guarantees:

- The same JDBC connection is never used concurrently.
- Transactional services cannot accidentally commit or roll back a caller-owned transaction.
- Every repository participating in one transaction is known to use that transaction's connection.
- UI updates remain on the JavaFX application thread.
- Blocking provider, archive, and summary work remains off the JavaFX thread.
- Multiple background operations cannot race with each other or with foreground database operations.
- Executor/connection resources close cleanly during application shutdown.

A single serialized application/database executor is a reasonable solution for this small SQLite application. Connection-per-operation is also acceptable if transaction boundaries and repository construction remain explicit. Avoid introducing a framework or connection pool merely for appearance.

Centralize transaction behavior in a small abstraction such as `TransactionRunner`, `SqlTransaction`, or a database-session/unit-of-work object. If a transaction is already active, use a savepoint or otherwise preserve caller ownership; never call `commit()` on the caller's transaction.

### Tests

Add focused tests proving at least:

- A successful import inside an already-active outer transaction can still be rolled back by the caller.
- A failed import rolls back its partial chat/message changes.
- A changed conversation replacement remains atomic.
- Summary/tag persistence has the same transaction-ownership behavior.
- The chosen UI/database execution mechanism serializes overlapping database work or otherwise prevents concurrent connection use.

Do not rely on timing-heavy sleeps when a latch/barrier or deterministic fake can prove serialization.

## Pass 2: Import correctness and explicit persistence outcomes

### Problems

`ChatContentHasher` deliberately hashes message roles and text but excludes the title. On an external-identity match, neither `updateImportMetadata(...)` nor `updateFromSource(...)` reliably refreshes the stored title. A source-side conversation rename therefore remains stale in ChatMap.

`ChatGptArchiveImportService` predicts `inserted`, `updated`, or `unchanged` before calling `ImportService.persist(...)`. The service performs duplicate lookup/hash work, and the reported result can diverge from the operation that actually occurred.

### Required result

Make `ImportService.persist(...)` return the stored chat and the actual persistence outcome, for example:

```java
record PersistResult(Chat chat, Outcome outcome) {}
```

The exact names and nesting are flexible, but use one canonical outcome type for all import callers.

Required semantics:

- No existing identity: insert chat and messages; outcome `inserted`.
- Same identity and same transcript hash: preserve existing message rows, refresh source metadata including the title; outcome `unchanged`.
- Same identity and changed transcript hash: update the existing chat row, refresh title/source metadata, atomically replace messages; outcome `updated`.
- Provider fallback deduplication by source/content hash must report its actual outcome consistently.
- `ChatGptArchiveImportService` must count the outcome returned by persistence; remove its `expectedOutcome(...)` prediction.
- Existing chat IDs remain stable across updates.
- Clarify in naming or documentation that the current hash is a transcript/message hash, not a hash of every chat field.

Update all callers and tests. Avoid adding a schema column solely to rename `contentHash` in this pass.

### Tests

Add or update focused tests for:

- Same external identity, same transcript, changed title: stored title changes; message row identities/content remain unchanged; outcome is `unchanged`.
- Same external identity, changed transcript and title: chat ID remains stable, title changes, messages are replaced, outcome is `updated`.
- First import reports `inserted`.
- Second identical archive import reports every imported conversation as `unchanged`.
- Archive counts come directly from returned persistence outcomes.
- Rollback behavior from Pass 1 remains intact.

## Pass 3: Search relevance and query scaling

### Problems

`SearchRepository.listResults(...)` and full-text search call `findTagsByChat(...)` once per result. Listing 309 chats therefore executes roughly one chat query plus 309 tag queries.

Full-text results are ordered primarily by import time and chat ID, not by FTS relevance. This makes the search less useful for the project's immediate goal of exploring the imported corpus.

### Required result

- Eliminate per-chat tag queries. Load tags in one bulk query or produce the equivalent result through a well-structured join/aggregation.
- Preserve `SearchResult.tags()` ordering by tag name, case-insensitively.
- Rank nonblank FTS searches by SQLite FTS5 relevance, preferably `bm25(messageFts)` with best matches first.
- Return each chat only once, using its best matching message/snippet.
- Keep deterministic tie-breaking, such as import time and chat ID.
- Preserve project, tag, and archived filters.
- Preserve the current behavior for a blank query: list chats rather than running FTS.
- Continue using prepared statements for all values.

Keep the SQL understandable. A small number of explicit queries is preferable to a very clever statement that is difficult to test.

### Tests

Add or update repository/service tests proving:

- Multiple tags are hydrated correctly for multiple chats without duplicates.
- Tag ordering remains deterministic.
- A more relevant FTS match appears before a weaker match even when import chronology would order them differently.
- Multiple matching messages from one chat still produce one result with the best snippet.
- Project, tag, and archived filters still work with ranked results.
- Empty and punctuation-only queries behave safely.

If practical, add a deterministic query-count test or a repository seam demonstrating that result hydration uses a bounded number of statements rather than one statement per chat. Do not add a fragile wall-clock performance test.

## Preserve the recent path-resolution work

Do not regress commit `fda6912`:

- `--home <directory>` support
- nonblank `CHATMAP_HOME`
- existing `./.chatmap-local` fallback
- existing legacy `~/.chatmap` fallback
- clear failure when no home is selected
- exact single-argument handling for Gradle `-Pargs`
- repository-root Gradle working directories
- startup home/database diagnostics
- plain Eclipse project without Buildship
- deterministic repeated `./gradlew eclipse`

No database schema change should be necessary for these refactorings.

## Explicitly deferred

Do not include these in this pass unless a tiny prerequisite is unavoidable:

- Web-provider diagnostic redesign
- Large-scale `ChatMapApp` visual/layout rewrite
- General composition-root cleanup unrelated to connection safety
- Numbered migration framework
- Keyword classification, embeddings, or semantic search
- Importing `codex.json`
- Attachments or alternate ChatGPT branches
- Domain-wide conversion of timestamp/role strings into new types
- Cosmetic renaming and comment cleanup

If an unavoidable prerequisite expands scope materially, stop and report it instead of quietly broadening the task.

## Final validation

Run from the repository root:

```bash
./gradlew test
./gradlew eclipse
./gradlew eclipse
git diff --check
git status --short --branch
git check-ignore -v .chatmap-local/chatmap.db .envrc
```

Confirm the second `./gradlew eclipse` produces no further tracked-file changes and `.project` contains no Buildship nature or builder.

Do not use the populated database for smoke testing. If an application-level smoke is useful, use a temporary `--home` containing a generated test database.

## Completion report

Report:

- baseline and final test counts
- exact files changed
- database concurrency model selected and why it is safe
- transaction behavior, including outer-transaction tests
- final `PersistResult`/outcome contract
- evidence that title-only source changes now update without replacing messages
- search-query strategy and relevance ordering
- evidence that tag hydration no longer performs one query per chat
- two-run Eclipse idempotence result
- final `git status --short --branch`
- confirmation that ignored local data was untouched

Stop without committing or pushing.
