# Codex Handoff: Inventory Conversations From Every ChatMap Source

## Immediate Goal

Make ChatMap produce one combined inventory of every **discoverable** conversation from all six configured sources:

1. Claude Web
2. ChatGPT Web
3. Gemini Web
4. Claude Code history
5. Codex CLI history
6. Gemini CLI history

This milestone is **discovery and visibility**, not bulk transcript import. It should tell Ray what conversations exist, where they came from, and which ones are already in the ChatMap database.

Do not claim that a web inventory is complete unless the provider implementation can establish that. Web sidebars may use lazy loading, pagination, archived sections, or virtualized DOM elements. Use the phrase **all discoverable conversations** and report provider limitations explicitly.

## Repository

- Repository: `github.com/rtayek/chatmap`
- Local project is normally `cjatmanager`.
- Preserve the current architecture and existing user work.
- Do not commit or push unless Ray explicitly requests it.

## Starting Procedure

1. Read the live project instructions and design documents.
2. Inspect the current branch and working tree:

   ```bash
   git status --short --branch
   git diff --check
   ./gradlew test
   ```

3. Stop and report if existing changes overlap this work and cannot be preserved safely.
4. Do not reset, clean, discard, or overwrite unrelated files.

## Current Verified Structure

The current public provider seam exposes only:

```java
String name();
Optional<ImportedChat> latestChat() throws Exception;
```

`LiveChatFetchService.resolve(null)` walks `DefaultChatProviders.ordered()` and imports the first nonempty `latestChat()` result.

Much of the iteration machinery already exists but is collapsed to the newest item:

| Source | Existing useful code | Current limitation |
| --- | --- | --- |
| Claude Web | `ClaudeWebAdapter.listChats(...)` builds title/URL summaries from sidebar links | Base adapter opens only element zero |
| ChatGPT Web | `ChatGptWebAdapter.listChats(...)` builds title/URL summaries from `/c/<id>` links | Base adapter opens only element zero |
| Gemini Web | `GeminiWebAdapter` locates all `[data-test-id='conversation']` elements | It clicks only `first()` and returns no list |
| Claude Code | `LocalCliSessions` recursively walks `*.jsonl`; provider can parse any supplied file | `max(...)` retains only the newest file |
| Codex CLI | Same shared file walk; provider can parse any supplied file | Only newest file retained |
| Gemini CLI | Same shared file walk; provider can parse any supplied file | Only newest file retained |

Relevant packages:

- `chatmap.backend.providers`
- `chatmap.backend.web`
- `chatmap.service`
- `chatmap.storage`
- `chatmap.ui`
- focused tests under `tst/chatmap/...`

## Required Design

Introduce a provider-neutral metadata record. Use the existing lower-camel-case identifier conventions and stable `Source.dbValue` values.

An acceptable shape is:

```java
public record ConversationCandidate(
        Source source,
        String externalConversationId,
        String title,
        String sourceUri,
        String updatedAt) {
}
```

If imported state is needed, keep it outside the provider-owned candidate, for example:

```java
public record InventoryEntry(
        ConversationCandidate candidate,
        Long importedChatId) {

    public boolean alreadyImported() {
        return importedChatId != null;
    }
}
```

Add an inventory result that retains provider boundaries, counts, and diagnostics. One unavailable or broken provider must not discard results from the other providers.

For example:

```java
public record ProviderInventory(
        String providerName,
        List<InventoryEntry> conversations,
        boolean complete,
        String diagnostic) {
}
```

Names may be adjusted to fit the current code, but preserve these responsibilities.

### Provider API

Extend `ChatProvider` so callers can discover candidates and fetch a chosen candidate. Preserve `latestChat()` behavior for existing callers and tests.

An acceptable direction is:

```java
List<ConversationCandidate> listChats() throws Exception;

ImportedChat fetch(ConversationCandidate candidate) throws Exception;
```

`latestChat()` may remain abstract during a staged migration or become a compatibility default based on the first candidate. Avoid duplicating provider parsing logic.

The discovery operation must not call `ImportService.persist(...)` and must not fetch every complete transcript merely to obtain the inventory.

## Provider-Specific Work

### CLI histories

Refactor `LocalCliSessions` to expose all session files recursively in deterministic newest-first order.

Suggested behavior:

```java
static List<Path> listSessionFiles(Path root)
```

Requirements:

- Include regular `*.jsonl` files only.
- Sort by modification time descending.
- Add a deterministic path tie-breaker.
- Preserve or reimplement `newestSessionFile(...)` by taking the first result so current behavior remains unchanged.
- Use the existing `ProviderIdentity.cliSessionId(root, file)` for durable identity.
- Use normalized absolute file URIs for `sourceUri`.
- Do not parse every complete transcript just to list candidates when filename, path, and file metadata are sufficient.
- Surface an unreadable-root diagnostic in the inventory instead of silently making the entire multi-provider scan fail.

### Claude Web and ChatGPT Web

Expose and reuse the existing sidebar summary iteration instead of rewriting selectors.

Requirements:

- Return all distinct title/URL summaries currently discoverable.
- Derive durable external IDs with the existing `ProviderIdentity` helpers.
- Preserve sidebar order, normally newest first.
- Deduplicate by normalized URL or external identity.
- Account for lazy loading with a bounded scroll/load loop if practical.
- Stop when no new identities appear, the end is detected, or a documented safety bound is reached.
- Mark the result incomplete and provide a concise diagnostic if only the currently rendered sidebar entries can be guaranteed.
- Do not read every conversation transcript during listing.

### Gemini Web

Gemini currently navigates through JavaScript clicks rather than stable sidebar links.

Requirements:

- Enumerate the available sidebar elements and titles.
- Investigate whether clicking an entry produces a stable `page.url()` or another durable conversation identifier.
- Do not invent a durable ID if the live site does not provide one.
- A candidate with a session-local selector and no durable external ID is acceptable for the first inventory milestone, but it must be marked or diagnosed clearly.
- Keep navigation bounded and avoid transcript extraction during listing.
- If exhaustive listing cannot be made reliable, return the discoverable entries with `complete=false` and explain why.

## Inventory Service

Add a service that queries all configured providers and returns a combined provider-grouped result.

Requirements:

- Preserve `DefaultChatProviders.ordered()` ordering unless there is a good documented reason not to.
- Isolate provider failures: record the failure and continue.
- Compare non-null `(source, externalConversationId)` identities with the database.
- Determine imported status using a bulk repository query, not one SQL query per candidate.
- Do not modify chats, messages, tags, summaries, or import timestamps.
- If two providers return the same identity within one source, retain one deterministic candidate and report/deduplicate the rest.

## User-Visible Result

Provide at least one production entry point that Ray can use without writing Java code.

Preferred first version:

- Add a JavaFX **Discover Conversations** or **Conversation Inventory** action.
- Run discovery through the existing background execution mechanism.
- Display results grouped or sortable by source.
- Show at minimum:
  - source/provider
  - title
  - updated time when known
  - imported/missing status
  - source URI or session filename when useful
- Display provider counts and diagnostics.
- Respect the application-wide font size and existing low-vision behavior.

A simple noneditable list or table is sufficient. Do not build bulk import in this pass unless it falls out trivially and remains clearly separated from discovery.

Also consider a small CLI/Gradle entry point for deterministic testing and troubleshooting. It may print TSV or a readable grouped report, but it must not expose message bodies.

## Non-Goals

Do not include these in this pass:

- Importing every discovered transcript
- Semantic extraction, embeddings, keywords, or clustering
- Browser automation framework changes
- Replacing CDP
- Database schema changes unless strictly necessary for inventory status
- A connection-pool or storage-architecture redesign
- Background schedules or continuous monitoring
- Deleting or rewriting existing provider functionality

## Tests

Add focused browser-free tests wherever possible.

Minimum coverage:

1. `LocalCliSessions` returns every nested JSONL file newest-first with deterministic ties.
2. Non-JSONL files are ignored.
3. Each CLI provider exposes multiple candidates and can still fetch a selected session.
4. Claude and ChatGPT sidebar-summary code returns multiple distinct candidates.
5. Duplicate URLs/identities are removed deterministically.
6. One provider failure does not suppress successful provider inventories.
7. Imported status is correct for existing and missing external identities.
8. Inventory performs no database writes.
9. Existing `latestChat()` and `LiveChatFetchService` behavior remains covered and passing.
10. UI/controller tests cover displaying multiple providers and provider diagnostics without requiring a real browser.

Do not make tests depend on Ray being logged into live websites. Keep live verification as a separately reported manual smoke test.

## Manual Smoke Test

If the environment permits, perform a read-only/manual discovery against Ray's configured sources.

Report:

- count discovered from each source
- count already imported and missing
- whether enumeration is believed complete
- provider diagnostics
- whether Gemini exposed a durable identifier
- whether any browser sidebar required scrolling/lazy loading

Do not print transcript bodies, credentials, tokens, or private message excerpts.

## Validation

Run at minimum:

```bash
./gradlew compileTestJava
./gradlew test
./gradlew eclipse
git diff --check
git status --short --branch
```

Run `./gradlew check` as well if the repository's configured static-analysis tasks are currently healthy. Report separately if an existing unrelated check failure prevents completion.

## Acceptance Criteria

The milestone is complete when:

1. ChatMap can ask every configured source for a list of discoverable conversation metadata.
2. The results from all six sources can appear in one combined inventory.
3. Provider failure is visible but does not abort other providers.
4. Each durable candidate carries its existing stable source identity when available.
5. The inventory distinguishes already-imported conversations from missing ones.
6. Discovery does not import transcripts or change the database.
7. Existing single-latest-chat behavior still works.
8. Automated tests pass.
9. Web completeness limitations are reported honestly.

## Final Report

Stop without committing or pushing. Provide a concise report containing:

- design implemented
- files changed or added
- results and counts by provider, if live smoke testing was possible
- automated test totals and commands run
- database read/write verification
- current Git status
- remaining limitations, especially web lazy loading and Gemini identity

