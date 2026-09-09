# ChatMap — Post-Refactor Cleanup Handoff
*August 10, 2026*

## Context

Follow-up to `2026-08-review-findings-handoff.md`. All 9 items from that
handoff are now done, including both provider triplets (CLI-history and
web). This is a small, separate handoff for cleanup opportunities that only
became visible after the web-provider extraction (`CdpWebChatProvider`)
landed — they weren't refactoring opportunities before that commit existed.

Both items are small and low-risk. Fine to do in one commit together, or
split — your call.

---

## 1. Delete dead code: `HttpChatProvider.java`

**File:** `src/chatmap/backend/providers/HttpChatProvider.java`

Zero callers anywhere in `src/`. The only reference to it at all is a
comment in `ClaudeWebChatProvider.java` calling it "the old
`HttpChatProvider`" — it was superseded and never removed. Same situation
`ChatGptDto.java` was in a few commits back.

**Action:** delete the file. Check `tst/` for a matching test file
(`HttpChatProviderTest.java` turned up in an earlier `git pull` diff) and
delete that too if it only tests the dead class. Confirm nothing in
`ServiceGraph`/`DefaultServiceIntegrations` constructs it before removing —
should be a no-op given the grep above, but worth a final check before
deleting.

---

## 2. Collapse `LiveSourceExchanges`'s now-redundant `instanceof` chain

**File:** `src/chatmap/cli/LiveSourceExchanges.java`

`unavailableReason()` checks `instanceof ClaudeWebChatProvider`, then
`ChatGptWebChatProvider`, then `GeminiWebChatProvider` individually, each
branch calling that class's `lastUnavailableReason()`. All three now extend
`CdpWebChatProvider`, which already exposes `lastUnavailableReason()` itself
(delegating to the shared adapter) — the chain was necessary before the
extraction and is dead weight now.

**Suggested fix:**

```java
private static Optional<String> unavailableReason(ChatProvider provider) {
    return provider instanceof CdpWebChatProvider cdp
            ? cdp.lastUnavailableReason()
            : Optional.empty();
}
```

Confirmed via grep that this is the only `instanceof` chain against the old
three concrete web-provider types anywhere in the codebase — no other call
site needs the same treatment.

---

## Optional, smaller than the above — only if convenient

**`DEFAULT_CDP_URL` lives on the wrong class.** `ChatGptWebChatProvider` and
`GeminiWebChatProvider` both reference `ClaudeWebChatProvider.DEFAULT_CDP_URL`
— a constant defined on a sibling subclass rather than on `CdpWebChatProvider`,
the shared base all three now extend. Harmless as-is, but backwards now that
there's a natural home for it. Move the constant to `CdpWebChatProvider` and
update the two references, or leave it if there's a reason it's pinned to
`ClaudeWebChatProvider` specifically that isn't visible from the outside.
