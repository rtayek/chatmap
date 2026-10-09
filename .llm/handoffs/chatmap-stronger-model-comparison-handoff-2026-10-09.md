# CM-EXP-SEMANTIC-04 stronger-model comparison handoff

## Assignment

Run one bounded semantic-extraction comparison using `qwen2.5:14b` through
Ollama. Change the model only. Preserve the final CM-EXP-SEMANTIC-03 prompt,
ten-case corpus, request settings, deterministic scorers, warning checker,
manual acceptance rules, and evidence format.

This experiment asks whether increased model capacity corrects the remaining
semantic failures. It does not authorize production integration or changes to
ChatMap code, schema, database, UI, or project data.

## Why this model

`qwen2.5:14b` is the first comparison candidate because it keeps the model
family, local Ollama provider, privacy boundary, and request path constant while
increasing capacity beyond `qwen2.5:7b`. This gives a cleaner comparison than
changing both model family and provider at once.

The default Ollama build may exceed the available GPU memory. CPU or mixed
CPU/GPU execution is acceptable; record the observed execution environment and
timings. Performance is secondary to semantic correctness in this experiment.

## Authoritative prior evidence

Use these records:

- ChatMap handoff:
  `.llm/handoffs/chatmap-semantic-refinement-3-final-handoff-2026-10-07.md`
- CM-EXP-SEMANTIC-03 evidence repository:
  `C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-3-2026-10-07\`
- Frozen pre-execution commit:
  `70178b62a9378a400ff62145c026ce0a454c12b9`
- CM-EXP-SEMANTIC-03 result commit:
  `9f95f57dd5cc0a47c2619db05d65ea231bb829fb`

Do not modify any prior evidence repository. Do not rewrite timestamps in prior
score files merely to reproduce them.

## Isolation

Create a separate sibling repository:

`C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-stronger-model-2026-10-09\`

Create it as an independent local clone or copy of the CM-EXP-SEMANTIC-03 result
commit. Do not use the ChatMap repository as the experiment workspace. Do not
read or write ChatMap production data or `.chatmap-local/`.

Before execution, record:

- source repository path and source commit;
- new repository initial commit;
- clean `git status` for both ChatMap and the new experiment repository;
- SHA-256 hashes of every frozen prompt, fixture, scorer, checker, and test;
- Ollama version, exact model name, model digest, quantization, context setting,
  temperature, seed, JSON mode, and other request parameters.

## Phase 0: preflight and stop conditions

1. Run `ollama list` and determine whether `qwen2.5:14b` is already installed.
2. If it is absent, report the required `ollama pull qwen2.5:14b` command and
   expected download size, then pause for Ray's approval before downloading.
3. Confirm that Ollama is reachable and that the chosen model identifier
   resolves exactly.
4. Confirm that the prior evidence repository is clean at result commit
   `9f95f57dd5cc0a47c2619db05d65ea231bb829fb`.
5. Confirm that all frozen-input hashes match the preserved CM-EXP-SEMANTIC-03
   inventory.

Stop and report rather than improvising if the prior evidence is missing,
dirty, inconsistent, or cannot be reproduced.

## Phase 1: reproduce without contacting a model

Using an isolated copy, rerun the existing deterministic scorer, warning
checker, and focused checker tests against the preserved CM-EXP-SEMANTIC-03 raw
responses.

Required reproduction:

- legacy and revised score files reproduce byte-for-byte;
- the known result remains 8/10 scored cases and 24/30 passing runs;
- manual acceptance remains 7/10 because S04 is rejected in addition to S07
  and S10;
- all seven focused checker tests pass;
- the six preserved warnings reproduce;
- no model request occurs in this phase.

If any result differs, stop before model execution and explain the discrepancy.

## Phase 2: prepare the model-only comparison

Create new output paths rather than overwriting prior responses or reports.
Keep the complete prompt, fixtures, ordering, three-runs-per-case schedule,
request parameters, JSON schema, scorer, warning checker, and acceptance rules
unchanged.

The only intended experimental variable is:

- baseline model: `qwen2.5:7b`
- comparison model: `qwen2.5:14b`

If the existing runner hardcodes the model name, make the smallest isolated
runner change needed to select `qwen2.5:14b`. Record that change explicitly and
test that it changes no prompt or scoring input.

Commit the prepared, pre-execution state before contacting Ollama.

## Phase 3: execute once

Run all ten frozen cases exactly three times with `qwen2.5:14b`, for 30 total
responses. Do not retry, repair, or selectively regenerate individual outputs.
If execution is interrupted, preserve the partial evidence and report the
failure; do not silently resume in a way that obscures chronology.

For every request preserve:

- full prompt hash;
- raw response bytes;
- case and repetition identity;
- start and finish timestamps plus elapsed duration;
- HTTP or process result and any error;
- exact invocation metadata.

## Phase 4: score and inspect

Run the unchanged deterministic scorer and warning checker. Then manually
inspect all 30 responses, with special attention to:

- S04: the output must resolve `them` to `the workers`; retaining only the
  pronoun fails manual acceptance even if the frozen scorer passes it;
- S07: decisions, rejections, and open questions must be placed in their proper
  categories rather than flattened into `facts`;
- S10: preserve both disputed attributions and explicitly preserve the fact
  that neither report was independently verified;
- the seven previously accepted cases: identify any regression, fabrication,
  omitted qualifier, reversed fact, or invented causality.

Report both the unchanged scorer result and the manual-acceptance result. Do
not alter a scorer or fixture after seeing model output. If a newly discovered
scoring defect matters, document it separately and score the run with the
frozen rules first.

## Phase 5: compare and decide

Produce a case-by-case comparison with:

- CM-EXP-SEMANTIC-01: 7/10 scored cases, 21/30 passing runs;
- CM-EXP-SEMANTIC-02: 7/10 scored cases, 23/30 passing runs;
- CM-EXP-SEMANTIC-03: 8/10 scored cases, 24/30 passing runs, but 7/10 under the
  explicit manual S04 rule;
- CM-EXP-SEMANTIC-04: scored cases, passing runs, manual acceptance, warnings,
  stability, regressions, and timings.

Classify the result:

- **Strong improvement:** all ten cases pass all three runs under both frozen
  scoring and manual acceptance, with no regression or fabrication.
- **Useful improvement:** at least two of S04, S07, and S10 are corrected under
  manual acceptance, with no regression in the other seven cases.
- **Inconclusive:** execution or evidence is incomplete, or environmental
  differences prevent a fair comparison.
- **No useful improvement:** fewer than two target cases are corrected, or a
  previously accepted case regresses.

Regardless of classification, do not integrate semantic extraction into
production. Recommend only the next bounded evidence step.

## Required artifacts

Commit these to the new experiment repository:

- immutable input and tool hash inventory;
- pre-execution plan and invocation metadata;
- all 30 raw responses;
- scored JSON and CSV outputs;
- warning output and checker-test results;
- case-by-case baseline comparison;
- manual acceptance record;
- final experiment report;
- `semantic-stronger-model-final-handoff.md`.

The final handoff must state exactly what changed, what remained frozen, the
model identity and digest, scored and manual results, evidence locations,
limitations, and recommendation. Do not copy the final handoff into ChatMap
until Ray reviews it.

## Completion report to Ray

Return a concise report containing:

1. preflight result and exact model identity;
2. reproduction result;
3. scored and manual comparison totals;
4. S04, S07, and S10 outcomes;
5. any regression or fabrication;
6. result classification;
7. pre-execution and result commit hashes;
8. paths to the detailed report and final handoff;
9. confirmation that ChatMap code and data remained untouched.
