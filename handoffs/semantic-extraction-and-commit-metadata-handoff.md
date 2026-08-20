# Handoff: Semantic Extraction + Structured Commit Metadata (capture of design turns)

> **STATUS: DESIGN CAPTURE, not a build task.** This records decisions and ideas
> from a design conversation so they aren't lost. Two related threads:
> (A) refinements to the semantic-extraction design, and (B) a NEW idea — putting
> structured, durable content into git commit messages so extraction can mine it
> later. Nothing here is code-ready; it's input for the eventual flagship build.
> Cross-refs: semantic-extraction-design-PRELIMINARY.md,
> knowledge-representation-PRELIMINARY.md.

---

## Part A — Semantic extraction: new framings to fold in

### A1. The complexity motivation (the sharp "why")
Raw chat context is expensive: transformer attention is O(n²) in sequence length,
AND each new turn reprocesses the growing history, so cumulative cost over a chat
grows ~quadratically in turns (L·k²/2). KV-caching softens the per-turn cost toward
linear, but memory and attention-over-history still grow, so long chats degrade and
eventually don't fit. Tool "compaction" (e.g. Claude Code summarizing old turns) is
the industry's mitigation of exactly this.

The point for ChatMap: a long transcript carries O(n²) reprocessing cost and O(n)
storage for what is really O(1) worth of durable knowledge (a few decisions,
constraints, patterns). **Extraction collapses that quadratic into near-constant
carry-forward cost** — you carry ~500 tokens of atomic knowledge nodes instead of
re-feeding 50K tokens of transcript. This is Principle 17 ("a pile of transcripts
is not progress") stated in complexity terms, and it's the technical justification
for the whole feature. Add this as the "why it earns its complexity" section.

### A2. Commit log as a THIRD extraction source
Extraction sources are now three, not two:
- **Chats** — verbose episodic; heavy compression (Mode B).
- **Memory files** (Claude Code, Codex) — already-semantic; reconcile vs code (Mode A).
- **Commit log** — semi-structured event record; grep-able, provenance-stamped,
  already terse.

Why commits are special for **reconcile (Mode A)**: a commit IS the diff between two
code states, so the commit log is literally the record of HOW the code moved from
the state a stale memory describes to the current state. It can tell reconcile *why*
a claim went stale, not just *that* it did (e.g. a "hexagonal restructure" commit
explains why a memory referencing `chatmap.cli` is now obsolete). Commits bridge
memory-claims and current code.

Caveats (commits are a NOISY source):
- Quality is bimodal. Real example from this project's own log: excellent
  ("Wrap SQLite migrations in an atomic transaction") next to empty
  ("added a .md file.", "from agy.", "fix compile errors.").
- Some messages LIE. A qwen/aider commit titled "Add shutdown method and override
  close in [4 classes]" actually added two `toString()`s and an empty method. So
  message text alone is untrustworthy.
- Therefore: mine **message + diff together**, and filter for semantic content — a
  "fix compile errors" commit is noise; skip it.
- A commit node is an *event* (temporal + provenance), and in the knowledge graph it
  `supports` or `replaces` other nodes rather than being a timeless fact.

---

## Part B — NEW IDEA: structured durable content in commit messages

The insight: instead of only mining semantics OUT of messy commits after the fact,
write durable content INTO commits at commit time — in a structured form extraction
can parse cheaply. This is Mode-B compression done by the author/agent at the moment
of maximum context (cheapest, most accurate time to compress).

### B1. Structured trailers (git already supports this convention)
Git trailers (like the `Co-authored-by:` aider already emits) can be extended with
a project vocabulary. A commit becomes:

```
<summary — what the change does>

<body — the why, prose>

Decision: <what was decided, if any>
Rejected: <approach tried and abandoned, if any>
Constraint: <invariant this establishes or depends on>
Affects: <package/class the durable claim is about>
```

These map DIRECTLY onto the knowledge-graph model (see
knowledge-representation-PRELIMINARY.md):
- `Decision:` → a decision node
- `Rejected:` → a rejected-approach node + a `replaces` edge
- `Constraint:` → a constraint node
- `Affects:` → an edge to a code/package node

So extraction doesn't have to INFER the semantics — the committer declared them,
grep-ably and machine-parseably.

### B2. Guardrails (critical — this fails if over-specified)
1. **Trailers are OPTIONAL and only when there is genuinely durable content.** A
   `fix typo` commit gets none. Forcing structure onto trivial commits produces fake
   semantic content (an agent will write `Decision: improved modularity` to satisfy
   the format — worthless). No ceremony on trivial commits.
2. **HARD RULE for everyone (the most important guideline): the commit message must
   describe what the diff ACTUALLY does, never intentions or aspirations. Message ⊆
   diff.** This directly targets the qwen failure mode (aspirational messages that
   don't match the change). Claim only what the change contains.

### B3. Two audiences — separate the guidance
- **For the AIs** — enforceable via `agents.md`/`CLAUDE.md`: when a commit embodies a
  decision/rejection/constraint, add the trailer; and always obey the message-⊆-diff
  rule. Agents are the main noise/lie source, so the strict version targets them.
- **For the human (Ray)** — a personal convention, NOT enforced, lower priority. The
  terse messages ("added a .md file.") are Ray's; they're under-informative because
  Ray holds the context in his head, but the only cost is felt later by extraction.
  Worth improving, but don't over-discipline; Ray is not the lie source.

### B4. Open decision for Ray (do NOT decide unilaterally)
Two flavors, pick per audience:
- **Light**: no formal trailers, just "write accurate messages that state the
  decision and why." Low friction; fits Ray's dislike of ceremony; still improves
  raw material.
- **Formal**: the `Decision:/Rejected:/Constraint:/Affects:` trailers. More powerful
  (direct node mapping) but higher friction; better suited to the agents than to Ray.
- Likely split: **hard accuracy rule for everyone; formal trailers optional, mainly
  for agents.**

---

## Suggested next artifacts (when this moves forward)

1. Add A1 (complexity motivation) and A2 (commit log as third source) into
   `semantic-extraction-design-PRELIMINARY.md`.
2. Write `commit-guidelines.md` (in dotmdfiles, referenced from `agents.md`) with:
   the message-⊆-diff hard rule, the optional trailer vocabulary, the no-ceremony
   escape valve, and a note that trailers are a future extraction source.
3. Leave the light-vs-formal choice (B4) to Ray before writing commit-guidelines.md.

## Constraints
- This is capture + design input. No code. No premature commit-format enforcement
  until Ray picks light vs formal.
- Keep everything consistent with the node+edge knowledge model and Principles 3,
  11, 13, 17.
