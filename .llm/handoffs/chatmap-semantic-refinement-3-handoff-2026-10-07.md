---
title: ChatMap third semantic-refinement experiment handoff
date: 2026-10-07
project: ChatMap
experiment: CM-EXP-SEMANTIC-03
status: ready
---

# ChatMap Third Semantic-Refinement Experiment Handoff

## Assignment

Run one final bounded prompt-refinement experiment with local `qwen2.5:7b`.
Test whether explicit coreference and evidence-preservation rules can correct
the remaining S04 and S10 failures while preserving all prior successes.

This assignment is runtime-neutral. Claude Code, Codex CLI, or another capable
command-line agent may execute it. The executing agent is responsible for
following the frozen experimental procedure; it must not grade model output
with another LLM.

## ChatMap boundary

ChatMap's long-term purpose is to mine conversations for durable, connected,
reviewable semantic knowledge while preserving provenance and history.
Transport, valid JSON, repeatability, and durable storage do not establish
semantic correctness. Model output remains evidence until it passes an
explicit acceptance contract.

ChatMap is a continuity and ledger system, not a general scheduler or agent
harness. This experiment remains outside ChatMap production code and data.

## Prior evidence

Preserve these two experiment directories unchanged:

```text
C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-2026-09-13\
C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-2026-10-07\
```

The first experiment, CM-EXP-SEMANTIC-01, established:

- 30/30 schema-valid responses;
- complete byte-level stability within each case;
- 7/10 strict semantic case passes;
- failures on S04 negation, S07 decision/rejection category placement, and S10
  disputed attribution.

The second experiment, CM-EXP-SEMANTIC-02, established:

- 30/30 schema-valid responses;
- 23/30 passing runs;
- the same strict 7/10 case result;
- all seven previous passing cases preserved at 3/3;
- S04 still 0/3, S07 improved to 2/3, and S10 remained 0/3;
- some byte-level instability in S06 and S07;
- warning-checker tests passed, but the checker emitted no warning on actual
  failures because those failures were outside its narrow predicates.

Interpretation of the target cases:

1. **S04:** The second prompt corrected negation in all runs. It still failed
   because the negated launch fact used an empty object and did not resolve
   `them` to the previously mentioned workers. The remaining defect is
   coreference and required-argument preservation.
2. **S07:** All three second-experiment runs placed the accepted choice in
   `decisions` and the rejected choice in `rejected_alternatives`. One run used
   `Use ChatMap as the ledger`, which was semantically reasonable but outside
   the frozen scorer's accepted lexical variants. This is principally an
   evaluator-coverage issue, not a remaining category-placement failure.
3. **S10:** Every second-experiment run discarded the two attributed reports
   and the certain absence of independent verification, replacing them with a
   single open question. This is the most important remaining model failure.

## Goal and stopping rule

This is the final prompt-refinement attempt for `qwen2.5:7b` on this corpus.

- If S04 or S10 fails in any run, recommend stopping prompt refinement for
  this model and compare a stronger model using the same frozen corpus.
- If all ten cases pass all three runs without regression, recommend expanding
  to a larger, independently authored corpus.
- Do not recommend production integration from this experiment alone.

## Preservation rules

1. Treat both prior experiment directories as read-only.
2. Do not run their scorers in place.
3. Do not edit, rename, delete, or overwrite prior evidence.
4. Create a new sibling directory, preferably:

   ```text
   C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-3-2026-10-07\
   ```

5. Copy required fixtures, scorers, and evidence into the new directory.
6. Do not read or modify ChatMap's `.chatmap-local/` directory.
7. Do not modify ChatMap production code, schema, database, UI, A2A, or worker
   lifecycle.
8. Do not use an LLM to score or certify another LLM's output.
9. Preserve raw model responses exactly as received.
10. A documented blocker is a valid result. Do not reconstruct missing evidence
    or silently change the experimental procedure.

## Phase 1: reproduce prior evidence from copies

1. Inventory both prior experiment directories and record SHA-256 hashes.
2. Copy the required files into the new experiment directory.
3. Run both prior deterministic scorers only against the copies.
4. Confirm the first result is 7/10 and the second result is 7/10 strict cases,
   23/30 passing runs.
5. Record the current ChatMap Git revision for context, without requiring or
   altering ChatMap's worktree.

Stop and report a blocker if the prior results cannot be reproduced.

## Phase 2: preregister the third refinement

Before any model request, create `refinement-3-plan.md`. It must document:

- the exact prompt changes;
- the exact scoring changes;
- the exact checker changes;
- predicted effects on S04, S07, and S10;
- success and stopping criteria;
- the files that will be frozen;
- how legacy and revised scoring will be reported separately.

### Prompt refinement: coreference

Add a worked example using entities and wording different from S04. The example
must show that a pronoun or anaphoric reference is replaced by its explicit
referent in the structured output.

The prompt must state:

- preserve every explicit participant and required relation argument;
- resolve pronouns such as `it`, `they`, `them`, and `this` when the referent is
  unambiguous from the supplied text;
- do not leave `object` empty when the source supplies an explicit or
  unambiguous referenced object;
- if the referent is genuinely ambiguous, preserve the ambiguity explicitly
  rather than inventing an entity.

### Prompt refinement: attributed disagreement

Add a worked example using entities and wording different from S10. It must
show that:

- a report is a fact about what the named source reported, even when the
  underlying event remains disputed;
- each conflicting report remains separately attributed;
- disagreement changes the certainty of the underlying reported claim to
  `disputed`; it does not erase the reports;
- an explicit statement that no independent verification occurred is itself a
  certain fact;
- uncertainty about the underlying event must not collapse all supplied
  evidence into one `open_questions` item.

### S07 scorer correction

Do not retroactively change CM-EXP-SEMANTIC-02. Its frozen 7/10 result remains
authoritative for that experiment.

For CM-EXP-SEMANTIC-03, preregister one of these approaches before execution:

1. extend the accepted decision vocabulary to include semantically equivalent
   forms such as `use`; or
2. define a canonical structured action/value representation that avoids a
   small ad hoc verb list.

Report two results after execution:

- **legacy score:** using the unchanged CM-EXP-SEMANTIC-02 scorer for direct
  historical comparison;
- **revised score:** using the new preregistered scorer.

Never change either scorer after the first model request.

## Phase 3: extend deterministic diagnostics

Keep the checker diagnostic: it may flag or reject output but must never repair
or rewrite model output.

Add focused tests and rules for at least:

1. an empty structured object where the source contains an unambiguous
   anaphoric object;
2. conflicting attributed reports in the source when the output contains no
   corresponding report facts;
3. an explicit no-verification statement placed only in `open_questions` or
   omitted entirely;
4. the existing affirmed-negation, category-placement, and false-certainty
   checks from the second experiment;
5. conforming controls for every rule to limit obvious false positives.

Be explicit about checker limitations. A generic checker cannot prove that all
meaning was preserved without an expected fixture. Fixture-based scoring
remains the evaluation authority.

## Phase 4: freeze before execution

Before contacting Ollama:

1. Freeze and hash the prompt, worked examples, ten unchanged fixtures, runner,
   legacy scorer, revised scorer, checker, and tests.
2. Record model name and digest, Ollama version, parameters, environment,
   corpus hash, prompt hash, and timestamps in `experiment-manifest.json`.
3. Commit the complete pre-execution state to the new experiment repository.
4. Record that commit hash in the manifest and plan.
5. Verify that the worktree is clean.

After the first model request, do not modify frozen inputs or executable
artifacts. If a defect requires a change, preserve the run as invalidated,
create a new version, freeze it, and start again.

## Phase 5: execute the unchanged corpus

Run exactly ten cases with three independent runs per case using:

- local `qwen2.5:7b` through the same Ollama path;
- temperature 0 and seed 0;
- JSON mode;
- no conversational memory between cases;
- the frozen third prompt;
- the unchanged expected semantic fixtures.

Store all 30 raw responses and invocation metadata in the new experiment
directory. Do not normalize or manually edit responses.

## Phase 6: score and compare

Run deterministic scoring and diagnostics without contacting Ollama again.
Produce exact per-run and per-case results for:

- schema and format validity;
- required items, omissions, and unmatched items;
- polarity, certainty, and category placement;
- source/provenance preservation;
- unsupported facts and causal relationships;
- checker warnings;
- cross-run semantic and byte-level stability;
- legacy score versus revised score;
- comparison with CM-EXP-SEMANTIC-01 and CM-EXP-SEMANTIC-02.

Manually inspect S04, S07, and S10 only after frozen scoring is complete. Do not
change the scores after inspection. Record apparent scorer false positives or
false negatives as qualifications.

## Acceptance criteria

Strong success requires all of the following:

1. S01, S02, S03, S05, S06, S08, and S09 remain passing in all three runs.
2. S04 preserves both facts, resolves the workers as the negated launch
   object's referent, and passes all three runs.
3. S07 places the decision and rejection correctly and passes all three runs
   under the preregistered revised scorer; report its legacy score separately.
4. S10 preserves both attributed reports as disputed evidence and preserves
   the certain absence of independent verification in all three runs.
5. All 30 responses are valid, schema-conforming JSON.
6. No unsupported fact, reversal, or causal relation is introduced.
7. Every extracted item has appropriate source IDs.
8. All checker tests pass, including conforming controls.
9. Frozen artifacts remain byte-identical after execution.

## Required artifacts

Keep these in the new external experiment directory:

1. `refinement-3-plan.md`
2. `experiment-manifest.json`
3. prior-evidence inventory and hashes
4. frozen-input hash inventory
5. frozen prompt and worked examples
6. unchanged corpus fixtures
7. runner source
8. legacy and revised scorer sources
9. checker source and focused tests
10. all 30 raw responses and invocation metadata
11. legacy and revised score files
12. checker-warning results
13. comparison across all three experiments
14. `semantic-refinement-3-report.md`
15. `semantic-refinement-3-final-handoff.md`

## Final recommendation

Conclude with exactly one recommendation:

- **Stop prompt refinement for qwen2.5:7b and compare a stronger model:** use
  this if S04 or S10 fails any run, or if a previously passing case regresses.
- **Expand:** use this only if all ten cases pass all three runs without
  regression under the preregistered evaluation.

Do not recommend production integration from this experiment alone.

## Repository delivery

Do not modify ChatMap during execution. After Ray reviews the result, copy only
the concise final handoff into ChatMap's `.llm/handoffs/`. Leave prompts,
scripts, score files, raw responses, and reports in the external experiment
repository.

## Completion response to Ray

Return a concise summary containing:

- both prior-result reproductions;
- exact prompt, scorer, and checker changes;
- legacy and revised results for all cases;
- exact S04, S07, and S10 outcomes;
- stability and checker-test results;
- whether the stopping rule was triggered;
- evidence path and pre-execution commit hash;
- the final Stop-or-Expand recommendation;
- confirmation that ChatMap code and data were untouched.
