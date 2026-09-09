# ChatMap — Tackling ChatMapApp
*August 10, 2026*

## Context

Item 1 from `2026-08-fresh-pass-handoff.md`, on its own since it's a bigger,
slower job than the other two (which are already done). `ChatMapApp.java` is
554 lines, 64 methods. Two extractions already happened —
`ChatMapViewBuilder` (leaf widget builders, formatters) and `ChatMapDialogs`
(modal dialog construction) — so this isn't a from-scratch job, it's
continuing a pattern that's already proven out twice.

**Ground rule from last time this was discussed: one extraction per commit,
full test suite between each.** This file is load-bearing for the whole GUI.
A previous round noted that the file's size didn't actually drop even after
a real extraction, because an unrelated commit added code back into it in
the same window — that's not a reason to rush, it's a reason to keep each
step small and independently verifiable.

I went through all 64 methods and grouped them by what they actually do.
Below is the grouping, in the order I'd tackle them — easiest and safest
first, hardest and most coupled last.

---

## Group A — Toolbar/layout composite builders (lowest risk, do first)

**Methods:** `createToolbar`, `createSearchBar`, `createProjectBar`,
`createTagBar` (lines 96–168, ~90 lines)

`ChatMapViewBuilder` already exists and already does exactly this kind of
work one level down — it has `createChatListView`, `createDetailTextArea`,
`assembleRootPane`. These four methods are the natural continuation: they
build composite toolbars/bars out of the leaf widgets `ChatMapViewBuilder`
already knows how to build. This is the same pattern already proven, just
one layer up.

**Watch for:** `ChatMapApp` has its own private `button(String,
ThrowingRunnable)` (line 175) — different signature from
`ChatMapViewBuilder.button(String, Runnable)`, which already exists.
`ThrowingRunnable` presumably wraps `runWithFeedback`/`reportError` handling
that `Runnable` can't express. Reconcile these — either give
`ChatMapViewBuilder` a second overload, or make clear why they need to stay
separate — rather than ending up with two same-named-but-different `button`
helpers across two files.

**Suggested new home:** these can likely just move into `ChatMapViewBuilder`
directly, no new file needed.

---

## Group B — Background task orchestration

**Methods:** `runInBackground` (2 overloads), `runOnBackendLane` (2
overloads), `runWithFeedback`, `reportError` (lines 261–305, 498–510, ~65
lines)

`BackgroundActionRunner` already exists and already does the actual
lane-submission work — these six methods in `ChatMapApp` are thin wrappers
around it (passing `status`, buttons, and lambdas through). Check whether
`BackgroundActionRunner` can just take the `Button`/`Label status`
references directly (constructor or method params) and absorb these
wrappers entirely, rather than `ChatMapApp` holding its own pass-through
layer in front of it.

**Watch for:** `reportError` also calls `ChatMapDialogs.showError` — small,
but confirm the error-reporting path doesn't end up split across three
files in a way that's harder to follow than it is today.

---

## Group C — Business action handlers (the bulk — read the note before touching)

**Methods:** `importFile`, `exportSelectedChat`, `importChatGptArchive`,
`getLatestChat`, `showConversationInventory`/`showConversationInventoryDialog`,
`summarizeSelectedChat`, `searchChats`, `clearSearchAndFilters`,
`createProject`, `assignProject`, `clearProject`, `filterByProject`,
`createTag`, `addTag`, `removeTag`, `filterByTag`,
`refreshOrganizationChoices` (~18 methods, ~250 lines — well over a third of
the file)

**This is the one to think hardest about before extracting.** Each of these
is "a button was clicked, call the controller, run it on the right lane,
update the status label." That's arguably *what ChatMapApp's job actually
is* — it's the event-wiring layer connecting widgets to `ChatMapController`.
Pulling all of this into a new class doesn't shrink the god class, it just
renames it. If Groups A, B, and D come out cleanly and this is still 250+
lines of "click handler calls controller method," that may be a reasonable
place to stop rather than force a further split that doesn't actually
reduce complexity, just moves it.

If you do want to split this further, the one line that looks real: the
six **organization** handlers (`createProject`, `assignProject`,
`clearProject`, `filterByProject`, `createTag`, `addTag`, `removeTag`,
`filterByTag`, `refreshOrganizationChoices` — 9 methods, all about
project/tag management) are a distinct cluster from the **chat-action**
handlers (`importFile`, `exportSelectedChat`, `getLatestChat`,
`summarizeSelectedChat`, `searchChats`). Worth a judgment call on whether
that split earns its keep or is splitting for its own sake.

---

## Group D — Selection state / detail rendering

**Methods:** `handleSelectedResult`, `showChatDetails`, `renderChatDetail`,
`applyListState`, `selectChat`, `updateSelectionActionStates`,
`selectedChatId` (lines 414–497, ~85 lines)

`ChatDetailRenderer` already exists (27 lines) and handles formatting —
these methods are the surrounding coordination: syncing the `ListView`
selection, enabling/disabling action buttons based on what's selected,
calling into `ChatDetailRenderer` for the actual text. This is a real,
coherent cluster — "keep the chat-list selection and the detail pane and
the button-enabled-states all in sync with each other" is one job.

**Watch for:** this group is the most tightly coupled to JavaFX widget
fields (`chatList`, `detail`, `exportChatButton`, `summarizeButton`, etc.)
of anything in this file. If extracted, the new class needs those widget
references passed in — check whether that constructor ends up as long as
the problem it's solving before committing to this one. This is the
riskiest extraction of the four groups; do it last, and only if A–C already
went cleanly.

---

## What's left over after A–D

`start()`, `stop()`, the four small nested types (`ThrowingRunnable`,
`BackgroundCall`, `OrganizationChoices`, `ChatDetail`), `applyFontSize`,
`registerFontShortcuts`, field declarations. That's a reasonable footprint
for what should remain — app lifecycle plus wiring everything else
together. If Groups A, B, and D all land, this file should end up closer to
200–250 lines, which is a real reduction, not a relocation.

---

## Suggested order

1. Group A — lowest risk, extends an existing pattern.
2. Group B — mostly mechanical, existing target class already there.
3. Group D — real payoff, but check the coupling before committing.
4. Group C — think about whether it should move at all before doing it;
   revisit once A/B/D are done and see how much of the "god class" feeling
   is actually left.
