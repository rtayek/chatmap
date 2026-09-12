# Handoff: run the six-specialist review as real Claude Code subagents

Goal: stop designing the worker/skill filing system in the abstract and get
first real contact with Claude Code's native subagent mechanism, using a
task with a known-good answer to compare against.

## Baseline

A single Claude session already did this review sequentially, switching
lenses one at a time. See
`.llm/handoffs/2026-09-11-CM-multi-lens-review-findings.md` for the
findings it produced (README path drift, gson/junit version mismatch, plus
several lower-priority notes). That file is the answer to check the
subagent run against, not a task to redo from scratch.

## Task

Define six subagents, one per specialist lens used in the baseline:

- architecture
- security
- code quality
- test coverage
- dependency/build
- documentation

Each as a markdown file with `name`, `description`, and `tools` frontmatter
in `.claude/agents/` (project-scoped, so this stays specific to ChatMap
rather than leaking into every project). Read-only tools only for this
experiment -- no write access needed for a review pass, and it removes any
risk of the six running concurrently stepping on each other's file edits.

Run them against the ChatMap repository, same scope as the baseline
review, and let Claude Code fan them out concurrently rather than running
one at a time.

## What to watch for while running this

- Whether Claude picks the right subagent from the `description` field
  alone, or whether each one has to be named explicitly. This tells you
  how much the description wording actually matters before writing more
  role files.
- Whether running six subagents concurrently against the same working
  directory causes any friction, even though this pass is read-only.
- How much the subagent's system prompt needed to say versus what it could
  leave implicit. This directly informs how thin or thick real role files
  should be, rather than guessing.

## Acceptance criteria

- [ ] Six subagent definitions exist in `.claude/agents/`, one per lens
      above.
- [ ] All six were actually invoked for one review pass over ChatMap
      (confirm this wasn't silently handled by one subagent doing
      everything, or by Claude answering directly without delegating).
- [ ] Findings compared against the baseline handoff: same real issues
      caught (README paths, dependency versions), no read/write conflicts
      encountered, and any new or missed findings noted.
- [ ] A short note on the three watch-for questions above, even if the
      answer is "no meaningful friction" -- record it either way.

## Explicitly out of scope for this pass

- No new roles/skills directory design.
- No changes to the naming or handoff-routing conventions already agreed.
- No write-access subagents yet -- that is a separate, later experiment
  once read-only behavior is understood.

## Scope note

This is a mechanism experiment, not a code-quality task. The point is to
learn how Claude Code's subagents actually behave, using a review whose
correct answer is already known, before committing further design effort
to the skills/roles filing system built around them.
