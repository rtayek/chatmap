# ChatMap Handoff: Harden Web Conversation Enumeration

## Goal

Correct the remaining weaknesses in the exhaustive web-discovery implementation introduced by commit:

```text
88eaaea6603bd661c206069aaacccb62364aa7ce
```

Keep that implementation. Make a focused corrective pass covering:

1. Gemini false-completeness risks.
2. ChatGPT HTTP 429 handling.
3. Provider-specific pagination tests.

Do not redesign the provider architecture.

## Starting procedure

Read the project instructions and inspect the latest repository state.

Run:

```bash
git status --short --branch
git diff --check
./gradlew test
```

Preserve existing work and `.chatmap-local`. Stop if overlapping changes cannot be preserved safely.

Do not commit or push. Ray will review first.

## 1. Make Gemini completeness defensible

The generic scrolling algorithm currently reports `complete` after three rounds with no newly discovered conversations and no scroll movement.

This can produce false completion when:

* No conversation-list scroll container was found.
* The wrong scrollable element was selected.
* The user is logged out.
* The sidebar failed to hydrate.
* Selectors have changed.
* Loading takes longer than the fixed waits.

The existing test that treats three `notFound()` results as a complete empty inventory is unsafe.

### Required behavior

For Gemini:

* Positively identify the scroll container belonging to the conversation list.
* Prefer finding the nearest scrollable ancestor of the known Gemini conversation elements over selecting the largest scrollable `<div>` on the page.
* Only report `complete` when the correct container was found and repeatedly verified at its terminal position after allowing lazy loading to finish.
* If the correct container cannot be identified, report `incomplete` or `unavailable`.
* An empty list may be complete only when the authenticated conversation UI or an explicit provider empty-state has been positively identified.
* A missing list, missing container, selector failure, login page, or unhydrated sidebar must never be reported as complete.
* Continue accumulating stable conversation IDs across virtualized DOM batches.
* Preserve deterministic first-seen ordering.

Do not claim account-wide Gemini completeness if archived or hidden conversations cannot be enumerated. State the exact verified scope.

## 2. Add bounded ChatGPT rate-limit recovery

The ChatGPT conversation endpoint produced HTTP 429 during repeated live enumeration. A fixed 500 ms delay reduces the problem but does not recover from it.

Add bounded retry handling for HTTP 429:

* Honor `Retry-After` when present and reasonable.
* Otherwise use bounded exponential backoff.
* Use a small maximum retry count.
* Preserve the current offset and retry that page.
* Do not duplicate conversations after a retry.
* If retries are exhausted, return `incomplete` with the list accumulated so far and a diagnostic containing the failing offset.
* Do not retry authentication failures indefinitely.
* Keep access tokens inside the authenticated browser context; do not return, log, or persist them.
* Consider avoiding an unnecessary `/api/auth/session` request for every page if that can be done without exposing the token to Java.

Normal and archived enumeration must remain separate, and the final result should deduplicate by durable conversation ID if an item appears in both lists.

## 3. Add provider-specific tests

The generic scrolling tests are useful, but the two authenticated pagination implementations need deterministic tests.

Add tests for Claude covering:

* Multiple pages.
* `has_more=true` followed by `has_more=false`.
* Missing organization identity.
* Malformed responses.
* HTTP errors.
* Pagination safety-limit exhaustion.
* Stable ordering and deduplication.

Add tests for ChatGPT covering:

* Multiple active pages.
* Active plus archived conversations.
* A full final page followed by an empty page.
* Deduplication across pages and between active and archived lists.
* Missing access token.
* Malformed responses.
* Non-retryable HTTP failures.
* HTTP 429 followed by successful retry.
* Repeated HTTP 429 exhausting the retry limit.
* Correct offset after retry.
* Stable ordering.

Add or revise Gemini tests covering:

* Correct conversation-list container found.
* Virtualized entries accumulated.
* Verified bottom reached.
* Container not found produces `incomplete`, not `complete`.
* Empty authenticated list with an explicit empty state.
* Empty or missing list without proof produces `incomplete` or `unavailable`.
* Slow hydration does not cause premature completion.

Use fake CDP responses or small package-private abstractions. Tests must not require live accounts.

## Real-account verification

After automated validation:

1. Confirm the selected ChatMap home and database.
2. Confirm no competing ChatMap process is running.
3. Make a timestamped database backup before any import.
4. Run web inventory twice.
5. Verify that ChatGPT either completes both times or recovers correctly from 429 responses.
6. Record the exact completeness and scope for Claude, ChatGPT, and Gemini.
7. Import only if new conversations are found.
8. Repeat the import to verify idempotence.
9. Verify:

```text
duplicate (source, externalConversationId) = 0
orphan messages = 0
PRAGMA foreign_key_check violations = 0
zero-message chats = 0
```

Do not include credentials, tokens, cookies, or message bodies in reports.

## Validation

Run:

```bash
./gradlew test
./gradlew check
./gradlew eclipse
git diff --check
git status --short --branch
```

Run `./gradlew eclipse` a second time and confirm that it creates no additional tracked changes.

## Final report

Report:

* Exact behavior changed.
* Tests added or changed.
* Live discovery counts for all three web providers.
* Completeness status and verified scope for each provider.
* Whether any 429 occurred and whether retry recovered.
* Import results for both passes, if an import was needed.
* Database-integrity results.
* Files changed.
* Current Git status.
* Confirmation that no commit or push was made.
* Any remaining limitation.
