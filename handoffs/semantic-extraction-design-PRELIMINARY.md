# SemanticExtractionService — Preliminary Design Note

> **STATUS: PRELIMINARY / DRAFT.** Not settled. Captures the direction that
> emerged from surveying Claude Code's auto-memory files, plus two independent
> analyses (manual + local-LLM survey) that agreed on the same core insight.
> Expect this to change once the design conversation actually happens.

## Why this exists (the one-line thesis)

Per first-principles Principle 17, ChatMap's point is *content mining*, not
storage. A pile of well-organized transcripts is not progress. SemanticExtraction
is the first real **capability**: turn transient episodic material into durable,
non-contradictory project knowledge.

## The key realization: there are TWO extraction modes, not one

The survey of real auto-memory files made this concrete. These are different
operations and should probably be built and reasoned about separately.

### Mode A — Reconcile (recommended FIRST)
Input: an existing semantic doc (a memory/findings/decision file) + the current
codebase.
Output: the same doc, with each concrete claim classified: **still-valid /
obsolete / needs-human-check**, with file:line evidence.

Why first:
- Smaller than compression — no heavy summarization required.
- Directly attacks the **staleness problem** the whole field admits is unsolved
  (see agent-memory literature: contradictions accumulate, memories go
  confidently-wrong when facts change).
- Demonstrably feasible — already done by hand on `standing-review-findings.md`.
- We have a perfect test corpus: real, drifted memory files.

The algorithm (straight from the survey's own conclusion):
> Diff a memory's concrete claims — package names, file paths, commit hashes —
> against current repo state before trusting its "how to apply" guidance.

### Mode B — Compress (build SECOND)
Input: a raw chat / session transcript.
Output: a distilled durable note (the extract-chat-template shape: Stable
Decisions, Constraints, Active Model, Reusable Patterns, Open Questions,
Rejected/Deferred).

Why second: it's the better-understood, lower-differentiation half. Summarization
is a solved-ish LLM task; the value ChatMap adds is smaller here than in reconcile.

## Worked example / first test case (proven by hand)

`standing-review-findings.md` (Claude Code memory) lists 3 "remaining" findings.
Reconciled against current code:
- **`Chat` dual hash accessor** (`contentHash()` and `transcriptHash()` both return
  `importMetadata.transcriptHash()`) — **STILL VALID** (verified: Chat.java:44,104).
- Two sibling findings reference `chatmap.cli` / `chatmap.backend.web` packages that
  **no longer exist** (hexagonal refactor renamed them) — **OBSOLETE**.

This is exactly Mode A output, and it's obviously useful. It's the demo.

## Input taxonomy (extraction must not treat all .md alike)

From the survey — real memory files fall into distinct types with different value:
- **decision-log** (e.g. concurrency-design-settled) — high value, single-topic,
  Why + How-to-apply. Best ingestion shape. Needs staleness check.
- **project-vision** (e.g. switchboard-pivot) — high value, has supersession
  relationships worth preserving.
- **findings / running-log** (e.g. standing-review-findings) — hybrid; append-only
  scratch logs, NOT settled memory. Highest staleness risk.
- **preference** (e.g. prefers-bourne-shell) — reconcile against stated prefs, not
  code. Different axis.
- **workflow** — durable cross-project habits; age well (not code-coupled).
- **index** (bare MEMORY.md) — noise as content; keep only as a map.
- **superseded/self-flagged-stale** — low risk BECAUSE self-declared; handle as
  history, not truth.

## Design principles (inherited, not invented)

- **Propose, don't auto-file** (Principle 10 — humans decide). Output is a proposed
  `.md` / a triage report, never an automatic write into canonical docs.
- **Preserve original sources** (Principle 12). The chat/memory stays; extraction
  is additive.
- **LLM does the semantic step** (Principle 9); deterministic code does the
  plumbing (read, diff file:line, assemble prompt, write report).
- **Run as a fresh LLM call over stored input**, not inline — mirrors how Claude
  Code spawns a clean instance for compaction (full context budget, no pollution).
- **Silent staleness is the enemy.** Self-flagged-stale is safe; confidently-wrong
  is dangerous. Reconcile must surface contradictions, even if it can't resolve them.

## Open questions (for the real design conversation — NOT decided)

1. Reconcile output: annotate the file in place, or emit a separate triage report?
2. What counts as a "concrete claim" the differ can check? (package names, paths,
   commit hashes are easy; prose assertions are hard.)
3. Does it run per-file, per-project, or per-chat?
4. On-demand only, or eventual idle/AutoDream-style consolidation?
5. Does ChatMap ingest Claude Code memory files at all, or only its own chats?
   (The .claude/projects corpus is a tempting second source but carries the temp-
   worktree litter problem.)
6. Contradiction handling when a new chat reverses an old decision — surface only,
   or attempt merge? (Field considers this unsolved; scope conservatively.)

## Explicitly NOT deciding here

- The prompt/template wording (exists in draft elsewhere; not settled).
- Whether this and Facets share machinery (they don't obviously).
- Storage/schema for extracted artifacts.

## Recommended build order

1. Mode A (reconcile) minimal: one findings file + repo → triage report. Grade
   against the known dual-hash answer.
2. Mode A generalized: across the memory-file corpus.
3. Mode B (compress): chat → template, once reconcile machinery is proven.
4. (Much later, maybe) idle consolidation.

Own long-lived branch. Design-first (diagrams) before code, same as model/channel.
