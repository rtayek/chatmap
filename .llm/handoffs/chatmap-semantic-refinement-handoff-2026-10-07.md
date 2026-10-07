---
title: ChatMap semantic-evaluation refinement handoff
date: 2026-10-07
project: ChatMap
experiment: CM-EXP-SEMANTIC-02
status: ready
---

# ChatMap Semantic-Evaluation Refinement Handoff

## Assignment

Run one bounded follow-up to ChatMap's first semantic-evaluation experiment.
Determine whether targeted prompt examples and a deterministic warning check
can correct the three known semantic failures without regressing the seven
cases that already pass.

This is an external evidence-producing experiment. Do not integrate semantic
extraction into ChatMap production code, its database, UI, A2A implementation,
or worker-lifecycle paths.

## Architectural boundary

ChatMap's long-term purpose is to mine conversations for durable, connected,
reviewable semantic knowledge while preserving provenance and history.
Successful transport, valid JSON, stable execution, and durable storage do not
establish semantic correctness. Model output remains evidence until it passes
an explicit acceptance contract.

ChatMap is a continuity and ledger system, not a general agent scheduler or
harness.

## Preserved baseline

The first experiment is `CM-EXP-SEMANTIC-01`. Its evidence should be at:

```text
C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-2026-09-13\
```

Before working, read these repository documents if present:

- `.llm/handoffs/chatmap-semantic-evaluation-experiment-specification.md`
- `.llm/handoffs/chatmap-semantic-evaluation-experiment-handoff-2026-09-13.md`
- `.llm/handoffs/independent-semantic-evidence-audit.md`

The verified baseline is:

- model: local `qwen2.5:7b`, Q4_K_M, 7.6B, through Ollama;
- settings: temperature 0, seed 0, JSON mode, no cross-case memory;
- corpus: ten fixed cases, three runs per case;
- format: 10/10 under the disclosed empty-object rule, or 9/10 under the
  earlier strict non-empty-object rule;
- semantic correctness: 7/10;
- passing: S01, S02, S03, S05, S06, S08, and S09;
- failing: S04, S07, and S10;
- all three responses for each case were byte-identical;
- the independent audit reproduced the scores byte-for-byte.

Known failures:

1. S04 negation: the denial was represented with `polarity:"affirmed"`.
2. S07 status: accepted and rejected choices were emitted as ordinary facts;
   `decisions` and `rejected_alternatives` remained empty.
3. S10 attribution/disagreement: conflicting reports were marked `certain`,
   and the absence of independent verification was misclassified.

The model generally captured the words but failed to populate the
machine-readable control fields reliably.

## Safety and preservation rules

1. Treat the baseline directory as read-only.
2. Do not edit, rename, delete, or overwrite its files.
3. Create a new sibling directory, preferably:

   ```text
   C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-2026-10-07\
   ```

4. Copy only what is needed into the new directory, preserving the originals.
5. Do not read or modify ChatMap's `.chatmap-local/` directory.
6. Do not use an LLM to grade another LLM's output.
7. Do not modify the expected meaning of the ten corpus cases.
8. Do not change a prompt, fixture, validator, or scoring rule after the first
   model invocation. If a defect requires a change, stop that run, preserve it
   as invalidated evidence, create a new version, freeze it, and begin again.
9. A documented blocker is an acceptable outcome. Do not fabricate missing
   evidence or silently reconstruct historical claims.

## Phase 1: verify and preserve the baseline

1. Confirm that the baseline directory exists and inventory its contents.
2. Run its deterministic scorer without invoking a model.
3. Confirm that the reproduced result is still 7/10 with S04, S07, and S10
   failing.
4. Record SHA-256 hashes for at least the original prompt, fixtures, raw
   responses, scorer, scores, and report.
5. Record the current ChatMap Git revision for context. Do not require a clean
   ChatMap checkout merely to run this external experiment, but record any
   relevant repository state honestly.

If the baseline cannot be found or the scores cannot be reproduced, stop and
write a blocker report before attempting refinement.

## Phase 2: specify the refinement before execution

Create `refinement-plan.md`. It must state the proposed changes, predicted
effects, success criteria, and files to be frozen. Do this before any new model
request.

### Prompt changes

Create a new fixed prompt that retains the original schema, enum values,
source-ID requirements, JSON-only rule, and prohibitions on outside knowledge
and unsupported inference.

Add short worked examples covering these three control problems:

1. **Negation:** demonstrate that an explicitly denied action uses
   `polarity:"negated"`, even when the relation text itself contains a negating
   word.
2. **Decision status:** demonstrate the difference among an accepted decision,
   a rejected alternative, and an unresolved/open question, placing each in
   its correct top-level array.
3. **Disputed attribution:** demonstrate that conflicting attributed reports
   remain attributed and `disputed`; do not select either report as true when
   no independent verification exists.

The examples MUST use different entities and wording from S04, S07, and S10.
Do not paste the test answers into the prompt.

### Deterministic warning check

Add a separate deterministic post-parse checker. At minimum it must flag:

- an explicitly negating cue in the supplied sentence or extracted relation
  paired with `polarity:"affirmed"`;
- content describing a rejected alternative placed as an accepted decision or
  ordinary affirmed fact;
- conflicting attributed reports marked `certain` when the source states that
  no independent verification occurred.

The checker may reject or flag output. It MUST NOT silently rewrite model
output into a passing answer. Preserve raw output unchanged.

The ordinary fixture-based scorer remains the authority for the ten-case
comparison; the warning checker supplies additional diagnostic evidence.

## Phase 3: freeze the experiment

Before the first model request:

1. Freeze the revised prompt, worked examples, fixtures, runner, scorer, and
   warning checker.
2. Generate SHA-256 hashes for every frozen input and executable artifact.
3. Record model name and digest, Ollama version, parameters, operating
   environment, corpus hash, prompt hash, and timestamps in
   `experiment-manifest.json`.
4. Preserve the original scorer's disclosed empty-object behavior. Do not
   revisit the S06 rule during this experiment.
5. Make the frozen state independently inspectable, preferably with a local
   Git commit inside the new experiment directory or a complete hash inventory.

## Phase 4: run the unchanged corpus

Run exactly the same ten cases, three independent runs per case, using:

- the same `qwen2.5:7b` model and Ollama path;
- temperature 0 and seed 0;
- JSON mode;
- no conversational memory between cases;
- the revised frozen prompt;
- the unchanged expected fixtures.

Store all 30 raw responses without normalization or editing. Clearly separate
them from baseline output, for example under `raw-v2/`.

## Phase 5: deterministic scoring and comparison

Produce per-run and per-case results for:

- successful invocation;
- JSON parsing and schema validity;
- required semantic items;
- omissions and reversals;
- certainty and polarity errors;
- category-placement errors;
- unsupported facts or causal relationships;
- source/provenance errors;
- cross-run stability;
- warnings emitted by the new checker.

Compare the new result directly with the verified 7/10 baseline. Report exact
case identifiers rather than only an overall percentage.

## Predeclared success criteria

The refinement is a strong success only if:

1. S01, S02, S03, S05, S06, S08, and S09 remain passing in all three runs.
2. S04, S07, and S10 pass in all three runs.
3. All outputs remain valid, schema-conforming JSON.
4. No unsupported fact or causal relation is introduced.
5. Every extracted item retains appropriate source IDs.
6. The warning checker detects deliberately malformed test specimens for each
   of its three rules.

This would produce 10/10 on the same frozen corpus. It would justify a larger,
independently authored evaluation corpus; it would not prove general semantic
reliability or justify production integration by itself.

If some failures improve but the complete criteria are not met, classify the
result as partial progress and identify the remaining cases precisely.

## Required artifacts

Keep these together in the new experiment directory:

1. `refinement-plan.md`
2. `experiment-manifest.json`
3. `baseline-hashes.txt`
4. `frozen-input-hashes.txt`
5. the frozen revised prompt and worked examples
6. unchanged corpus fixtures
7. runner source
8. deterministic scorer source
9. deterministic warning-checker source and its focused tests
10. all 30 raw model responses and invocation metadata
11. `scores-v2.json` and `scores-v2.csv`
12. `baseline-comparison.md`
13. `semantic-refinement-report.md`
14. a final handoff naming every evidence location

## Final recommendation

Conclude with exactly one recommendation:

- **Stop:** targeted refinement did not improve reliability enough to justify
  another experiment.
- **Refine again:** the result improved but one or more high-value properties
  remain unreliable.
- **Expand:** all ten frozen cases pass without regression, justifying a
  larger independently authored corpus.

Do not recommend production integration from this experiment alone.

## Repository delivery

Do not modify the ChatMap repository during the experiment unless Ray
explicitly requests it. After Ray reviews the result, the only likely
repository addition is a concise final handoff under `.llm/handoffs/`.
Generated responses, scripts, score files, and evidence should remain in the
external experiment directory.

## Completion response to Ray

Return a concise summary containing:

- baseline reproduction result;
- exact prompt and checker changes;
- new per-case results and comparison with 7/10;
- checker-test results;
- whether the success criteria were met;
- limitations and deviations;
- exact evidence path;
- the final Stop, Refine again, or Expand recommendation;
- confirmation that ChatMap production code and data were untouched.
