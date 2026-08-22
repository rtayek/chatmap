# ChatMap Prompt Status Indicator and Focus Return — Implementation Handoff

## Recipient

Give this handoff to **Claude or Codex working in `rtayek/chatmap`**.

## Purpose

Accessibility gap: right now the only feedback that a prompt is being
processed is a text change in the status label (small text, easy to
miss) and the Send button becoming disabled. Add a high-visibility
color-state indicator for the send/wait/response cycle, plus
automatic focus return to the prompt field so it's obvious — without
reading small text — when the app is busy and when it's ready for
input again.

## Scope Decision — Confirm Before Building Broadly

`BackgroundActionRunner.setPending(...)`/`finish(...)` are shared hook
points already used by **every** background action (import, export,
search, inventory, summarize, prompt-send) — not prompt-send
specifically. This means the busy/ready color pattern could be:

- **App-wide**: built into `BackgroundActionRunner` itself, so every
  long-running action gets the same visual feedback automatically.
- **Prompt-specific**: only touching `sendPrompt`/`showPromptResult`
  in `ChatMapApp.java`.

Recommend **app-wide** — the hook points already exist, it's not
meaningfully more work, and it gives a stronger, more consistent
accessibility signal across the whole app rather than just one
feature. But this is a real scope expansion beyond what was literally
asked for (which was specifically about the prompt send/response
cycle) — confirm with the user before committing to the broader
version if there's any doubt.

## Design

### Color states

- **Busy**: the instant an action starts (`setPending` runs, or
  `sendPrompt` specifically if scoped narrowly), the status
  indicator switches to a clearly distinct "busy" color — red is
  what was asked for, but consider whether red specifically reads as
  "error" to a user and whether a busy-but-not-alarming color (e.g.
  amber/orange) is less confusing while something is simply in
  progress, reserving red for actual failures (see Error State below).
  Confirm color choice with the user rather than assuming red is
  right for the non-error "working" state.
- **Ready (success)**: when the action completes successfully,
  animate a transition from the busy color to a "ready" color (green)
  — this must be a real animation (JavaFX `Timeline`/`Transition`
  interpolating a `Color` and re-applying style each frame, or a
  `FillTransition` on a backing `Region`/`Rectangle`), not an instant
  style swap. JavaFX does not animate CSS property changes the way
  web CSS transitions do; a snap from red to green is what happens by
  default if this isn't explicitly built. Target roughly 400-800ms for
  the transition — exact timing isn't critical, just needs to be
  perceptible as a transition, not a jump cut.
- **Error state**: an action that fails should not transition to
  green — use a distinct, clearly-different treatment (e.g. stays on
  or snaps to red/an error color, doesn't animate toward green at
  all). This wasn't explicitly specified by the user but is a
  necessary decision — don't let a failed prompt visually read as
  successful.
- **Settling behavior**: after reaching the ready color, decide
  whether it stays green indefinitely or fades to a neutral/default
  color after a short delay (a few seconds). Either is defensible;
  confirm with the user rather than guessing — staying green risks
  looking stale/misleading before the next action, fading back risks
  losing the "did that work?" confirmation if the user looks away
  briefly.

### Focus return

When an action completes (success case specifically — for prompt
sending, this means after `showPromptResult` runs), call
`promptArea.requestFocus()` so the cursor is placed back in the
prompt field automatically. The user should never need to click into
the field again after a response arrives.

Consider pairing this with a brief visual highlight on the prompt
field itself (e.g. a temporary border-color pulse, similar
duration/mechanism to the status color transition) to reinforce
"ready for input" beyond just the status indicator — the user asked
for "the prompt window should be highlighted... something to
indicate the prompt is waiting to be used." This is a second,
complementary signal to the status color, not a replacement for it.

## Where to Hook In

- If scoped app-wide: `BackgroundActionRunner.setPending(...)` (busy
  color) and the `finish(...)` paths in `valueRunnable`/
  `snapshotRunnable` (ready/error color, split on success vs.
  exception — currently both paths call the same `finish(setDisabled)`
  with no success/failure distinction passed through, so this will
  need a new parameter or a second callback to differentiate).
- If scoped to prompt-send only: `sendPrompt()` (busy color, start)
  and `showPromptResult(...)` (ready color, success) /
  `reportError(...)` (error color, failure) in `ChatMapApp.java`.

## Tests Required

- Status indicator reaches the busy color when an action starts.
- Status indicator transitions to the ready color on success (verify
  the transition mechanism runs — e.g. a `Timeline` was started — not
  just that the end color is eventually correct).
- Status indicator does **not** reach the ready/green color on
  failure — reaches the distinct error treatment instead.
- `promptArea` receives focus after a successful prompt response.
- If app-wide: confirm other actions (search, import, etc.) exhibit
  the same busy→ready/error behavior, not just prompt-send.

## Non-Goals

- No change to which actions run on which background lane — this is
  purely a visual feedback layer on top of the existing
  `BackgroundActionRunner` structure.
- No change to button-disable behavior during pending state — that
  already works and is unaffected by this handoff.

Run:

```bash
./gradlew test
```
