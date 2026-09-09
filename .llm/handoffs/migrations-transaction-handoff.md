---
project: chatmap
agent: claude
branch: feature-migrations-transactional
---

# Task: Wrap schema migrations in a transaction so an interrupted migration can't corrupt the database

## Background / why this matters

`Database.applyMigrations(Connection)` (in
`src/chatmap/infrastructure/persistence/sqlite/Database.java`) performs
destructive, multi-statement, multi-table data merges on an existing user
database, but runs entirely in **autocommit mode** -- there is no
`setAutoCommit(false)` / `commit()` / `rollback()` anywhere in the class. Each
`executeUpdate` commits independently.

The dangerous sequences are `mergeDuplicateContentHashChats` and
`mergeDuplicateProjectNames`. Each does, per duplicate group: reassign
projects/tags/summaries onto a survivor row, THEN `DELETE` the duplicate row --
then a UNIQUE index is created on the just-deduplicated columns.

If the process dies (or any statement throws) partway through a group -- e.g.
after `reassignChatSummaries` but before the `DELETE`, or after deleting one
duplicate but before the next -- the database is left partially merged: orphaned
or half-repointed rows, possibly with the UNIQUE index not yet created. The next
startup then re-runs the migration against inconsistent data and the index
creation may fail outright, leaving the app unable to open its own database.

This is the only place in the codebase where the careful transaction discipline
used everywhere else (see `TransactionRunner`) is missing, and it's the place
that mutates existing user data destructively. Fix: make the migration atomic.

## What to do

Wrap the body of `applyMigrations` in a single local transaction so the whole
migration either fully applies or fully rolls back.

Key correctness points:

1. **SQLite allows DDL inside a transaction.** Do NOT exclude the
   `CREATE UNIQUE INDEX` or `ALTER TABLE ADD COLUMN` statements from the
   transaction thinking they force an implicit commit -- in SQLite they don't,
   and keeping them inside is what makes "columns added + rows merged + index
   created" a single atomic unit. Wrap the entire method body.

2. **Preserve and restore the prior autocommit state.** `applyMigrations` is
   `public static` and is also reachable via `initialize(Connection)`, so a
   caller could conceivably pass a connection that is already mid-transaction.
   Mirror the pattern `TransactionRunner` uses:
   - read `boolean previousAutoCommit = conn.getAutoCommit();`
   - if already in a transaction (`!previousAutoCommit`), do NOT start a new one
     or commit/rollback -- just run the body and let the caller own the
     transaction (the caller's rollback will cover it). A SAVEPOINT is an
     acceptable alternative if you prefer symmetry with `TransactionRunner`, but
     the simple "only manage the transaction if we started in autocommit" guard
     is sufficient and simpler here.
   - otherwise: `conn.setAutoCommit(false)`, run the body, `conn.commit()` on
     success, `conn.rollback()` on any `SQLException`/`RuntimeException` (then
     rethrow), and restore `conn.setAutoCommit(previousAutoCommit)` in a
     `finally`.

3. **Do not swallow exceptions.** On failure, roll back and rethrow the original
   exception unchanged. A failed migration must be loud, not silent.

4. Keep the existing ordering and the explanatory comments intact -- the merge
   steps must still run before their corresponding index creation.

## Constraints

- Change is confined to `Database.java`. Do not alter the merge helpers'
  behavior, SQL, or ordering -- only wrap them.
- `Database` runs before the service graph exists, so it cannot use
  `TransactionRunner`/`TransactionManager`. Use a local begin/commit on the raw
  `Connection`, matching the style already in `TransactionRunner`.
- Preserve the existing method signatures (`applyMigrations(Connection)` stays
  `public static ... throws SQLException`).

## Tests (add to the existing Database/migration test class)

- **Atomic rollback:** seed an in-memory DB (or a temp file DB) with data that
  makes a mid-migration statement fail (e.g. force one of the merge/DELETE steps
  to throw by using a spy/stubbed Connection, or by seeding a row that violates a
  constraint reached mid-body). Assert that after the failed `applyMigrations`
  call throws, the pre-migration rows are unchanged (nothing half-merged, no
  partial deletes).
- **Happy path unchanged:** an existing DB with genuine duplicate project names
  and duplicate (source, contentHash) chats still merges correctly and creates
  the indexes -- i.e. wrapping in a transaction didn't change the observable
  outcome.
- **Nested/caller-transaction case:** calling `applyMigrations` on a connection
  where `getAutoCommit()` is already false does not throw due to double-begin and
  leaves the caller able to commit/rollback.
- **Autocommit restored:** after a successful `applyMigrations` on a fresh
  connection, `conn.getAutoCommit()` is back to its original value.

## Validation

`./gradlew check` passes, including the new tests. Existing migration tests
(duplicate-merge, index-creation) still pass unchanged.
