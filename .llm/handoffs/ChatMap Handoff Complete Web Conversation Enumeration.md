# ChatMap Handoff: Complete Web Conversation Enumeration

## Goal

Make ChatMap enumerate every discoverable conversation from:

* Claude web
* ChatGPT web
* Gemini web

The current adapters only reconcile the conversations initially visible in each sidebar. They must handle lazy loading, scrolling, and virtualized lists, and must not report a source as complete unless enumeration actually reaches a defensible fixed point.

Do not redesign unrelated parts of ChatMap.

## Verified starting state

The latest reconciliation report is:

```text
.chatmap-local/reports/conversation-reconciliation-20260812T063449Z/reconciliation-summary.md
```

Current web discovery counts:

| Source      | Discovered | Status                   |
| ----------- | ---------: | ------------------------ |
| Claude web  |         30 | Incomplete, sidebar only |
| ChatGPT web |         25 | Incomplete, sidebar only |
| Gemini web  |         23 | Incomplete, sidebar only |

The database currently contains:

* 677 chats
* 23,119 messages
* No duplicate external identities
* No orphan messages
* No foreign-key violations

The previous reconciliation was idempotent and had no genuine import failures.

## Starting procedure

1. Read the project instructions and live design documents.
2. Check out the latest intended branch and inspect recent commits.
3. Run:

```bash
git status --short --branch
git diff --check
./gradlew test
```

4. Preserve all existing work and ignored local data.
5. Do not delete or recreate `.chatmap-local/chatmap.db`.
6. Do not commit or push. Ray will review first.

If the working tree contains overlapping changes that cannot safely be preserved, stop and report them.

## Required investigation

For each web provider, inspect the current discovery implementation and determine:

* How conversation elements are located.
* How stable conversation IDs and URLs are extracted.
* Whether the sidebar is lazy-loaded or virtualized.
* What happens when the sidebar is scrolled.
* Whether older elements disappear from the DOM during scrolling.
* How the provider indicates the true end of the list.
* Whether archived conversations are accessible separately.
* Whether search can reveal conversations omitted from the normal sidebar.
* Whether an authenticated browser endpoint already used by the page provides a safer complete listing.

Use the existing authenticated browser/CDP architecture. Do not add Playwright, Selenium, or another browser-automation dependency.

Do not copy authentication credentials, cookies, or tokens into files or reports.

## Implementation requirements

### 1. Exhaustive scrolling and accumulation

For each provider:

* Scroll through the conversation list until a defensible end condition is reached.
* Accumulate conversation identities across every scroll step; do not depend on all elements remaining in the DOM.
* Deduplicate using the stable provider conversation ID.
* Preserve deterministic ordering.
* Tolerate virtualized lists where older elements disappear as newer ones appear.
* Wait for lazy-loaded entries after each scroll.
* Avoid arbitrary conversation-count limits.

A timeout or repeated browser/selector failure must produce an incomplete or failed result, not a successful complete result.

### 2. Explicit completeness result

Return structured discovery information equivalent to:

```text
provider
discoveredCount
conversationIds
complete
incompleteReason
```

The exact type design may follow existing project conventions.

Distinguish at least:

* Complete: the implementation reached a verified terminal condition.
* Incomplete: enumeration stopped without proving the end of the list.
* Unavailable: browser, authentication, CDP, or provider UI was unavailable.
* Failed: an unexpected error prevented enumeration.

Do not label “no newly discovered chats” as complete unless the complete list was actually enumerated.

### 3. Stable identities

Continue using durable provider identities:

* Claude: ID from `/chat/<uuid>`
* ChatGPT: ID from `/c/<id>`
* Gemini: determine and test the most durable ID available from its conversation URL or page data

Do not use titles as identities.

### 4. Archived or hidden conversations

Investigate archived or otherwise hidden conversation lists.

If they can be enumerated safely through the authenticated UI or browser traffic already used by the page, include them and identify their category.

If they cannot be enumerated, explicitly report that limitation. Do not claim complete account coverage.

### 5. Reconciliation reporting

Update reconciliation output so each provider clearly reports:

* Discovered
* Already imported
* Newly inserted
* Updated
* No content
* Failed
* Complete or incomplete
* Reason when incomplete

“Complete” should mean complete within a clearly stated provider scope, such as:

```text
Complete for the normal and archived conversation lists exposed by the current authenticated web UI.
```

## Tests

Add deterministic tests covering:

1. Multiple lazy-loaded pages or scroll batches.
2. Virtualized lists that remove earlier DOM entries.
3. Duplicate IDs appearing across scroll batches.
4. Delayed loading after scrolling.
5. A genuine terminal condition.
6. A fixed point that does not prove completion.
7. Timeout or selector failure reported as incomplete/unavailable.
8. Stable ordering.
9. Stable identity extraction for all three providers.
10. Existing empty-content behavior remains distinct from discovery failure.

Use fixtures or fake CDP/browser responses. Automated tests must not require live provider accounts.

## Real-data verification

After automated tests pass:

1. Confirm the selected database path.
2. Confirm no competing ChatMap process is using it.
3. Make a timestamped backup.
4. Run discovery for all three web providers.
5. Record the complete set of discovered IDs.
6. Import any newly discovered conversations.
7. Repeat discovery and import.
8. Verify that the second pass is idempotent.
9. Run database integrity checks:

```text
duplicate (source, externalConversationId)
orphan messages
zero-message chats
PRAGMA foreign_key_check
FTS sanity query
```

For ChatGPT, compare discovered web IDs with the 301 ChatGPT archive conversations already imported. Explain mismatches without assuming that every archive conversation must still be available on the website.

## Validation

Run:

```bash
./gradlew test
./gradlew check
./gradlew eclipse
git diff --check
git status --short --branch
```

Run `./gradlew eclipse` a second time and confirm that it produces no additional tracked changes.

## Deliverables

Create a report under:

```text
<CHATMAP_HOME>/reports/web-conversation-enumeration-<timestamp>/
```

Include:

* `enumeration-summary.md`
* `provider-counts.csv`
* `missing-or-unavailable.csv`
* `failures.csv`

Do not include message bodies, authentication data, cookies, or tokens.

The final response must state:

* The before-and-after count for each provider.
* Whether each provider is truly complete and within what scope.
* Any remaining provider limitation.
* Import results from both passes.
* Database-integrity results.
* Tests and checks run.
* Files changed.
* Current Git status.
* Confirmation that no commit or push was made.
