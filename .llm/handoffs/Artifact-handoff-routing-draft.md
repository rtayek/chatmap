# Artifact and Handoff Routing - Rough Draft

Status: draft, not settled. Written to capture where the discussion landed so
it can be reworked against a single document instead of scrollback.

## Scope

Covers anything an LLM produces that should end up saved as a file in one of
the project repos: scripts, log bundles, tree listings, notes, and
cross-project handoffs. All of these are "artifacts." A handoff is one kind
of artifact - specifically one that carries context from one project to
another.

## Terminology

- Artifact: any file an agent produces that is worth keeping. General case.
- Handoff: an artifact whose purpose is moving context across a project
  boundary. A subtype of artifact, not a separate thing.

This matches the existing AGENTS.md distinction: "Actual handoffs may be
placed in an existing project handoffs/ directory. Other artifacts should not
be placed in handoffs/ merely because no better location exists."

## Naming Convention

Cross-project handoff (has a from and a to):

    YYYY-MM-DD-<from>-to-<to>-<description>.md

Example: 2026-09-10-SYS-to-CM-memory-ownership-handoff.md

Single-project artifact (belongs to one project, no cross-project move):

    YYYY-MM-DD-<project>-<description>.md

Example: 2026-09-10-CM-dependency-tree.txt

Project codes follow the same short prefixes already used in index.md IDs
(CM-IDX-01, DMF-IDX-01, and so on):

- SYS - the System repo
- CM  - ChatMap
- DMF - dotmdfiles
- DF  - dotfiles

No YAML front matter required for routing. The filename carries everything
the router needs. YAML remains a separate concern for describing a document
once it is in place (lifecycle, status, provenance), not for getting it
there.

## Routing Rule

The watcher looks at the field immediately before the description:

- If "-to-<code>-" appears in the filename, route on <code>.
- Otherwise, route on the single project code present.
- If no known code is found, do not guess. Leave the file in place and flag
  it for manual review.

## Where Artifacts Land

- Handoffs go to the destination project's .llm/handoffs/.
- Other artifacts go wherever their type suggests inside the destination
  project (to be worked out per project; not every artifact belongs in
  handoffs/).

## Inbox and Watcher

Single staging point: one GitHub repo acts as the universal inbox. Anything
can be committed there regardless of source - phone dictation into an LLM,
a paste from a chat, a download, an agentic LLM with repo access, another
assistant. Nothing needs to know in advance where it is ultimately headed.

The existing Java handoff watcher can watch the inbox repo the same way it
would watch a local folder (via a local clone, kept pulled). It reads each
new file, applies the routing rule above, and moves or commits the file into
the correct destination project.

Two things this buys, both raised in discussion and worth keeping:

- Catch-all for non-agentic sources. Not everything can commit directly to
  a project repo (dictation, plain chat paste, clipboard-manager output).
  The inbox is the one place all of those can land.
- Free audit trail. Because the inbox is git-backed, every artifact that
  ever moved through the system has a timestamped, immutable record in one
  place, independent of the destination project's own git history.

## Considered and Dropped

Direct commit by the LLM into the destination project, skipping the inbox
entirely. Dropped because:

- It only works when the LLM in question has write access to the right
  repo and gets the routing decision right with nothing checking it.
- It loses the single cross-project audit trail (each project's git log
  only shows its own piece).
- It has no fallback for sources that cannot commit directly.

The inbox-plus-watcher stays as the general mechanism. Direct commit may
still happen opportunistically when an agentic LLM already has repo access,
but it is not relied on as the only path.

## Open Questions

- Full list of project codes, confirmed (SYS, CM, DMF, DF assumed above).
- Whether non-handoff artifacts need their own subfolder convention
  (artifacts/ alongside handoffs/) or get sorted per project as they come.
- Whether a provenance breadcrumb should be left in the source project when
  a handoff crosses boundaries, or whether the filename plus inbox history
  is enough.
- Whether existing files that predate this convention (for example
  chatmap-project-memory-ownership-handoff-2026-09-09.md, which is not in
  date-first order) get renamed, or are left as-is with the new pattern
  enforced only going forward.
- Whether the watcher polls the inbox repo on a timer or reacts to
  something more immediate.
