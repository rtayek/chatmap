# Codex Handoff: Finish ChatMap JDBC Safety Fixes

Work directly in the existing ChatMap repository. Complete this task autonomously. Do not ask for routine design confirmation; use the constraints and acceptance criteria below. Stop and report if the working tree contains overlapping changes that cannot be preserved safely.

## Goal

Finish the small, high-value JDBC/SQLite safety work identified during review:

1. Preserve the existing passing `Database.open()` connection-lifecycle fix.
2. Harden `TransactionRunner` so every failed unit of work rolls back and retains the original failure.
3. Prevent application shutdown from closing the shared SQLite connection while a background task may still be using it.
4. Prevent services from accepting a `TransactionRunner` backed by a different connection from their repositories.

Do not redesign the storage architecture in this pass.

## Starting procedure

1. Read the project instruction and live design files that are available.
2. Run:

   ```bash
   git status --short --branch
   git diff --check
   ./gradlew test
   ```

3. Preserve all existing user work. Do not reset, discard, overwrite, or clean unrelated files.
4. The working tree may already contain the recent connection fix:

   - modified `src/chatmap/storage/Database.java`
   - new `tst/chatmap/storage/DatabaseConnectionTest.java`

   That fix configures `PRAGMA foreign_keys = ON` and `PRAGMA busy_timeout = 5000`, closes a connection when configuration or initialization fails, and preserves an `SQLException` from closing as a suppressed exception. Its tests have passed locally. Keep it unless current code proves it has already been committed or superseded.

## Task 1: Finish the connection failure handling

Review the pending `Database` change without rewriting it unnecessarily.

- A successful `open()` or `openAndInitialize()` result is owned and closed by its caller.
- A failure before either method returns must close the connection.
- A close failure must never replace the original configuration or initialization failure.
- Preserve unchecked close failures as suppressed failures too, not only `SQLException`, unless a concrete Java/JDBC constraint makes that unsafe.
- Add a focused test for any behavior added here.

Do not add pooling, WAL mode, or multiple production connections.

## Task 2: Harden `TransactionRunner`

The current implementation has a correctness bug: it catches `SQLException` and `RuntimeException`, but not `Error`. If `Work.run()` throws an `Error`, the rollback is skipped and the `finally` call to `setAutoCommit(true)` may commit partial work.

Update `TransactionRunner` so that:

- `SQLException`, `RuntimeException`, and `Error` from the work all cause rollback.
- The original work or commit failure remains the primary thrown failure.
- Rollback failures are attached to the primary failure with `addSuppressed()`.
- Restoring the original auto-commit state is always attempted.
- An auto-commit restoration failure is suppressed when another failure is already in flight.
- On a genuinely successful transaction, commit occurs exactly once.
- When the caller already owns a transaction, continue using a savepoint rather than committing or rolling back the caller's entire transaction.
- Nested savepoint rollback and release failures preserve the original failure and useful cleanup diagnostics.
- Preserve the interrupt status wherever an interruption is caught.

Add focused `TransactionRunner` tests covering at least:

1. successful top-level commit;
2. rollback after `SQLException`;
3. rollback after `RuntimeException`;
4. rollback after `Error`, proving partial writes are not committed;
5. original failure retained when rollback also fails;
6. original failure retained when auto-commit restoration also fails;
7. successful work inside a caller-owned transaction remains rollbackable by the caller;
8. failed nested work rolls back only to its savepoint and leaves the caller transaction usable.

Use a real in-memory SQLite connection where it proves behavior most clearly. Use a small proxy/fake only for JDBC failure modes that SQLite cannot conveniently induce.

## Task 3: Enforce connection consistency

`ImportService` and `SummaryService` verify that their repositories share one `Connection`, but their public constructors can still receive a `TransactionRunner` backed by a different connection.

- Add the smallest clear `TransactionRunner` API needed to verify connection identity without transferring connection ownership.
- Make the injectable constructors in `ImportService` and `SummaryService` reject a runner using a different `Connection`.
- Reject null collaborators clearly.
- Add focused constructor tests for mismatched connections.
- Do not introduce a dependency-injection framework.

## Task 4: Make UI shutdown safe

Current behavior:

- `SerializedTaskExecutor.close()` calls `shutdownNow()` and waits five seconds, but ignores whether termination succeeded.
- `ChatMapApp.stop()` then closes the shared SQLite connection even if a background task is still running.
- If executor shutdown is interrupted, the connection may not be closed and the interrupt status may be lost.

Implement a small deterministic fix:

- Signal whether the worker actually terminated.
- Do not close the shared connection while a worker that can use it remains alive.
- Preserve the current thread's interrupt status when interrupted.
- Ensure the connection is closed when termination succeeds, including appropriate failure paths.
- Avoid a long test delay by making the termination timeout injectable or otherwise quickly testable.
- Add focused executor/lifecycle tests for normal shutdown, interrupted shutdown, and a task that does not terminate within the allowed interval.
- Do not perform a broad asynchronous UI rewrite.

## Explicitly deferred

Do not implement these in this pass:

- connection pooling;
- one connection per operation;
- WAL mode;
- migration version tables or `PRAGMA user_version`;
- a complete atomic migration redesign;
- a broad JavaFX concurrency refactor;
- unrelated cleanup or formatting.

If an obvious migration concern needs recording, mention it in the final report only; do not change the schema for it.

## Validation

Run, at minimum:

```bash
./gradlew compileTestJava
./gradlew test
./gradlew eclipse
./gradlew eclipse
git diff --check
git status --short --branch
```

If `./gradlew check` is part of the established project workflow and has no known unrelated baseline failure, run it too.

Confirm that:

- all tests pass;
- two consecutive Eclipse-generation runs are idempotent;
- no Buildship nature or builder is introduced if the project intentionally excludes Buildship;
- ignored local data, especially `.chatmap-local/`, database files, backups, reports, and `.envrc`, is not modified;
- no unrelated working-tree changes are touched.

## Git boundary

- Do not commit.
- Do not push.
- Do not create or switch branches unless required to protect existing work; if required, explain why first.

## Final report

Return a concise handoff containing:

- baseline and final test counts;
- files changed or added;
- exact transaction and shutdown behaviors now guaranteed;
- validation command results;
- final `git status --short --branch`;
- any remaining limitation or blocker.

