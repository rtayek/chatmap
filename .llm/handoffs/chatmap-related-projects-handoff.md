# ChatMap Chat–Project Relationships — Implementation Handoff

## Recipient

Give this handoff to **Claude or Codex working in `rtayek/chatmap`**.
Self-contained schema + service + UI work in one repo. The `speech`
project is unrelated — do not touch it for this handoff.

## Purpose

Two related but separable gaps in the current `Project` model:

1. `Project.repositoryPath` is a single ambiguous string. In practice a
   project often has both a local filesystem path and a remote (GitHub)
   URL, and today there's only room for one.
2. A chat currently belongs to exactly one project
   (`chats.projectId`, a single nullable FK). In practice a single
   chat/conversation sometimes touches more than one project and should
   be findable under both, without forcing a single "correct" owner.

## Design Decision: One Main Project, Many Related Projects

Do **not** turn `chats.projectId` into a many-to-many relationship.
Keep it exactly as it is today — a chat still has exactly one main,
required project, and every existing feature that assumes that
(`assignProject`/`clearProject`/`filterByProject` in
`ChatMapController`, `ProjectContext.from(Project)` in the prompt
router) keeps working completely unchanged. This was a deliberate
choice to avoid rippling a required-multiplicity change through
routing, filtering, and project-assignment UI that isn't ready for
that ambiguity yet — see "Explicitly Out of Scope" below.

Add a **second, purely additive** many-to-many relationship for
projects that are relevant to a chat without being its main project.
This mirrors the existing `chatTags` / `tags` pattern exactly —
same shape, same cascade behavior, same composite primary key.

## Schema Changes

### 1. Split `repositoryPath` into local and remote paths

```sql
-- Migration: add columns, do not drop repositoryPath yet.
ALTER TABLE projects ADD COLUMN localPath TEXT;
ALTER TABLE projects ADD COLUMN remoteUrl TEXT;
```

Backfill: for existing rows, `repositoryPath` values that look like a
filesystem path (start with a drive letter or `/`) should be copied to
`localPath`; values that look like a URL (`https://...`, `git@...`)
should be copied to `remoteUrl`. Write this as an explicit backfill
step in the migration, not left as a manual follow-up — do not leave
existing project rows with only the old `repositoryPath` populated and
the new columns empty.

Decide whether to drop `repositoryPath` in this same migration or keep
it for one release cycle as a deprecated/read-only column. Given the
migration-atomicity principle already established in this project
(SQLite migrations must be wrapped in a real transaction with
rollback — no additive-only, non-transactional migrations), do the
backfill and any column removal inside the same transactional
migration rather than as a separate follow-up commit.

### 2. `Project` record and `ProjectContext` changes

`Project` gains `localPath` and `remoteUrl` fields (replacing
`repositoryPath`, or alongside it during the deprecation window — see
above). Every call site constructing a `Project` needs updating,
including test fixtures.

`ProjectContext.from(Project)` currently does:

```java
Path path = project.repositoryPath() == null || project.repositoryPath().isBlank()
        ? null
        : Path.of(project.repositoryPath());
```

This should read from `project.localPath()` specifically — the
router needs a filesystem path to operate against, not a remote URL.
Decide whether `ProjectContext` should also carry `remoteUrl` for
display/reference purposes even though routing itself doesn't need it.

### 3. Related-projects join table (mirrors `chatTags`)

```sql
CREATE TABLE IF NOT EXISTS chatRelatedProjects (
    chatId    INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    PRIMARY KEY (chatId, projectId)
);

CREATE INDEX IF NOT EXISTS chatRelatedProjectsProjectIndex ON chatRelatedProjects(projectId);
```

Note this table has no constraint preventing a chat's main
`projectId` from also appearing as one of its related projects. Decide
whether that should be prevented (application-level check) or is
harmless to allow (a project a chat is "also related to," even if it's
also the main one, isn't really a bug — just redundant). Lean toward
allowing it unless it causes a concrete UI confusion once built.

## Service and Repository Work

Mirror `TagService`/`TagRepository`'s existing shape as closely as
possible rather than inventing new patterns:

- `RelatedProjectRepository` (or similar name) — `addRelatedProject(chatId, projectId)`,
  `removeRelatedProject(chatId, projectId)`, `listRelatedProjects(chatId)`,
  `listChatsForRelatedProject(projectId)` — same CRUD shape as the
  tag repository.
- `ProjectService` (or a new `RelatedProjectService`) exposes the
  corresponding application-layer methods.
- `ChatMapController` gains thin pass-through methods, same pattern as
  `addTag`/`removeTag`/`filterByTag`.

## UI Work

Reuse `ChatMapViewBuilder`'s tag-bar pattern
(`createTagBar`/`TagBarWidgets`) for a parallel "related projects" bar
— same multi-select-and-add-remove interaction shape, wired to the new
repository/service methods instead of `TagService`. Do not build a
new interaction pattern from scratch; the tag bar already solves this
exact UI problem (attach/detach many-to-many labels to a chat).

## Tests Required

- `Project` record construction with `localPath`/`remoteUrl` instead
  of `repositoryPath`; update existing fixtures.
- `ProjectContext.from(Project)` uses `localPath`, not `remoteUrl`,
  for its `Path`.
- Migration backfill: existing rows with a filesystem-style
  `repositoryPath` end up with `localPath` populated and `remoteUrl`
  null, and vice versa for URL-style values.
- `RelatedProjectRepository` CRUD: add, remove, list by chat, list by
  project — same coverage shape as the existing tag repository tests.
- A chat's main `projectId` is completely unaffected by adding or
  removing related projects (confirms the two relationships are
  genuinely independent, not accidentally coupled).
- Deleting a project cascades correctly: removes it from
  `chatRelatedProjects`, and (per existing `ON DELETE SET NULL`
  behavior on `chats.projectId`) chats that had it as their main
  project retain the chat with `projectId` cleared rather than being
  deleted. Confirm this existing cascade behavior isn't accidentally
  changed by this work.

## Explicitly Out of Scope

- **Prompt routing does not become project-aware for related
  projects.** `PromptRouterService`/`ProjectContext` continue to route
  against the main project only. If a prompt needs to consider related
  projects too, that's a separate future design decision — don't
  build speculative support for it now.
- **No UI or logic changes to `assignProject`/`clearProject`/
  `filterByProject`** — these continue to operate on the single main
  project exactly as today.
- **No change to the `speech` project.** That work is tracked
  separately and unrelated to this handoff.

## Completion Criteria

A chat has a required main project (unchanged from today) and can
additionally be tagged with zero or more related projects, visible and
editable from the chat detail view via a UI pattern matching the
existing tag bar. `Project` rows have separate `localPath` and
`remoteUrl` fields, correctly backfilled from existing data, and the
prompt router correctly uses `localPath` for its repository context.

Run:

```bash
./gradlew test
```
