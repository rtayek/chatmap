# Handoff: dotmdfiles condensation

Repo: github.com/rtayek/dotmdfiles (work in a local clone; user reviews diffs before push).
User: Ray. Low vision — keep all output concise. LF-only line endings. Voice dictation user.

## Context

The `files/` folder holds Markdown templates that guide AI agents. Goal: minimum
necessary content. Guiding rule: **agents already know standard practice (SOLID,
patterns, TDD mechanics, layered architecture); files should state only Ray's
preferences and non-derivable rules.** Recent work already condensed design.md,
sdlc.md (now Philosophy/Tests/Build/Rules), and moved Tests+Build out of
coding-style.md.

## Tasks (in order)

### 1. Replace files/architecture.md with exactly:

```
> Purpose: Structural rules for any project.
> Scope: Layers and dependencies.

# Architecture

Layers, top to bottom: UI → Application → Core → Infrastructure.

- Dependencies point downward only: UI → Application → Core; Infrastructure → Core.
- Core knows nothing above it. No circular dependencies.
- Cross layers with simple, preferably immutable boundary data; don't leak
  infrastructure types upward.
- Put code in the lowest layer that can own it. No business logic in UI;
  no technical wiring in Core.
```

### 2. Condense files/persona.md to roughly half

Keep every rule, remove restatements. Keep the section structure
(Identity, Communication, Disagreement, Uncertainty, Code, Review, Tone) but
collapse each to its bullets. Keep: peer treatment, concise/lead-with-answer,
no filler phrases, disagree openly with reasons, never guess — say unsure and
ask, must-fix/should-fix/consider labels for reviews, collegial/honest/confident
tone. Target ~20 lines of content.

### 3. Trim files/coding-style.md by ~1KB

- Comments policy: keep "code is the documentation; comments MUST be minimal"
  plus the allowed-comment list; delete the explanatory prose around it.
- Dead code: collapse to 2 bullets (remove dead code when clearly safe;
  retaining it requires a brief documented reason).
- Do not touch: Naming table, Formatting, Class layout, Visibility,
  Spec violations, Agent rules, Utilities.

### 4. Delete files/claudes-ideas.md (executed working notes).

### 5. Small fixes

- files/sdlc.md: add header lines:
  `> Purpose: Defines the development process for agents.`
  `> Scope: Testing, build, and delivery rules.`
  and a Status section matching the other files (RFC-keyword sentence).
  Delete the now-redundant `### Tests as specification` sub-header.
- files/agents.md: change "read all `.md` files in the project and its
  sub-folders" to "read all `.md` files at the project root and in folders
  you work in." Delete the second paragraph of the Status section (the
  baseline/extension sentence) — it over-explains.
- Root file `claudes-desing-review-as-a-semantic-compression-system.md`:
  rename to fix the "desing" typo (keep content).

## Constraints

- LF line endings everywhere. No CRLF.
- Do not create new files beyond what is listed.
- Do not touch: human.md, java.md, c.md, project.md, accessibility.md,
  design.md, CLAUDE.md, README.md, prompts/, templates/.
- One commit per numbered task, clear messages. Do not push; Ray reviews first.

## Open questions (do NOT act; ask Ray if relevant)

- Fold java.md/c.md into coding-style.md, or keep separate?
- Move Visibility section into java.md (it is Java-specific)?
- Fate of the root .txt research files (agents.txt, sdlc.txt, sdlc2.txt)?
