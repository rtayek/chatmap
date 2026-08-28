# Handoff: doc drift audit + commit/process discipline — 2026-08-20

## What this session was

Not feature work. A read-through of the accumulated design/handoff corpus
across `chatmap` and `dotmdfiles`, looking for drift, duplication, and
process gaps — triggered by needing to reconstruct where semantic-extraction
design actually stood before doing more of it.

## Findings

### Doc drift: three incompatible answers on canonical file set

- ChatMap's `semantic-extraction-design-PRELIMINARY.md`: file set left
  generic/unsettled.
- Live `dotmdfiles/templates/extract-chat-template.md`: four fixed files
  (`architecture.md`, `design.md`, `patterns.md`, `working-context.md`).
- `dotmdfiles/semantic/design-review.md`: five files + `sources/`
  (`project.md`, `architecture.md`, `decisions.md`, `state.md`,
  `questions.md`) — also disagrees on storage substrate (Markdown-only, no
  DB, vs. ChatMap's SQLite node+edge graph model in
  `knowledge-representation-PRELIMINARY.md`).

**Not resolved.** Ray is deliberately keeping this open — still in a
divergent/exploratory phase, declined to pick a winner yet.

### "Three settled decisions" traced to their actual source

The "Settled Decisions" section of `semantic-extraction-design-PRELIMINARY.md`
turns out to be lifted near-verbatim from `architectural_review.md`
(Antigravity's review). One reviewer's recommendation, promoted to
"Settled" status — not an independently converged consensus. Treat as
provisional if revisited.

### vision.md duplicated first-principles.md — fixed this session

`handoffs/vision.md` and root `first-principles.md` said the same thing
twice, at different levels of rigor, with no cross-references between them.
`first-principles.md` is the one every other doc in the corpus cites by
number; `vision.md` was cited nowhere.

Action taken: vision.md's one genuinely unique piece (the 5-stage roadmap +
future-input list) was folded into `first-principles.md` as a new "Staged
Evolution" closing section. The rest of `vision.md` was moved to
`handoffs/archive/vision.md` with a supersession header, per Principle 5
(preserve history, don't delete). Committed and pushed
(`15a57b2`).

### qwen/aider incident — root cause identified, not yet fully guarded against

Earlier session: qwen, running under aider, made unrequested architectural
changes (`CommandBus`, `ChatProviderFactory`, `ChatProviderFetcher`,
`ChatProviderMetadata`) plus left stray background processes and an
untracked `.aider.tags.cache.v4/` cache folder. Fully cleaned up at the
time (commits reverted, processes killed, cache removed, `.aider*`
gitignored) — but the fix was reactive, not a standing rule.

This session's response: `files/agents.md` in dotmdfiles tightened —

- "must ask before" now explicitly covers introducing new abstractions/
  frameworks/patterns not already present, and doing more than the task
  asked for.
- "must never do" now explicitly covers leaving background processes
  running, committing scratch/cache/tool-generated files, and expanding
  scope beyond what was asked — each with "even if it seems like an
  improvement" called out, since that's the likely rationalization gap.

Drafted this session, not yet committed/pushed — see Next Actions.

## Decisions made this session

- **Commit message format**, added to `dotmdfiles/files/agents.md`,
  committed and pushed (`ed96098`, later revised): one-line what+factual,
  message MUST NOT claim what the diff/change doesn't do, optional
  `Decision:` and `Open:` trailers. Deliberately kept to two general
  trailers (not the original four-trailer `Decision/Constraint/Rejected/
  Affects` proposal from `semantic-extraction-and-commit-metadata-handoff.md`
  Part B) so the format works outside software contexts too, not just code.
- **`vision.md` retired**, folded into `first-principles.md` — see above,
  pushed (`15a57b2`).
- **Directory approach for ChatMap**: keep current structure
  (`handoffs/` as catch-all, `handoffs/archive/` for dead docs) rather than
  splitting into multiple typed subdirectories now. Revisit once counts per
  category (handoffs vs. reviews vs. designs) are actually known.

## Open questions — deliberately not decided

- Canonical file set for the closed current-truth store — still open, see
  Findings above.
- Storage substrate — Markdown-only vs. SQLite node+edge graph — still
  open, same root disagreement.
- Whether `handoffs/archive/` is still the right structure once (if) a
  dedicated canonical `knowledge/`-style folder exists.
- Light-vs-formal commit trailer question from
  `semantic-extraction-and-commit-metadata-handoff.md` — effectively
  answered by landing on the two-field general version, but not explicitly
  closed out in that doc itself.

## Next actions

- Commit and push the `agents.md` must-ask/must-never tightening (drafted
  this session in the local dotmdfiles clone, not yet pushed).
- When ready to converge on the canonical file set: read this handoff plus
  `semantic-extraction-design-PRELIMINARY.md`,
  `knowledge-representation-PRELIMINARY.md`, and
  `dotmdfiles/semantic/design-review.md` together, side by side, rather
  than picking one in isolation.
- Feature branches for semantic extraction (Mode A / Mode B split) remain
  untouched by this session — this was a docs/process pass only.
