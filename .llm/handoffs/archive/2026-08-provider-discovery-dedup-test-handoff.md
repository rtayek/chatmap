# Handoff: test that discovery recognizes already-persisted chats (reverse direction)

Companion to `2026-08-provider-round-trip-test-handoff.md`, but a genuinely
separate gap — kept as its own handoff rather than folded into that one, so
the two don't get confused with each other.

---

## The direction that handoff doesn't cover

That handoff tests the forward path: `listChats()` -> `fetch()` -> `persist()`
-> search -> export, plus persist-time dedup (fetch and persist the same
candidate twice, expect the second to come back `Outcome.unchanged`).

This handoff is the reverse: starting from a chat that's *already* persisted,
does fresh discovery correctly recognize it without needing to fetch it
again?

## Why this is a different code path, not the same thing restated

`ImportAllChatsCli` doesn't rely on persist-time dedup for its main
efficiency win. It checks
`ChatRepository.findImportedIdsByExternalIdentity(candidates)` *before*
calling `fetch()` at all, using only the metadata `listChats()` already
returned. That's what makes re-running `importAllChats` against a large
local history (Gemini's 1500+ candidates, say) cheap on a second run,
instead of re-fetching everything just to find out it's already there.

The persist-time `unchanged` test in the other handoff proves fetch+persist
is idempotent. It does NOT prove the pre-fetch skip optimization works,
since that path never calls `fetch()` in the first place. If discovery-side
matching were broken, nothing in the forward-direction test would catch it.

## Suggested test

Add to the same test class as the forward-direction one (or a sibling), once
that exists:

1. Persist a candidate via the normal forward path (`listChats()` ->
   `fetch()` -> `persist()`).
2. Call `provider.listChats()` again against the same directory — a fresh
   discovery pass, not reusing the earlier candidate list.
3. Call `chats.findImportedIdsByExternalIdentity(candidates)` with that
   fresh list.
4. Assert the returned map contains the persisted candidate's identity key
   (`ChatRepository.identityKey(candidate.source(), candidate.externalConversationId())`)
   and that the mapped chat id matches the one persist returned in step 1.

Worth also asserting the negative case: a *new*, never-persisted candidate
added to the same directory should NOT appear in that map — proving the
check doesn't just always say "yes" once the directory's been touched once.

## Explicitly out of scope for this handoff

Same scope boundary as the companion handoff: web providers (CDP-based) and
the `ImportAllChatsCli` entry point itself are separate, larger pieces of
work, not part of this one.
