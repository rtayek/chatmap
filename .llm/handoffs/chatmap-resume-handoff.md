# ChatMap — Resume Handoff

> Paste this into a fresh session to continue. Terse "you are here + do next +
> pointers." Design *content* lives in the referenced notes, not here.

## Current state (all good)
- **master is clean**, all known bugs closed, substrate hardened, MVP audit PASSED.
- Merged this session: migration atomicity (data-loss bug, tested with forced
  mid-migration failure), worktree preservation (dirty-failure tests), CI/codex.cmd
  (green, fine on Windows), structured-output silent-blank fix, Ollama null-safety,
  Model/Channel refactor (independent many-to-many axes; `"default"` → Optional),
  code-health batch (ProjectRegistry, GitOutcome, JSON-helper dedup), MyClaw
  TranscriptRenderer fence-safety port, MyClaw ProcessRunner edge tests.
- No open bugs. No must-fix/should-fix on master.

## Hard-won lesson: aider/qwen-2.5-72b is RETIRED from review/refactor duty
- Given "improve the project," qwen twice produced confident garbage: a broken
  CommandBus (thread-pool-block anti-pattern), an async-for-nothing ProcessRunner
  rippling `.join()` into 18 files, and a commit whose message did NOT match its diff
  ("add shutdown/close to 4 classes" that actually added two toString()s + an empty
  method). Both batches caught before settling, reverted, master restored.
- Rule going forward: any agent gets NARROW, specific handoffs only — never "review
  the project." Review EVERY diff before merge, especially any "fix compile errors"
  follow-up (that's where ripple damage hides). Prefer a frontier model over qwen.
- aider scratch files are gitignored (`.aider*`, ideally global).

## Next work: two flagship CAPABILITIES (per Principle 17 — substrate is done)
Both greenfield, both on their OWN long-lived branches, both DESIGN-FIRST (diagrams
before code, like the Model/Channel work). They share a data substrate (see below).

1. **SemanticExtractionService** (the flagship — the app's actual point).
   - Two modes: **Reconcile FIRST** (diff a memory/findings doc's concrete claims
     against current code → still-valid/obsolete/needs-check, file:line evidence),
     **Compress second** (chat → distilled template).
   - Proven feasible by hand: the `Chat` dual-hash finding (contentHash() and
     transcriptHash() both return importMetadata.transcriptHash() at Chat.java:44,104)
     is STILL VALID; two sibling findings in the same memory doc are OBSOLETE by the
     hexagonal refactor. That triage IS the demo.
   - Sources: Claude Code memory (Markdown, minable), Codex memory (Markdown, minable,
     frozen ~Aug 17), commit log (third source — see commit-metadata note).
     Antigravity memory is protobuf/opaque — out of scope. Ollama has none.

2. **Facets** (faceted classification). Orthogonal to extraction's plumbing but
   SHARES the knowledge substrate: a facet is just a short `instance-of` edge.

## Shared foundation (settle before building either branch)
Node + typed-directed-edge model: atomic knowledge units AND category values are
NODES; classification (facets) and semantic relations (supports/contradicts/
refines/replaces/...) are typed EDGES in one store. Flat SQLite, no graph DB, no
framework. Hierarchies/sets-of-sets/views are DERIVED, never stored.

## Open decision waiting on Ray
- Commit-message metadata: **light** (just accurate messages stating decision + why)
  vs **formal** (`Decision:/Rejected:/Constraint:/Affects:` trailers mapping to graph
  nodes). Likely: hard "message ⊆ diff" accuracy rule for everyone; formal trailers
  optional, mainly for agents. Pick before writing `commit-guidelines.md`.

## Design docs to pull (the actual content)
In `handoffs/`:
- `semantic-extraction-design-PRELIMINARY.md` — two modes, reconcile-first, input
  taxonomy, cross-tool memory survey addendum.
- `knowledge-representation-PRELIMINARY.md` — node+edge model, "a facet is a short
  edge," guardrails.
- `semantic-extraction-and-commit-metadata-handoff.md` — O(n²) motivation, commit
  log as third source, structured-commit-trailer idea + guardrails.
- `claude-memory-survey.md` — the 12-file memory survey.
- `architectural_review.md` — agy's review (validated: master is clean).

## Immediate next step
Start the SemanticExtractionService design conversation (Reconcile mode) — diagrams
first, settle output format + what counts as a checkable claim, THEN a build handoff.
Do NOT hand this to an agent cold.
