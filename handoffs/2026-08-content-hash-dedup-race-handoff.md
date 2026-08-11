# Handoff: missing unique-index backstop on content-hash dedup

Found in a fresh architecture pass on 2026-08-10. One item, narrow but real.

---

## The gap

`ImportService.persistInTransaction` has two dedup paths:

1. **External-identity path** (`persistWithExternalIdentity`) — used when
   `incoming.externalConversationId() != null`. Backed by a real unique index
   (`chatsExternalIdentityIndex`, see `Database.java`), so even if two writers
   race past the "does this already exist" check, the DB itself rejects the
   second insert. `ProjectRepository` already has an established pattern for
   this: catch the `SQLITE_CONSTRAINT_UNIQUE` violation and treat it as "someone
   else just inserted it" rather than a real error.

2. **Content-hash fallback path** — used for every plainText/markdown import
   and any provider fetch without an external ID. Looks up
   `findBySourceAndContentHash(source, transcriptHash)`; if nothing's found,
   inserts. `schema.sql` has no unique index on `(source, contentHash)` — only
   a check in application code, no backstop at the database level.

## Why it matters

Within a single running app instance, this isn't currently exploitable — all
DB writes already funnel through one serialized executor thread
(`ChatMapRuntime`'s `dbExecutor`), so the check-then-insert is naturally
atomic there.

The real exposure is cross-process: `ChatConsolidatorCli` writes to the same
persistent `chatmap.db` as the GUI app. If it's ever run from the command
line while the GUI is also open and importing, that's two separate JVMs, each
with its own serialized executor, each blind to the other. Both could pass
the "does this exist" check for the same plainText/markdown import before
either inserts, producing duplicate rows for the same conversation.

## Suggested fix

Add a unique index on `(source, contentHash)` in `schema.sql`, mirroring the
existing `chatsExternalIdentityIndex` pattern. Then apply the same
constraint-violation-as-expected-race pattern `ProjectRepository` already
uses: catch `SQLITE_CONSTRAINT_UNIQUE` on the insert and fall back to
treating it as "already exists" (re-fetch and update import metadata, same
as the normal `unchanged` outcome) rather than surfacing it as a failure.

Two things worth checking before adding the index:

- **Existing data.** A unique index needs zero pre-existing duplicates to
  create successfully — `Database.java` already has a comment noting this
  same constraint for the external-identity index, so there's likely already
  a precedent in this codebase for how that migration was handled (dedup
  pass before creating the index, or similar). Follow whatever pattern was
  used there.
- **NULL handling.** Confirm whether `contentHash` can be null for any
  legitimate imported chat (e.g. an empty conversation) — SQLite treats NULL
  as distinct from any other NULL in a unique index, so if that's a real case
  it won't be caught by the index and that's fine/expected, just worth
  knowing rather than assuming full coverage.

Not urgent enough to block other work — this is a defense-in-depth gap for a
fairly narrow scenario (concurrent CLI + GUI writes), not an active bug.
