# Handoff: Run the Combined OpenWorker + Failing-Worker Experiment

From: chatmap (planning session, claude.ai)
To: Claude Code (real repo, real Windows workstation)
Date: 2026-09-14
Supplements: .llm/handoffs/openworker-bounded-evaluation-handoff-2026-09-14.md
-- all safety rules, phases, and acceptance criteria in that handoff still
apply in full. This does not replace it.

## Context

You already produced a recommendation: run one bounded OpenWorker
experiment combining the still-pending "parallel exercise with a
deliberately failing worker" item (Active Agenda item 1) with the
OpenWorker evaluation itself, rather than doing them separately. Ray
approved this combined approach. This handoff authorizes proceeding.

## Step 1: Phase 2 disclosure, before anything else

Per the original handoff's Phase 2, step 2 -- this has not yet happened
and must happen first. Before downloading, installing, or running any
OpenWorker binary, show Ray:

- the exact source and version/commit being installed;
- whether the binary is signed;
- the intended install destination;
- the permissions and network access it may request.

Wait for Ray's explicit approval on this before proceeding to install or
run anything.

## Step 2: Run the combined experiment

Once Step 1 is approved, run:

- One lead / coordinator.
- Two workers on independent, bounded, read-only tasks (per the original
  handoff's Phase 3 task shape -- read-only inspection of a disposable
  pinned ChatMap checkout, produce a Markdown artifact, cite file paths,
  distinguish observed fact from inference).
- One of the two workers deliberately fails or is blocked (per the
  original handoff's Phase 4 -- either a nonexistent-file task with an
  explicit no-fabrication instruction, or a harmless approval request that
  Ray denies).
- Local Ollama only. No email, Slack, calendar, GitHub, or other cloud
  connections. A limited API key is acceptable only if Ollama genuinely
  cannot do the task -- prefer Ollama.
- Disposable Git worktree, not Ray's active checkout.

## Step 3: Check what OpenWorker's own continuity mechanism actually preserves

This is the real test behind Phase 5 of the original handoff. Explicitly
verify and document:

- Does OpenWorker's journal preserve the failed/blocked worker's reason
  and any partial work?
- Does it preserve the successful sibling worker's output?
- Does it preserve the lead's decisions and the final synthesis?
- Does all of the above survive OpenWorker's application close and reopen?

## Step 4: Document what "journal" and "team board" concretely are

Both terms came up in your own recommendation but are not yet defined in
concrete, checkable terms. Answer directly:

- Is the journal a real file on disk, in some format? Or only visible
  through OpenWorker's own UI, with no export?
- Same question for the team board.
- If either is a real, stable, on-disk artifact: could ChatMap read it
  directly through its existing import/provider abstractions without
  scraping private storage or reverse-engineering an undocumented format?
- If either is UI-only: say so plainly. That is a real finding, not a
  gap in your work.

This directly answers the original handoff's Phase 5 question 4: is there
a stable integration seam, or would recording require brittle scraping.

## Constraints

- Every safety rule in the original handoff still applies in full:
  disposable checkout pinned to a recorded commit, no `.git` credentials
  or secret stores exposed, no unattended auto-approval, no ChatMap schema
  changes, no OpenWorker source changes, no system-wide installs without
  Ray's explicit approval.
- Do not skip Step 1. Do not proceed to Step 2 without Ray's explicit
  approval of the disclosed install details.
- Produce the deliverable exactly as specified in the original handoff:
  `openworker-bounded-evaluation-report-2026-09-14.md`, with the required
  sections and a PROMISING / INCONCLUSIVE / NOT A FIT verdict. Steps 3 and
  4 above should be folded into that same report, not a separate document.
- Default to no ChatMap code changes. Do not commit or push the report
  without Ray's explicit authorization after he reviews it, per the
  original handoff's Repository Changes section.
