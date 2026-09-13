---
id: CM-EXP-SEMANTIC-01
lifecycle: working
status: active
provenance: externally-assigned-experiment
---
# ChatMap Bounded Semantic-Evaluation Experiment

## Assignment

Run a small, reproducible experiment that measures whether an LLM preserves
explicit meaning while converting short source passages into structured facts.
This is an external research experiment. Do not integrate it into ChatMap's
production import, database, UI, A2A, or worker-lifecycle paths.

## Background

ChatMap's long-term purpose is to mine conversations for durable, connected,
reviewable semantic knowledge while preserving provenance and history. Existing
work has proven transport, structured output, lifecycle recording, and one
bounded four-field semantic probe. That single passing example does not prove
general semantic reliability. Manual trials have already shown fact reversal,
overstated causality, and instruction violations from the same local model.

## Required project orientation

Before starting:

1. Record the ChatMap commit being examined.
2. Follow the repository's normal discovery path beginning with `AGENTS.md` and
   `.llm/index.md`.
3. Read only the project documents selected by that index and any handoff
   directly relevant to the existing semantic probe.
4. Do not read or scan `.chatmap-local/`.
5. Do not modify canonical project-memory documents while conducting the test.

## Questions to answer

1. Can the model return valid JSON in the required schema?
2. Does it preserve every explicitly stated fact?
3. Does it preserve negation, uncertainty, disagreement, and chronology?
4. Does it avoid inventing unsupported facts or causal relationships?
5. Does it distinguish accepted decisions, rejected alternatives, and open
   questions?
6. Are repeated runs stable enough to support deterministic downstream checks?

## Scope

- Required baseline model: local `qwen2.5:7b`, using the same Ollama path as the
  existing bounded probe.
- Optional comparison: one additional model or CLI, provided its name, version,
  settings, and invocation method are recorded.
- Corpus: exactly the ten cases below for the first run.
- Repetitions: three independent runs per case and model.
- Temperature: zero when the provider supports it; otherwise record the actual
  setting or that it could not be controlled.
- No conversational memory between cases.
- No model grading of its own output.

## Required output schema

The model must return one JSON object and no surrounding prose:

```json
{
  "case_id": "S01",
  "facts": [
    {
      "subject": "text",
      "relation": "text",
      "object": "text",
      "polarity": "affirmed",
      "certainty": "certain",
      "source_ids": ["s1"]
    }
  ],
  "decisions": [],
  "rejected_alternatives": [],
  "open_questions": []
}
```

Allowed values:

- `polarity`: `affirmed` or `negated`
- `certainty`: `certain`, `uncertain`, or `disputed`
- `source_ids`: one or more supplied sentence identifiers

Use empty arrays when a category has no entries. Do not infer facts that are not
explicitly supported by the supplied sentences.

## Fixed corpus and expected meaning

The executor must encode the expected answers as separate fixtures before
running any model. Minor textual normalization may be defined in advance, but
the expected semantic values must not be changed after seeing model output.

### S01 - Identity

Input: `[s1] The project is named ChatMap.`

Expected: one certain, affirmed fact: ChatMap is the project name.

### S02 - Quantity

Input: `[s1] The experiment used three read-only workers.`

Expected: one certain, affirmed fact preserving the number three and the
read-only qualification.

### S03 - Date

Input: `[s1] The review was completed on September 12, 2026.`

Expected: one certain, affirmed completion-date fact, normalized to
`2026-09-12` if normalization is declared in advance.

### S04 - Negation

Input: `[s1] ChatMap recorded the workers. [s2] ChatMap did not launch them.`

Expected: one affirmed recording fact and one negated launching fact. The output
must not claim that ChatMap launched or scheduled the workers.

### S05 - Uncertainty

Input: `[s1] The failed import may have preserved a partial result.`

Expected: one uncertain fact. It must not be upgraded to certain.

### S06 - Chronology without causality

Input: `[s1] Eclipse was closed. [s2] The next Gradle check passed.`

Expected: two certain chronological facts. No fact may state that closing
Eclipse caused the check to pass.

### S07 - Decision and rejected alternative

Input: `[s1] We decided to keep ChatMap as the ledger. [s2] We rejected making
ChatMap a general scheduler.`

Expected: one accepted decision and one rejected alternative. The rejected
alternative must not appear as an accepted design.

### S08 - Open question

Input: `[s1] We have not decided whether to add semantic search.`

Expected: one open question or undecided issue. It must not become a decision or
an implemented feature.

### S09 - Supersession

Input: `[s1] The old plan placed handoffs in the repository root. [s2] The
current plan places them under .llm/handoffs.`

Expected: the root location is historical/superseded and `.llm/handoffs` is the
current location. The two statements must not be reported as simultaneous
current requirements.

### S10 - Conflicting reports

Input: `[s1] Worker A reported that the test passed. [s2] Worker B reported that
the same test failed. [s3] No independent verification was performed.`

Expected: two attributed, disputed reports plus the certain absence of
independent verification. The output must not select either result as true.

## Prompt protocol

Use one fixed instruction prompt for every case. It must:

1. Include the exact output schema and allowed enum values.
2. Require source identifiers for every extracted item.
3. Prohibit outside knowledge and unsupported inference.
4. Require preservation of negation, uncertainty, attribution, and status.
5. Require JSON only.

Freeze and hash the prompt before the first model run. Record any provider-added
system prompt that is visible to the executor.

## Deterministic evaluation

Evaluate model output with code or exact fixtures, not with another LLM.

Record these measures for every run:

- Invocation completed successfully.
- Output parsed as JSON.
- Output conformed to the schema.
- Required facts or classifications were present.
- No expected fact was omitted.
- No supplied fact was reversed.
- No uncertainty was upgraded to certainty.
- No unsupported causal relation was introduced.
- No unsupported fact was introduced.
- Every item cited an appropriate supplied sentence ID.
- The output was semantically identical across the three repetitions.

Report both exact counts and the individual failing cases. Do not collapse all
results into one overall percentage.

## Suggested scoring

For each run, report:

- `format_pass`: boolean
- `required_items`: integer
- `correct_items`: integer
- `omissions`: integer
- `reversals`: integer
- `certainty_errors`: integer
- `unsupported_inferences`: integer
- `provenance_errors`: integer

A case passes only if `format_pass` is true, every required item is correct, and
all error counts are zero.

## Required artifacts

Produce:

1. `experiment-manifest.json` - commit, environment, model identifiers,
   parameters, prompt hash, corpus hash, and timestamps.
2. `prompt.txt` - the frozen instruction prompt.
3. `cases/` - the ten immutable input and expected-output fixtures.
4. `raw/` - unedited model responses, one file per case/run/model.
5. `scores.json` or `scores.csv` - deterministic per-run results.
6. `semantic-evaluation-report.md` - findings, failures, limitations, and a
   recommendation.
7. A final handoff stating exactly where all evidence resides.

Artifacts may be produced outside the repository. If repository files are
added, use a dedicated experiment branch and keep generated output out of
production source directories.

## Acceptance criteria

The experiment is complete when:

- All ten cases have three recorded runs for the baseline model.
- The prompt and expected fixtures were fixed before model execution.
- Every raw response is preserved unchanged.
- Scoring is reproducible without an LLM.
- Negation, uncertainty, chronology/causality, decisions, supersession, and
  conflicting reports each have an explicit result.
- The report distinguishes transport/format success from semantic success.
- The report does not claim general semantic reliability from this corpus.
- ChatMap production code, schema, data, and UI remain unchanged.

## Decision requested from the executor

Conclude with one of these recommendations and supporting evidence:

1. Stop: the approach is not reliable enough to justify another experiment.
2. Refine: revise the contract or prompting and repeat this same corpus.
3. Expand: the bounded results justify a larger, independently authored corpus.

Do not recommend production integration solely because the ten-case corpus
passes.
