# Handoff: multi-lens code review findings

Surveyed 2026-09-11, read-only pass over the repository as it stood at that
date. Six specialist lenses, one after another, over the existing codebase.
Two items are real and actionable; the rest are lower-priority notes or
false leads worth recording so they are not re-investigated later.

---

## 1. README.md references files at stale paths

**Files:** `README.md`

`README.md` points to `first-principles.md`, `implementation-notes.md`, and
`handoffs/a2a-experiment-runbook.md` at the repository root. All three
actually live under `.llm/`:

- `.llm/first-principles.md`
- `.llm/implementation-notes.md`
- `.llm/handoffs/a2a-experiment-runbook.md`

`.llm/evo.md` confirms these were deliberately moved into `.llm/` at some
point. The README was never updated to match. Low effort fix: update the
three references in the "Project Documents" and "Bounded A2A Experiment"
sections.

## 2. Declared dependency versions do not match vendored jars in lib/

**Files:** `build.gradle.kts`, `lib/`

`build.gradle.kts` declares:

- `com.google.code.gson:gson:2.11.0`
- `org.junit.jupiter:junit-jupiter:5.11.0`

The jars actually present in `lib/` are:

- `gson-2.14.0.jar`
- `junit-jupiter-6.1.3.jar` (plus platform/engine jars at matching 6.x
  versions)

Worth reconciling which is authoritative. If `lib/` was populated by
`./gradlew eclipse` from a different resolution than the current
`build.gradle.kts`, the plain-Eclipse classpath and the Gradle-resolved
classpath may not agree during development.

## 3. Lower-priority notes (not acted on, recorded for reference)

- `WorkerLifecycleChain`, `WorkerLifecycleEvent`, `WorkerLifecycleState`,
  `WorkerAssignment`, and `WorkerSemanticHandoff` already exist in
  `domain/`. Anyone designing multi-worker coordination for this project
  should start by reading these rather than designing from scratch.
- 18 call sites of `System.out.println` / `printStackTrace` outside the
  presentation/cli layer were not individually located in this pass. Worth
  a targeted grep if logging consistency becomes a concern.
- A name-matching heuristic (`Foo.java` implies `FooTest.java`) flagged 136
  of 213 non-test classes with no matching test file. This overcounts
  (plain data/enum classes often need none) and should not be treated as a
  real coverage number — a coverage tool would be needed for that.
- No hardcoded secrets, API keys, or passwords found in `src/`.
- SQL access is consistently via `PreparedStatement` across the repository
  classes. The only string-concatenated SQL builds column/alias lists from
  a fixed internal template, not external input — low risk as it stands.
- Two hardcoded Windows Chrome install paths in
  `infrastructure/provider/web/ChromeCdpLauncher.java`. Matches the
  README's own statement that this checkout targets Windows; a portability
  note, not a defect.
- Zero `TODO` / `FIXME` / `HACK` markers found in `src/`.

## Scope note

This was a single read-only review pass, not a full audit. Item 3 entries
are observations, not verified defects.
