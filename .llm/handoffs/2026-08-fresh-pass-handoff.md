# ChatMap — Fresh-Pass Findings Handoff
*August 10, 2026*

## Context

Follow-up to `2026-08-review-findings-handoff.md` and
`2026-08-post-refactor-cleanup-handoff.md`, both fully closed. This is a
fresh pass over the codebase with no assumptions carried forward from
earlier rounds. Three items, one of which corrects the record on an item
previously marked "partial progress."

---

## 1. `ChatMapApp` god-class item is still fully open, not partial

**File:** `src/chatmap/ui/ChatMapApp.java`

Still 554 lines, 64 methods (up from 62). The dialog-helper extraction
(`ChatMapDialogs.java`, 47 lines) was real, but the executor-split commit
that landed around the same time added the `*OnBackendLane` routing methods
back into this same file, netting out to zero size change. Both commits
were individually correct — this isn't a mistake to fix, it's confirmation
that the earlier "partial progress" note overstated where this actually
stands.

**Why it matters:** every unrelated UI-layer change has nowhere to go but
back into this one file unless something actively prevents it.

**Suggested direction:** same as before — extract incrementally, one
concern per commit, running the full test suite between each:
1. Background-task orchestration: `ChatMapApp` still calls `runInBackground`
   inline at each call site rather than fully delegating to
   `BackgroundActionRunner`. Check how much of that wiring can move.
2. Remaining `showAndWait` / prompt call sites not yet covered by
   `ChatMapDialogs`.

Don't attempt this in one pass — it's the file the whole GUI depends on, and
a bad extraction here is very visible. If nothing gets picked up on this
item for a while, that's fine; it's genuinely lower urgency than items 2–3
below, which are correctness-adjacent.

---

## 2. `stateLock` boilerplate mirrors the storage layer's `synchronized(conn)` pattern

**File:** `src/chatmap/ui/ChatMapController.java`

11 methods, each with its own hand-written `synchronized (stateLock) { ... }`
block — the same shape as `ChatRepository`'s per-method `synchronized(conn)`
pattern (flagged separately, never addressed there either). Confirms the
underlying suggestion — a shared `locked(Supplier<T> work)` helper — would
now pay off in two places, not one.

**Why it matters:** duplication risk; a new method that forgets the wrapper
compiles fine and silently reintroduces a race.

**Suggested direction:** low priority relative to item 3. If picked up,
consider doing storage and UI together with one shared locking-helper
pattern rather than two separate ones, since they're solving the same
problem independently right now.

---

## 3. `refreshCurrent()` holds `stateLock` across a database call — check for lock-ordering risk

**File:** `src/chatmap/ui/ChatMapController.java`

```java
private ChatListState.Snapshot refreshCurrent(String statusText, long selectedChatId) throws SQLException {
    synchronized (stateLock) {
        List<SearchResult> matches = currentResultsLocked();   // hits the DB
        ...
    }
}
```

`currentResultsLocked()` calls `searchService.searchResults(...)`, which goes
through repository methods that each do their own `synchronized(conn)`. That
means this nests two separate locks — `stateLock` held while waiting on a
repository's connection monitor. `refreshCurrent()` is called from five
places: `summarizeAndTag`, `assignProject`, `clearProject`, `addTag`,
`removeTag`.

Contrast with `fetchLatestChat()` and `loadAllChats()`, which are explicitly
careful to run DB work *before* entering `synchronized(stateLock)` —
`fetchLatestChat()`'s doc comment even states this as the pattern to follow.
`refreshCurrent()` doesn't follow it, and it's the more heavily-used path.

**Why it matters:** low severity today (a search query is fast, not a
multi-minute CLI call), but there's no stated invariant anywhere —
comparable to `TransactionRunner`'s doc comment stating its lock must never
be taken by callers above the storage layer — governing the relationship
between `stateLock` and repository locks. Without that invariant written
down anywhere, nothing stops a future change from acquiring the two locks in
the opposite order somewhere else, which would be a real deadlock, not just
added latency.

**Suggested direction:** two independent parts, either is worth doing on its
own:
1. Restructure `refreshCurrent()` to match `fetchLatestChat()`'s shape — run
   `currentResultsLocked()`'s query before acquiring `stateLock`, then take
   the lock only for the state update. Check whether `filterCriteria` (read
   inside `currentResultsLocked()`) needs to be read under the lock first
   and passed in, since it's currently read implicitly while already locked.
2. Regardless of (1), write down the invariant explicitly — a doc comment on
   `stateLock`'s declaration stating the required lock-acquisition order
   relative to repository locks, the same way `TransactionRunner` documents
   its own. This is cheap insurance even if (1) doesn't happen right away.

---

## Suggested order

1. Item 3 — the only one with real (if currently small) correctness risk.
2. Item 2 — cheap, mechanical, low risk, can piggyback on item 3 if convenient.
3. Item 1 — ongoing, incremental, no rush.
