---
project: chatmap
agent: claude
branch: feature-code-health-cleanup
---

# Task: Four independent code-health cleanups (JSON dedup, exception narrowing, registry contract, CommandResult leak)

These are four SEPARATE improvements. Do them as four separate commits on this
branch so each can be reviewed and reverted independently. None depends on
another. Ordered easiest-first. Run `./gradlew check` after each.

IMPORTANT — do NOT delete `src/junk/Facets.java`. It looks like dead scratch code
but it is being kept intentionally for upcoming work. Leave it untouched. Do not
"clean it up."

---

## Task 1 — Hoist duplicated JSON helpers into the shared base adapter

`ChatGptWebAdapter` and `ClaudeWebAdapter` each declare byte-for-byte identical
private static helpers:
- `parseOrNull(Object raw)` -> JsonObject
- `stringField(JsonObject, String)` -> String

They both extend `CdpTranscriptAdapter` (same package,
`chatmap.infrastructure.provider.web`), which already imports gson's JsonObject.
`GeminiWebAdapter` may have a near-identical `booleanField`/`stringField` too —
check and fold in any that are genuinely identical.

Do:
- Move the identical helpers UP into `CdpTranscriptAdapter` as `protected static`
  (or package-private static) methods.
- Delete the per-adapter copies; the subclasses inherit them.
- Only hoist helpers that are IDENTICAL across adapters. If a helper differs even
  slightly between adapters, leave it in place — don't force-unify divergent logic.
- No behavior change; this is pure dedup.

Tests: existing web-adapter tests must still pass (they exercise these helpers
indirectly). No new test needed unless coverage is currently absent for a hoisted
helper.

---

## Task 2 — Narrow the broad catch(Exception) blocks in the web/CDP package

The web/provider package has the codebase's concentration of
`catch (Exception)` / `catch (RuntimeException)` (CdpTranscriptAdapter 9,
GeminiWebAdapter 7, ChatGptWebAdapter 5, ClaudeWebAdapter 4, CdpPage 3, plus a few
singles). They already LOG rather than swallow (good), but catching the broad base
type means a genuine bug (NPE, IllegalState) is caught and reclassified as
"provider/CDP failure," hiding it.

Do, file by file (each file can be its own sub-commit if you prefer):
- For each broad catch, identify what the guarded block actually throws — typically
  some subset of: gson `JsonParseException`, the CDP/websocket IO or timeout type,
  `InterruptedException`, `java.io.IOException`. Narrow the catch to those.
- Let unexpected `RuntimeException`s propagate rather than be reclassified. If a
  block genuinely needs a catch-all for robustness (e.g. a top-level per-item loop
  that must not abort the whole scrape on one bad item), that's legitimate — KEEP
  the broad catch there but add a brief comment saying why it's intentional, and
  make sure it logs at WARN with the exception.
- Do NOT change control flow or what gets logged; only tighten the caught type.
- This is judgment work, not mechanical — if narrowing a given catch isn't clearly
  correct, leave it and note it. Partial completion is fine; better to narrow the
  clear ones than to guess.

Tests: existing tests must pass. Where you narrow a catch, if there's an easy way
to assert the intended exception type now propagates (a malformed-input test), add
it — but don't contort tests to force it.

---

## Task 3 — Replace the raw project registry map with a named contract type

`HandoffOrchestratorService` takes and stores a raw
`Map<String, Path> projectRegistry` (constructor param + field + `.get(projectKey)`
lookup). A bare generic collection as a cross-layer contract is a smell: the map's
meaning (project key -> local repo path) is implicit, and any caller can pass an
arbitrary map.

Do:
- Introduce a small value type, e.g. `ProjectRegistry` (in
  `chatmap.application.port` or alongside the orchestrator in
  `application.service` — match where similar contracts live), wrapping the
  `Map<String, Path>` with:
  - a validating constructor (non-null, defensive copy),
  - a lookup method returning `Optional<Path> pathFor(String projectKey)`.
- Change `HandoffOrchestratorService` to take and use `ProjectRegistry` instead of
  the raw map. The `.get(projectKey)` becomes `registry.pathFor(projectKey)`,
  which pairs nicely with the existing "no configured target project path" failure
  branch (now driven by `Optional.isEmpty()`).
- Update the composition root (`HandoffOrchestratorBootstrap`) to build a
  `ProjectRegistry` from its properties source.
- Keep behavior identical: same keys, same paths, same failure message.

Tests: update the orchestrator tests that construct it with a raw map to use
`ProjectRegistry`. Add a small test for the new type (null rejection, defensive
copy, pathFor present/absent).

---

## Task 4 — Stop leaking CommandResult through the orchestrator's application surface

`CommandResult` (exitCode / stdout / stderr / timedOut — a process-execution DTO)
threads through `HandoffOrchestratorService` (~16 refs) and `GitWorkspaceManager`
(~14 refs), which are application-layer classes. It's an infrastructure-shaped
type surfacing in the application layer.

This is the largest/most invasive of the four and the one most likely to be
awkward — approach it conservatively:

- FIRST assess: is `CommandResult` actually in an `application.port` package, or is
  it acceptable where it is? (`GitWorkspaceManager` is arguably an infra-flavored
  helper even though it sits under application.service.) If, on reading, the leak
  is mostly WITHIN git-command plumbing that never reaches a true port boundary,
  the right fix may be small or even "document and leave." State your assessment.
- If a real fix is warranted: introduce a minimal application-facing result type
  (e.g. `GitOutcome` with just what the orchestrator actually needs — success
  boolean, and the stderr/stdout strings it uses for messages) and have
  `GitWorkspaceManager` return THAT, keeping `CommandResult` behind the command
  execution boundary. Do NOT expand scope beyond what the orchestrator consumes.
- If the change balloons beyond ~a focused commit, STOP, implement only the
  cleanest slice, and note the rest as a follow-up in the commit message. Do not
  do a sweeping rewrite of the git plumbing for this.

Tests: whatever you change must keep the orchestrator/GitWorkspaceManager tests
green. This task explicitly permits partial completion with a documented remainder.

---

## Constraints (all tasks)

- Four separate commits, easiest-first, `./gradlew check` green after each.
- NO behavior changes anywhere — these are all structure/dedup/typing cleanups.
- Do NOT touch `src/junk/Facets.java` (kept intentionally).
- Do NOT touch the model/channel code, the migration code, or StructuredCliOutput
  (separate in-flight work).
- If any task turns out riskier or larger than it looks, do the safe part, commit
  it, and note the remainder rather than forcing a big change. Partial is fine.

## Validation

`./gradlew check` passes after each commit. Each task is independently reviewable.
