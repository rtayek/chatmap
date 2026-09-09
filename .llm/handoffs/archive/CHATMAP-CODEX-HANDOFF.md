# ChatMap Codex Handoff

## Assignment

Review and harden the ChatMap project before adding more provider integrations or semantic features.

The immediate goal is to make chat acquisition idempotent, preserve provenance, protect database integrity, and bring the documentation back into agreement with the implementation.

Repository:

- <https://github.com/rtayek/chatmap>
- Default branch: `master`
- Review baseline: commit `348b1c29a6d5f610f5a57301c5e304e2ce297842`

Do not commit, push, open a pull request, delete files, or rewrite unrelated work unless explicitly authorized.

## Project Purpose

ChatMap is a local Java desktop application that turns imported AI chats into organized, searchable, exportable project knowledge.

Core deterministic workflow:

```text
Import -> Normalize -> Store -> Search -> Organize -> Export
```

Longer term, ChatMap may extract durable semantic knowledge from conversations. Raw chats remain evidence; extracted knowledge becomes the working project state.

## User and Engineering Preferences

- Prefer Java.
- Use Gradle command-line builds as authoritative.
- Eclipse should work as a plain Java project without Buildship.
- Code and tests are documentation.
- Prefer expressive names and minimal comments.
- Keep the design simple, deterministic, testable, and modular.
- Use short, focused documentation; avoid a maze of overlapping Markdown files.
- Preserve raw source material and provenance.
- AI features must remain optional. The deterministic workflow must remain useful without an LLM.
- Accessibility matters: the application is used by someone with low vision.

## Current Implementation

The repository currently contains approximately:

- 59 Java source files
- 24 test classes
- 133 JUnit `@Test` methods
- JavaFX desktop UI
- SQLite persistence with FTS5 search
- plain-text, Markdown, and ChatGPT JSON importers
- project and tag management
- Markdown chat export
- deterministic project handoff export
- optional Claude summary-and-tag generation
- live CDP readers for Claude, ChatGPT, and Gemini
- local CLI-history readers for Claude Code, Codex, and Gemini

The deterministic MVP is substantially implemented. Recent work expanded rapidly into browser automation, local session discovery, and AI summaries.

## Main Review Conclusions

### 1. Live imports are not idempotent

`LiveChatFetchService.resolve(null)` accepts the first provider that returns a chat and calls `ImportService.persist(...)` unconditionally.

Every press of **Get latest chat** can therefore create another database row for the same conversation.

The current model has no durable provider conversation identifier, source URI, or content fingerprint. Duplicate chats will contaminate search results, project exports, tags, and summaries.

Relevant files:

- `src/chatmap/service/LiveChatFetchService.java`
- `src/chatmap/service/ImportService.java`
- `src/chatmap/importer/ImportedChat.java`
- `src/chatmap/domain/Chat.java`
- `src/chatmap/storage/ChatRepository.java`
- `src/chatmap/storage/schema.sql`

### 2. Provider provenance is lost

Web and CLI providers currently construct chats with `Source.markdown`.

This means ChatMap cannot reliably distinguish:

- Claude web
- ChatGPT web
- Gemini web
- Claude Code history
- Codex CLI history
- Gemini CLI history
- an ordinary Markdown file

This violates the project's provenance principle and blocks reliable deduplication.

Relevant files:

- `src/chatmap/domain/Source.java`
- `src/chatmap/backend/WebTranscripts.java`
- `src/chatmap/backend/LocalCliSessions.java`
- provider implementations in `src/chatmap/backend/`

### 3. "Latest chat" is not actually latest

`DefaultChatProviders.ordered()` defines a priority order. `LiveChatFetchService` stops at the first provider with any chat.

An older Claude chat can therefore win over a newer ChatGPT, Gemini, Codex, or other chat.

For the near term, prefer explicit provider selection in the UI. If the product truly needs a cross-provider latest operation later, provider results need comparable source timestamps.

### 4. Multi-row writes are not transactional

`ImportService.persist(...)` inserts a chat and then inserts its messages one by one using an auto-commit connection. A failed message insert leaves a partial chat.

`SummaryService.summarize(...)` similarly writes a summary and then tags without one transaction.

Importing a chat should be atomic. Summary-plus-tag updates should also either complete or roll back as a unit.

### 5. Summary and tags are not semantic extraction

`SummaryService` asks Claude for a short prose summary and several tags.

That can remain as an optional convenience, but it should not be confused with the proposed semantic extraction system. Semantic extraction would eventually produce typed units such as:

- Decision
- Constraint
- Question
- Task
- Pattern
- Fact
- Risk
- Reference
- Artifact

Those units also need provenance, source-message references, confidence or review state, and a human acceptance workflow.

Do not build this larger model during the hardening slice.

### 6. Documentation and implementation disagree

`design.md` says live-provider synchronization, browser automation, and AI work are MVP non-goals, while those features now occupy a substantial portion of the implementation.

The repository root also contains roughly 2,000 lines across multiple overlapping Markdown files, including session transcripts and historical handoffs. This contradicts the project's own documentation-consolidation goals.

Current root documents include:

- `README.md`
- `design.md`
- `vision.md`
- `first-principles.md`
- `semantic-extraction-handoff.md`
- `old-handoff.md`
- `chatmap-development-session.md`
- `chatmap-project-tag-ui.md`
- `exported-ChatMap-Development-Session.md`

Do not delete or consolidate these without explicit authorization. First propose a precise keep/merge/archive plan.

### 7. Build verification is incomplete

The test suite is substantial, but no GitHub Actions workflow is present.

The committed `gradlew` mode is `100644`, so `./gradlew test` fails under Linux/WSL with permission denied. `bash gradlew test` remains a workaround, but the wrapper should be executable in Git.

The committed `lib/` and `.classpath` currently contain Windows-specific JavaFX jars. That is consistent with the existing Windows Eclipse setup but should be documented clearly before claiming WSL/Linux Eclipse support.

### 8. Accessibility needs an explicit slice

The JavaFX UI uses default font sizing and a fixed initial window size. Before expanding the UI further, plan support for:

- larger global font scaling
- high-contrast presentation
- uncluttered controls
- keyboard-accessible operations
- readable status and error feedback

Do not mix this into the persistence hardening patch unless requested.

## Recommended Implementation Order

### Slice 1: Identity, provenance, and idempotent import

Design the smallest model that can distinguish provider conversations and prevent duplicates.

Likely fields or concepts:

```text
source
externalConversationId
sourceUri
contentHash
sourceUpdatedAt
lastImportedAt
```

Do not add fields blindly. First inspect provider data and determine which identifiers and timestamps are actually available from each provider.

Requirements:

- Ordinary file import must continue to work.
- Provider imports must retain their actual origin.
- Re-importing an unchanged provider conversation must not create a duplicate chat.
- Decide and document what happens when the same provider conversation has gained new messages.
- Prefer deterministic identifiers over titles.
- Do not use a title alone as identity.
- Content hashing may be a fallback, not the only provider identity when a durable external ID exists.
- Add a database uniqueness rule where possible.
- Add regression tests that import the same provider chat twice.

Before editing, present the proposed data-model change and migration approach.

### Slice 2: Transaction boundaries

Make chat-plus-message persistence atomic.

Requirements:

- A failure during message insertion leaves no partial chat.
- Successful import commits exactly once.
- Existing repository tests continue to pass.
- Add a test that deliberately fails after the chat row is inserted and verifies rollback.
- Apply the same principle to summary-plus-tag persistence, either in this slice or a clearly separated follow-up.

Do not scatter manual transaction management across repositories. Prefer one clear service-level unit-of-work boundary.

### Slice 3: Provider selection semantics

Replace the misleading cross-provider "latest" behavior.

Preferred initial behavior:

- Let the user choose a provider/source.
- Fetch the latest conversation from that selected provider.
- Keep a local-most-recent fallback as a separate, explicit operation.

Avoid building cross-provider timestamp comparison until provider timestamps and identity are trustworthy.

### Slice 4: Documentation consolidation proposal

After the code model is stable, propose a small canonical documentation set.

Likely target:

- `README.md` — minimal build/run entry point
- `design.md` — concise current design and implementation contract
- `vision.md` — durable long-term direction
- one short working handoff only when active work requires it

Historical chats and superseded handoffs should be archived or removed only after user approval.

### Slice 5: Build and accessibility

- Correct the executable bit on `gradlew`.
- Consider a minimal CI workflow running `./gradlew test` on the authoritative JDK.
- Document the Windows-specific plain-Eclipse classpath arrangement.
- Plan JavaFX font scaling and high-contrast support as a separate, testable UI slice.

## Acceptance Tests for the First Hardening Slice

At minimum, add tests demonstrating:

1. Importing one provider conversation stores its correct source and external identity.
2. Importing that unchanged conversation again does not add a second chat.
3. Importing an updated form of the same conversation follows the explicitly chosen update policy.
4. Two providers with identical titles remain distinct.
5. Two conversations from one provider with identical titles remain distinct.
6. A failed message insertion rolls back the chat row and all prior message rows.
7. Plain text, Markdown, and ChatGPT JSON imports still pass their existing tests.
8. FTS5 remains synchronized after inserts, updates, rollbacks, and deletes.
9. Existing project/tag and Markdown-export behavior remains unchanged.

## Important Non-Goals

Do not implement during the first hardening work:

- a knowledge graph
- atomic semantic knowledge objects
- automatic cross-chat synthesis
- multiple-model review
- a visual canvas
- cloud synchronization
- more web or CLI providers
- a large UI redesign
- migration to Python
- replacement of SQLite

## Suggested Working Procedure

1. Clone or update the repository and inspect `git status` before editing.
2. Run the current tests using the Gradle wrapper.
3. Confirm the baseline test count and record any pre-existing failures.
4. Inspect the current SQLite schema and provider outputs.
5. Propose the identity/provenance model and database migration before implementation.
6. Implement one bounded slice.
7. Add focused unit and integration tests.
8. Run the complete test suite.
9. Summarize changed behavior, compatibility implications, and remaining risks.
10. Do not commit or push without explicit permission.

## Useful Commands

Windows Git Bash:

```bash
./gradlew test
./gradlew run
```

Temporary Linux/WSL workaround until the wrapper executable bit is fixed:

```bash
bash gradlew test
```

Inspect repository state:

```bash
git status --short --branch
git log --oneline -20
```

## Desired First Response From Codex

Codex should begin by reporting:

1. Current branch and worktree status.
2. Baseline test result.
3. The concrete cause of duplicate live imports.
4. Which stable external identifiers are available from each provider.
5. A proposed minimal schema/domain change.
6. The migration and rollback strategy.
7. The exact files expected to change.

Do not start implementing until the proposed identity and migration design has been reviewed.
