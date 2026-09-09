# ChatMap Menu Bar Restructuring — Implementation Handoff

## Recipient

Give this handoff to **Claude or Codex working in `rtayek/chatmap`** —
self-contained UI work in one repo, no architectural risk needing
Antigravity.

## Purpose

The main window currently stacks six things vertically before you
even reach the chat browser: Toolbar, Search bar, Project bar,
Related-project bar, Tag bar, and the Prompt pane. All of them are
permanently visible regardless of what the user is actually doing.
The most recent compaction commit (`e91eaf8`, "Compact ChatMap UI
layout") tried to solve this by shrinking padding, gaps, font-size
range, and text-area row counts — but that treats the symptom (not
enough room) with the wrong lever. The actual problem is too many
things competing for attention at once, not that any individual
control is too big.

Replace the occasional-use bars with a standard menu bar
(File/View/Tools/Help) so they take near-zero space when not in use,
and restore the readable defaults the previous compaction commit
regressed.

## Part 1: Menu Bar

Add a `MenuBar` (`javafx.scene.control.MenuBar`) at the very top of
the window, above everything else. Move the following out of
always-visible bars and into menus:

- **File**
  - Import Text
  - Import Markdown
  - Import ChatGPT JSON
  - Export Chat
  - Get Latest Chat
  - (Exit, if there isn't already a standard way to close the app)
- **View**
  - Font Size submenu — the existing `FontSizeState.SIZES` choices as
    radio-style menu items (`RadioMenuItem` in a `ToggleGroup`), or
    keep the existing `ComboBox` if that's simpler to wire up; either
    is fine, just don't build two separate font-size controls.
  - `CheckMenuItem` toggles for **Search bar**, **Project bar**,
    **Related-project bar**, **Tag bar** — checked means visible.
    This directly solves "too many bars visible at once" using the
    same menu mechanism, rather than introducing a second UI pattern
    (e.g. Accordion/TitledPane) alongside it. Each bar's visibility
    should default to checked (visible) so existing behavior isn't
    surprising on first launch after this change; the user can then
    hide what they don't need.
- **Tools**
  - Inventory
  - Summarize
- **Help**
  - Leave empty or a placeholder for now (About, docs link) — don't
    force content that doesn't exist yet.

Do not force an **Edit** menu into existence if there's nothing real
to put in it. A standard menu bar with fewer menus is better than one
padded out to match a convention with no actual content behind it.

### Wiring notes

- `ChatMapViewBuilder.createToolbar(...)` currently returns a
  `ToolbarWidgets` record wrapping a `FlowPane` of buttons. Replace
  its content with menu construction, but keep returning whatever
  live references the caller (`ChatMapApp`) needs (e.g. the font-size
  control, if it's exposed as a menu, still needs to be settable
  programmatically when `applyFontSize` runs).
- For the bar-visibility toggles: `Search bar`, `Project bar`,
  `Related-project bar`, `Tag bar` need their `visible` and
  `managed` JavaFX properties bound together (setting only `visible`
  leaves an empty gap where the node used to be — `managed` must also
  be false for the layout to collapse properly). Bind both to the
  corresponding `CheckMenuItem.selectedProperty()`.
- `assembleRootPane(...)` currently builds the top section as a fixed
  `VBox(toolbar, searchBar, projectBar, relatedProjectBar, tagBar,
  promptPane)`. The menu bar goes above this `VBox`, not inside it —
  update the method to accept the `MenuBar` as an additional
  parameter, placed via `BorderPane` above the existing top `VBox`
  (or combine both into a single top `VBox` with the menu bar as its
  first child — either is fine, just confirm existing margin/spacing
  calls elsewhere in the method don't silently apply to the menu bar
  in a way that looks wrong).

## Part 2: Restore Readable Defaults

The `e91eaf8` compaction commit shrank several things in a way that
works against this application's actual accessibility purpose (low
vision — this is why a font-size picker exists at all). Revert these
specifically, in `FontSizeState.java` and `ChatMapViewBuilder.java`:

- **Font size range**: restore `SIZES = List.of(14, 16, 18, 20, 24,
  28)` and `DEFAULT_SIZE = 16` (was changed to `List.of(8, 10, 12,
  14, 16, 18)` / `14`). The smaller sizes (8/10/12) don't serve this
  application's users; the larger ones (20/24/28) that got removed
  do.
- **Text area row counts**: `historyArea`, `promptArea`, and
  `responseArea` were all cut to `setPrefRowCount(2)`. Restore
  something closer to the pre-compaction values (`historyArea` 6,
  `promptArea` 4, `responseArea` 8) or a reasonable compromise — the
  point is a response shouldn't require scrolling to read a couple of
  sentences. Since the menu bar work above frees significant vertical
  space (six stacked bars collapsing to one thin menu bar plus
  whichever the user leaves checked), there should be room to restore
  these without the layout feeling cramped again.
- **Field widths**: `resumeChatChoice` (currently 90px, was 220px)
  and `promptConversationField` (currently 70px, was 180px) are too
  narrow to show a real chat title or conversation name — restore
  toward the original widths, or pick new values wide enough that a
  typical chat title isn't immediately truncated. Use judgment here
  rather than restoring the exact old numbers if the new layout
  genuinely has different space constraints.
- **Padding and gaps**: `COMPACT_BUTTON_STYLE` (`"0 2 0 2"`, was `"3
  7 3 7"`) and `CONTROL_GAP`/`SECTION_GAP` (both `1`, was `8`/`4`)
  are tight enough to reduce click-target size and remove visual
  separation between controls. Loosen these back toward the
  pre-compaction values — exact numbers aren't critical, but don't
  leave them at near-zero.

Keep everything from `e91eaf8` that was a genuine improvement and
isn't part of the above list:
- `stage.setMaximized(true)` — keep, this is the actual fullscreen
  fix that was asked for.
- The vertical `SplitPane` restructuring (prompt pane above chat
  browser, user-resizable divider) — keep, this is a real structural
  improvement independent of the shrinking issue.
- The per-widget explicit font-size application in `applyFontSize`
  (setting style on each individual node, not just the root) — keep,
  this is a legitimate JavaFX cascade-inheritance fix, unrelated to
  the sizing regression.
- `FlowPane` for the toolbar (wraps instead of cutting off) — largely
  moot once toolbar buttons move into the File/Tools menus, but no
  harm keeping the pattern if anything remains outside the menu bar.

## Tests Required

- `FontSizeStateTest` — update expected values back to the restored
  `SIZES`/`DEFAULT_SIZE` (this test was already touched by `e91eaf8`
  to match the shrunk values; it needs updating again to match the
  restored ones).
- Menu bar toggles: checking/unchecking a `CheckMenuItem` for a given
  bar actually changes that bar's visibility in the scene graph
  (test via `isVisible()`/`isManaged()`, not just that the
  `CheckMenuItem`'s own state flipped).
- Existing UI tests that reference toolbar buttons directly (if any)
  need updating to find them via the new menu structure instead.

## Non-Goals For This Pass

- No pop-out-to-separate-window / detachable panel behavior. That
  was discussed as a possible future step but isn't required here —
  the menu bar alone should resolve the "too busy" problem. Revisit
  only if it turns out the menu bar isn't enough once it's actually
  used.
- No changes to the Prompt pane's internal three-column layout
  (History/Prompt/Response side by side) — that structure was
  confirmed to make sense as-is; only its row heights are part of
  this handoff (see Part 2).
- No changes to chat list / chat detail behavior — unaffected by
  this work.

## Completion Criteria

Launching the app shows a thin menu bar (File/View/Tools/Help) at the
top, no permanently-visible Search/Project/Related-Project/Tag bars
unless the user has checked them on in the View menu, and the
History/Prompt/Response areas and font-size range read comfortably at
the restored defaults. `./gradlew test` passes.

Run:

```bash
./gradlew test
```
