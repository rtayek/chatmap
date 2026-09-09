# ChatMap Migration Savepoint Handoff — 2026-09-08

## Executive summary

Port one useful guarantee from obsolete local commit `c01f8df` onto the current ChatMap implementation: when `Database.applyMigrations(Connection)` runs inside a caller-owned transaction, protect the migration with a JDBC savepoint so a migration failure removes only partial migration work without committing or rolling back the caller's entire transaction.

Do not merge, cherry-pick, or rebase the old branch. Work only on the clean branch created from current `master`.

## Repository state

- Repository: `rtayek/chatmap`
- Expected current branch: `fix/migration-caller-savepoint`
- The branch was created directly from an up-to-date, clean `master`.
- Old local reference: `feature/migrations-transactional`
- Old implementation commit: `c01f8df6a02fb65784480b8e684c4209fb5eecd7`
- Preservation tag: `archive/migrations-transactional-c01`
- The preservation tag has been pushed to GitHub.
- Do not alter or delete the old branch or archive tag during this task.

Verify the repository state before editing. Stop and report if the working tree is not clean or the current branch differs.

## Existing behavior on master

Current `Database.applyMigrations(Connection)` already:

- starts a transaction when auto-commit was initially enabled;
- commits successful migration work that it owns;
- rolls back failed migration work that it owns;
- restores auto-commit;
- preserves suppressed rollback or auto-commit restoration failures;
- leaves an existing caller-owned transaction in caller control.

The current tests cover owned-transaction rollback, caller ownership after success, and auto-commit restoration.

## Missing guarantee

When auto-commit is already disabled, current `master` runs migration statements directly in the caller's transaction. If a later statement fails, earlier migration statements may remain pending in that transaction.

The method should instead establish a savepoint immediately before its migration work. On failure it should roll back to that savepoint, preserving work the caller performed before invoking `applyMigrations` and leaving the surrounding transaction active.

## Required implementation

Inspect the current source and commit `c01f8df` for intent:

```sh
git show c01f8df -- \
  src/chatmap/infrastructure/persistence/sqlite/Database.java \
  tst/chatmap/infrastructure/persistence/sqlite/DatabaseMigrationTest.java
```

Then implement the behavior against current `master`:

1. Preserve every migration operation currently present in `applyMigrations`.
2. Refactor the migration statements into a private body method only if that keeps the two transaction paths clear and avoids duplication.
3. If auto-commit is enabled, preserve the current owned-transaction behavior exactly.
4. If auto-commit is disabled:
   - create a savepoint before performing migration work;
   - release it after success;
   - on `SQLException`, `RuntimeException`, or `Error`, roll back to the savepoint;
   - attempt to release the savepoint after rollback;
   - attach rollback or release failures as suppressed exceptions to the original failure;
   - rethrow the original failure;
   - never commit or roll back the caller's complete transaction;
   - leave auto-commit disabled.
5. Do not change database schema, migration order, public APIs, UI code, or unrelated files.

Do not mechanically transplant the old migration body. It predates worker-lifecycle tables, prompt routes, related projects, path backfills, and other current migrations.

## Required tests

Keep all existing migration tests passing and add focused coverage for failure inside a caller-owned transaction.

The new test must demonstrate that:

- auto-commit is disabled before calling `applyMigrations`;
- the caller performs observable work before the method establishes its savepoint;
- a deterministic failure occurs after at least one migration change;
- the migration's partial schema and data changes are rolled back;
- the caller's earlier work remains present;
- auto-commit remains disabled;
- the caller's transaction remains usable and under caller control;
- the method neither commits nor rolls back the caller's complete transaction.

Prefer real in-memory SQLite behavior over mocks. Use expressive test names and follow the existing test style.

## Verification

Run:

```sh
./gradlew test --tests chatmap.infrastructure.persistence.sqlite.DatabaseMigrationTest
./gradlew check
git diff --check
git status -sb
git diff --stat
```

Both Gradle commands must succeed. Skipped opt-in live-provider tests are acceptable if they are already excluded from the normal offline quality pipeline.

Review the final diff and confirm that no current migration operation disappeared during refactoring.

## Commit and push policy

If verification succeeds, create one focused local commit with a clear message such as:

```text
Protect caller-owned migrations with a savepoint
```

Do not push. Report the commit SHA and wait for Ray to review the result and authorize the push.

## Completion report

Report:

- the implementation approach;
- what the new test proves;
- focused-test result;
- full-check result;
- changed files;
- local commit SHA;
- any limitation or unexpected SQLite behavior.

Do not delete `feature/migrations-transactional` or the archive tag. That cleanup happens only after the replacement is reviewed, pushed, and merged.
