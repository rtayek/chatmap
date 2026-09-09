# ChatMap Prompt Mode — History Toggle and Route Visibility Handoff

## Recipient

Give this handoff to **Claude or Codex working in `rtayek/chatmap`**.
Two small view-wiring fixes, both in `ChatMapViewBuilder.java` /
`ChatMapApp.java`.

## 1. History needs to be a toggle, not a permanent box

The prior usability-fixes handoff asked for `historyArea` to be a
separate, toggleable panel (View menu `CheckMenuItem`, default off,
same `bindBarVisibility` pattern already used for Search/Project/Tag
bars) — explicitly not merged into `responseArea`. The "don't merge"
part landed correctly. The "toggleable, default off" part did not:
`historyArea` is currently hardcoded permanently into the pane:

```java
VBox pane = new VBox(SECTION_GAP,
        controls,
        activeChatLabel,
        historyArea,
        responseArea,
        promptInput);
```

This silently reintroduces a permanently-visible box on the minimal
Prompt screen — exactly what today's redesign was working to avoid.

Fix: add a `CheckMenuItem` ("History panel") to the View menu,
default unchecked, bound to `historyArea`'s `visible`/`managed`
properties together via the same `bindBarVisibility(Node, CheckMenuItem)`
helper already used for the other bars (see `ChatMapApp.java`).

## 2. Route/model indicator is built but not shown

`PromptResultDisplay.routeText(result)` already produces a thorough
string — channel, target display name and id, model name (if
present), provider/backend label, and session id (if present).
`classificationLabel` and `routeLabel` are both created in
`ChatMapViewBuilder.createPromptPane(...)` and returned via
`PromptPaneWidgets` — but neither is actually added to the visible
`pane` `VBox`. They exist, get updated correctly by
`showPromptResult` in `ChatMapApp.java`, and are simply never shown.
This is the same category of bug the history area had before this
handoff.

Fix: add `classificationLabel` and `routeLabel` to the `pane` VBox
(near `activeChatLabel` is a reasonable spot — all three are
status-style single-line indicators about the current session).

### Also: preview the route before sending, for resumed chats

Currently `routeLabel` only updates after a response comes back — it
confirms where a prompt went, but gives no indication beforehand.
For a **resumed** chat specifically, the model is already fixed before
sending (routing reuses the original chat's `modelTargetId` — see the
resume-conversation handoff), so there's no reason not to show it at
selection time.

When a chat is picked via `resumeChatChoice`, update `routeLabel` (or
a similar preview string) to reflect that resumed chat's existing
provider/model, before any prompt is sent — not just after. For a
brand-new (non-resumed) conversation, it's fine for `routeLabel` to
stay at its "Route: none" default until the first prompt is actually
classified and routed, since there's genuinely nothing to preview
until then.

## Tests Required

- History panel: hidden by default on launch; toggling the View menu
  `CheckMenuItem` shows/hides `historyArea` (assert both `isVisible()`
  and `isManaged()`, not just the menu item's own state).
- `classificationLabel` and `routeLabel` are present in the scene
  graph after `createPromptPane(...)` (regression test — this is
  exactly the kind of thing that silently regresses again if not
  covered).
- Selecting a resumed chat updates the route indicator to that chat's
  existing model/provider before any prompt is sent.
- Sending a prompt in a fresh (non-resumed) conversation still updates
  `routeLabel` correctly after the response returns, unchanged
  behavior from before this handoff.

## Non-Goals

- No changes to `PromptResultDisplay.routeText()`'s actual content —
  it's already thorough; this handoff is purely about making the
  existing labels visible and adding the pre-send preview for resumed
  chats.
- No changes to routing/classification logic itself.

Run:

```bash
./gradlew test
```
