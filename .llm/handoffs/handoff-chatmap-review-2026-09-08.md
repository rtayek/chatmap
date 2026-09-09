# Handoff: ChatMap Overview - Review and Continue

Date: 2026-09-08
Project: chatmap
Purpose: retire this "ChatMap overview" chat after a review; resume in a
fresh chat. Repo verified at origin/master commit e04f594.

## Headline: the A2A recorder got built, and it's live-tested against a real model

The last chat ended with a clear recommendation from the A2A experiment
findings: build a bounded recorder that turns externally-visible A2A
exchanges into ChatMap ledger entries, without ChatMap becoming the
orchestrator. That happened, and it went further than the minimum:

- "Add bounded A2A lifecycle recorder" - the recorder itself.
- "Record live A2A continuation in ChatMap ledger" and "Record verified A2A
  ledger continuation" - proved the recorder captures a real
  INPUT_REQUIRED -> follow-up -> COMPLETED continuation, not just a
  synthetic one.
- "Add bounded Ollama-backed A2A worker" + "Record real model-backed A2A
  tasks" + "Record verified model-backed A2A persistence" - the experiment
  moved from FakeWorker to an actual Ollama-backed worker. This is a real
  milestone: A2A task lifecycle, continuation, and now the recorder have all
  been proven against a live model, not just deterministic test fixtures.
- "Add deterministic A2A semantic probe" + "Record successful structured
  semantic probe" - some kind of check that semantic content survives the
  A2A round-trip, not just task state. Worth reading the probe itself next
  session if semantic-preservation guarantees matter for the recorder's
  design.
- "Enforce A2A experiment boundary" - a guard was added (worth checking
  what kind - test, lint rule, or doc) to keep the A2A package from leaking
  into the rest of ChatMap, consistent with the original "bounded
  experiment" framing.
- "Add read-only worker lifecycle record CLI" - a way to inspect
  WorkerLifecycleRecord data from the command line. Read-only is a good
  sign - observation tool, not another control surface.
- Document: `.llm/handoffs/2026-09-07-a2a-experiment-continuation-handoff.md`
  is the detailed writeup of this work and is worth reading in full next
  session; I have not yet reviewed it in depth.

Net effect: the "should ChatMap's worker adapters speak A2A while ChatMap's
own ledger stays the layer above it" question from two sessions ago now has
a working answer, proven against a real model, not just a design doc.

## Real bugfix landed: migration savepoint

`fix/migration-caller-savepoint`, merged. Precise, well-scoped fix: when
`Database.applyMigrations(Connection)` runs inside a caller-owned
transaction with auto-commit already disabled, it now wraps its own
migration work in a JDBC savepoint, so a mid-migration failure rolls back
only the migration's own statements, not the caller's entire surrounding
transaction. The handoff for this explicitly says do not touch the old
`feature/migrations-transactional` branch or its archive tag - this was a
clean reimplementation on a fresh branch from master, not a
cherry-pick/rebase. Existing owned-transaction behavior (auto-commit
enabled case) was preserved exactly; only the caller-owned-transaction case
changed.

## Repo reorganization: everything moved under .llm/

`design.md`, `evo.md`, `first-principles.md`, `implementation-notes.md`,
`working-context.md`, and the entire `handoffs/` directory now live under
`.llm/` instead of repo root. If you or an agent goes looking for these
files at their old root-level paths, they've moved - check `.llm/` first.

## The handoff-file rule is working - maybe too well

The "every response becomes a downloadable .md" rule from two sessions ago
is clearly in effect: `.llm/handoffs/` now contains roughly 20+ files named
`handoff-chatmap-2026-09-0X-HHMM.md` or `handoff-misc-...md`, many
timestamped just minutes apart on 2026-09-07. This is the expected and
accepted cost of "no exceptions, no judgment calls" - but working-context.md
itself now has an open item that names the problem directly:

> **Handoff Pruning:** Define the criteria for when a transient handoff is
> permanent enough to be squashed into design.md versus deleted.

That's a real, live open question, not yet resolved. Worth deciding early
next session, since the pile only grows the longer it's deferred.

## working-context.md itself just got sharply trimmed

The latest commit ("Update current ChatMap work") cut `working-context.md`
from 123 lines to ~15, and `first-principles.md` from ~450 lines to a
fraction of that. Current working-context.md content in full:

---
Current Milestone: Manual Protocol Validation [2026-09-08]
"We are executing a localized experiment to determine if a small collection
of durable knowledge, operational rules, and working state can provide
reliable agent context without ingesting the conversational archive."

Immediate Action Items:
- Craft one manual handoff file inside .llm/handoffs/ tracking a closed
  sub-chat session.
- Move first-principles.md and evo.md into the local .llm/ directory
  (appears already done as of this commit).
- Verify that an open agent session cleanly processes the decoupled
  CLAUDE.md and AGENTS.md root files.

Open Statuses:
- Handoff Pruning (see above).
- Visualization: observe if the Obsidian local graph view accurately
  connects the updated front matter keys.
---

This is worth being aware of on its own: the project is now testing exactly
the meta-question this whole multi-session conversation has circled -
whether a small, curated set of durable files (not full conversation
history) is enough to give an agent reliable context. That's a genuine
experiment worth watching the outcome of, separate from any single
feature. There's also a new Obsidian angle (graph view over front-matter
keys) that hasn't come up in any prior session - worth asking about if it's
new tooling or just a spot-check.

## Two threads from the previous handoff, status unknown

Not confirmed either way this session - worth checking first thing next time:

1. **The escalation-chain / bubble-up idea** (WAITING_FOR_DECISION climbing
   through caller identity, Optional-style) - was it ever written into its
   own document, or does it still only exist in prior chat history? The A2A
   findings doc's AUTH_REQUIRED delegation-chain language is the closest
   protocol-level echo of this idea, but I have not confirmed whether
   ChatMap's own WorkerLifecycleRecord schema was ever updated to capture
   "who called me" for an in-progress escalation.
2. **The two small tools** (capture/download-button page; clipboard-relay
   plain-text stripper) - no evidence either was built. Still open.

## Suggested first move in the new chat

Given the volume of loose handoff files and the open "Handoff Pruning"
question, that's probably the highest-leverage small thing to resolve
first - it's blocking nothing technically, but it's the one open item
explicitly named in working-context.md itself, and it will only get more
tedious the longer the pile grows. After that: read
2026-09-07-a2a-experiment-continuation-handoff.md in full, since it is the
primary source for everything summarized above and this handoff was
written from commit messages and the findings doc, not that document
directly.
