# ChatMap Minimal Prompt Mode — Implementation Handoff

## Recipient

Give this handoff to **Claude or Codex working in `rtayek/chatmap`**.
Small, self-contained UI layout change, no new backend logic.

## Purpose

Test a stripped-down, focused layout for active prompting before
deciding what else the UI needs. Ship the minimal version first, use
it, and let real gaps drive the next round of design rather than
guessing upfront.

## Layout

Replace the current always-visible chat-browser split (chat list +
detail) with a single-purpose prompt screen:

- **Top**: thin bar — Project picker, Conversation field, Resume-chat
  picker. Same widgets that exist today in the Prompt pane, just
  without the chat list/detail split beneath them.
- **Bottom**: prompt input, pinned, with the Send button.
- **Middle**: response area, large — takes up most of the remaining
  vertical space. Shows only the **most recent response** — no merged
  transcript, no separate History box.
- The existing chat list + detail split (the "browse" view) is not
  shown in this mode at all — not collapsed, not a toggle, just not
  present. This is validating the sparsest possible version; the
  browse view can come back as a separate mode/toggle later if it
  turns out to be missed.

## Explicitly Deferred — Do Not Build Yet

These came up in design discussion but are intentionally out of scope
for this pass. Don't build speculative support for them:

- **Up-arrow prompt history** (readline-style cycling through
  previously typed prompts in the input field). Real feature, small
  but real work (JavaFX `TextArea`/`TextField` don't have this built
  in) — deferred until the minimal layout has actually been used.
- **Resumed-chat transcript display** (loading a resumed chat's full
  prior history into view, not just its most recent turn). Also
  deferred — for now, resuming a chat just continues the session
  server-side (already working, see the resume-conversation handoff);
  it doesn't need to visually replay prior turns yet. **Flagged as the
  likely next thing to revisit** once this minimal layout has actually
  been used — not part of this pass, but don't be surprised if it's
  the first gap that shows up.
- Any merging of "History" and "Response" into one shared scrollback
  — was discussed as an option, not adopted. Don't build it.

## Non-Goals

- No tabs, no detachable windows — separate, later handoff, only
  worth pursuing once single-session use has actually been tried and
  found lacking.
- No changes to `PromptRouterService`, `PromptService`, or any
  backend/routing logic — this is a pure layout change using existing
  wired-up actions.
- No removal of the browse view's code — it should still exist and be
  reachable, just not shown by default in this minimal test layout.
  **Resolved**: switch modes via the existing `View` menu, matching
  the established pattern (`View > Browse Chats`, or a radio pair
  `View > Prompt` / `View > Browse`) — same mechanism already used for
  the bar-visibility toggles, no new interaction pattern needed. A
  keyboard shortcut alongside it is a cheap optional add, not
  required for this pass.

## Completion Criteria

Launching into prompt mode shows only: top project/conversation/resume
bar, a large response area showing the last response, and a pinned
prompt input at the bottom. No chat list, no detail pane, no History
box, visible by default. The user can actually send prompts and use
this end to end to find out what's missing.

Run:

```bash
./gradlew test
```
