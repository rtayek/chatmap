# ChatMap Prompt Mode Usability Fixes — Implementation Handoff

## Recipient

Give this handoff to **Claude or Codex working in `rtayek/chatmap`**.
Four small, related fixes, all in `ChatMapViewBuilder.java` /
`ChatMapApp.java`, found from actually using the new minimal Prompt
mode. Bundled into one handoff rather than four.

## 1. Clear the prompt field after sending

Currently the submitted prompt text stays in `promptArea` after
`sendPrompt` completes. Clear it (`promptArea.clear()`) once the
prompt has been successfully submitted — not before, so a failed send
doesn't lose what the user typed.

## 2. Enter sends; Shift+Enter inserts a newline

No key handling currently exists on `promptArea` — confirmed via
`grep`, there's no `setOnKeyPressed`/`KeyEvent` handling anywhere in
`ChatMapViewBuilder.java`. Add an event filter: plain `Enter` triggers
the same action as clicking Send (and consumes the event, so it
doesn't also insert a newline); `Shift+Enter` inserts a newline as
normal `TextArea` behavior does today.

## 3. Show which chat is currently active

There is currently no persistent indicator of the active chat at all.
`promptConversationField` is purely free-text the user types
manually — confirmed via `grep`, it's set up once and never written
to programmatically anywhere else in `ChatMapApp.java`. It does not
reflect whichever chat is actually selected, resumed, or just
created.

Add a small, always-visible label (e.g. next to or below the top
controls bar) showing the active chat's title, updated:
- When an existing chat is picked via `resumeChatChoice`.
- When a new chat is created as the result of a send (use the title
  `PromptService` already generates — first 40 characters of the
  prompt, see `PromptService.java` around the `title` assignment).
- Cleared/reset to something like "New conversation" when neither
  applies yet (fresh session, nothing sent).

Decide whether this replaces `promptConversationField` entirely or
sits alongside it as a separate read-only indicator — the free-text
conversation field and "which chat is active" may be two genuinely
different concepts (a user-chosen label vs. the actual resolved
chat), don't conflate them without confirming that's fine.

## 4. Fix the chat display string

`ChatMapViewBuilder.namedChatConverter()` currently renders:

```java
String session = chat.providerSessionId() == null || chat.providerSessionId().isBlank()
        ? "" : " / " + chat.providerSessionId();
return title + " [" + chat.id() + session + "]";
```

The raw `providerSessionId` (a long UUID/hex string) dominates the
visible text in the resume-chat picker, even though `title` is
already meaningful. Drop the session id from the display string
entirely — `title + " [" + chat.id() + "]"` is enough for a human to
recognize and disambiguate. The session id is still there on the
`Chat` object for actual routing/resolution logic; this change is
display-only, not a data change.

## 5. Wire up history in the new layout

The old three-column History/Prompt/Response layout is gone (see the
minimal-prompt-mode handoff), but `historyArea` still exists and is
still being populated by `loadPromptHistory` on every send/resume —
it's just no longer attached to the visible scene graph, so this work
currently happens for nothing.

Lightest version, reusing what's already there: when a chat is
resumed, prepend its loaded history into `responseArea` itself (so
`responseArea` acts as a running scrollback — prior turns followed by
the new prompt/response) rather than resurrecting a separate visible
History box. This directly reuses the `loadPromptHistory` plumbing
that already runs, with no new data-fetching work needed — just
change where the result is displayed.

This is a design call the user only said "maybe" to — confirm the
scrollback approach actually matches what's wanted before building it
out further than a first pass. If it doesn't, the fallback is
reintroducing a slim, possibly collapsible history area above
`responseArea` instead of merging them.

## Tests Required

- Prompt field is empty after a successful send, unchanged after a
  failed one.
- Enter key triggers send; Shift+Enter inserts a newline and does not
  send.
- Active-chat indicator updates correctly on resume-selection and on
  new-chat-creation-via-send; resets appropriately for a fresh
  session.
- `namedChatConverter()` output no longer contains the session id.
- History display: resuming a chat with prior turns shows them in
  the chosen location (responseArea or a separate area, per whichever
  design is confirmed) before the next response is appended.

## Non-Goals

- No change to how titles are generated (`PromptService`'s
  first-40-characters logic) — display-only fixes in this handoff.
- No up-arrow prompt-history cycling — still deferred, tracked
  separately in the minimal-prompt-mode handoff.
- No tabs/detachable windows — unrelated, separate handoff.

Run:

```bash
./gradlew test
```
