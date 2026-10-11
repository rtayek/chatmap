# CM-EXP-SEMANTIC-05 output-contract handoff

## Assignment

Formalize and test the semantic-extraction output acceptance contract using
only the preserved CM-EXP-SEMANTIC-03 and CM-EXP-SEMANTIC-04 responses. Do not
contact Ollama or any other model. Do not change prompts, fixtures, raw
responses, or frozen scorer outputs.

This is an evaluator experiment, not production integration. ChatMap code,
schema, database, UI, A2A paths, worker-lifecycle paths, and `.chatmap-local/`
must remain untouched.

## Decision under test

Treat the following as three independent, ordered gates:

1. **Strict JSON validity:** input is UTF-8 JSON with one top-level object and
   no duplicate member names at any nesting depth.
2. **Schema conformance:** the parsed object contains the required fields,
   types, enumerations, and cardinalities defined by the frozen extraction
   contract.
3. **Semantic acceptance:** the content preserves the required facts,
   qualifications, attribution, polarity, decisions, rejections, open
   questions, and explicit coreference rules.

A response is fully accepted only if it passes all three gates. A later gate
must not erase or hide an earlier failure. In particular, last-key-wins parsing
must not convert a duplicate-key response into a structurally valid response.

The experiment should confirm or reject this contract with deterministic
evidence. It does not authorize adding it to ChatMap production.

## Source evidence

Use these repositories without modifying them:

- CM-EXP-SEMANTIC-03:
  `C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-3-2026-10-07\`
  at result commit `9f95f57dd5cc0a47c2619db05d65ea231bb829fb`.
- CM-EXP-SEMANTIC-04:
  `C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-stronger-model-2026-10-09\`
  at strict-recheck commit `c77eede`.

ChatMap records the accepted CM-EXP-SEMANTIC-04 result in:

- `.llm/handoffs/chatmap-semantic-stronger-model-final-handoff-2026-10-11.md`

Known CM-EXP-SEMANTIC-04 totals are:

- semantic acceptance: 30/30 runs, 10/10 cases;
- strict structural validity: 27/30 runs, 9/10 cases;
- all strict failures are S09 duplicate `facts` keys.

Do not assume an unverified strict-validity total for CM-EXP-SEMANTIC-03.
Compute it from the preserved responses.

## Isolation

Create a separate sibling repository:

`C:\Users\ray\eclipse-workspace\chatmap-semantic-output-contract-2026-10-11\`

Create it as an independent local clone or copy based on CM-EXP-SEMANTIC-04
commit `c77eede`. Do not use a ChatMap worktree as the experiment workspace.

Record the source path and commit, the new repository's initial commit, clean
Git status, and SHA-256 hashes for every imported response, fixture, scorer,
validator, and expected-output file.

## Phase 1: reproduce before changing the evaluator

Without contacting a model:

1. Verify every preserved source hash.
2. Reproduce the CM-EXP-SEMANTIC-03 frozen scores and warnings byte-for-byte.
3. Reproduce the CM-EXP-SEMANTIC-04 frozen scores, warnings, and strict-JSON
   recheck byte-for-byte.
4. Confirm that reproduction changed no source evidence or modification time in
   either prior repository. Use isolated copies for any tool that rewrites an
   output file even when its contents are identical.

Stop and report rather than improvising if any source repository is dirty, a
hash differs, or a result cannot be reproduced.

## Phase 2: specify the contract before implementation

Write `semantic-output-contract.md` before changing validator code. It must
define:

- the purpose and ordering of the three gates;
- exact pass/fail behavior for each gate;
- that duplicate names are compared after JSON string escape decoding;
- that duplicate detection applies recursively to every object;
- that duplicate keys fail even when their values are identical;
- that case-distinct names remain distinct unless the schema separately
  prohibits them;
- that structural or schema failure prevents full acceptance but does not
  suppress a separately reported semantic result when semantic inspection is
  still possible;
- separate run totals and case totals for every gate;
- the rule for case passage: all three runs for that case must pass the gate;
- deterministic output ordering and stable machine-readable reports.

Commit the specification before implementing or modifying the evaluator.

## Phase 3: implement the smallest deterministic evaluator

Build on the existing strict recheck and frozen scorers. Do not add an external
dependency when the standard runtime can enforce the contract.

The evaluator must:

1. Read, never rewrite, the preserved raw responses.
2. Apply strict JSON validation first.
3. Apply schema validation only to strict-JSON-valid responses.
4. Report the frozen semantic result separately, without changing the frozen
   semantic scorer or its historical outputs.
5. Produce one row per response containing model, case, run, strict JSON result,
   schema result, semantic result, full-acceptance result, and exact failure
   reasons.
6. Produce deterministic JSON and CSV summaries with both run and case totals.
7. Exit nonzero for an evaluator execution error, while representing response
   rejection as ordinary scored output rather than an evaluator crash.

## Required focused tests

Add deterministic tests covering at least:

- valid nested objects;
- malformed JSON;
- duplicate root key;
- duplicate nested key;
- duplicate key with identical values;
- duplicate names made equal after escape decoding, such as `facts` and
  `fa\u0063ts`;
- case-distinct names, such as `facts` and `Facts`;
- valid JSON with a non-object top level;
- strict-valid JSON that fails schema;
- strict-valid, schema-valid JSON that fails semantics;
- a response that passes all three gates;
- stable output ordering and byte-for-byte repeatability.

If the existing test framework already covers a case, reference and reuse it
rather than duplicating it.

## Phase 4: score the preserved 7B and 14B evidence

Run the completed evaluator over all preserved CM-EXP-SEMANTIC-03 and
CM-EXP-SEMANTIC-04 responses. Do not run a model and do not regenerate a raw
response.

Report for each model:

- strict JSON run and case totals;
- schema-conformance run and case totals;
- semantic-acceptance run and case totals;
- full-acceptance run and case totals;
- every failure grouped by gate, case, and exact reason.

Preserve the historical lenient scorer totals alongside the new contract
totals. Do not rewrite history by replacing the old figures.

## Acceptance criteria

This task passes when:

- all source hashes remain unchanged;
- no model endpoint was contacted;
- focused tests pass;
- two consecutive evaluator runs produce byte-identical JSON and CSV output;
- the new evaluator reproduces CM-EXP-SEMANTIC-04 strict validity as 27/30 runs
  and 9/10 cases, with only S09 failing for duplicate `facts` keys;
- semantic results remain 30/30 runs and 10/10 cases for CM-EXP-SEMANTIC-04;
- CM-EXP-SEMANTIC-03 receives independently computed three-gate totals;
- structural, schema, semantic, and full-acceptance results remain visibly
  separate;
- no ChatMap production file or prior evidence repository is modified.

## Required artifacts

Commit these in the isolated output-contract repository:

- `semantic-output-contract.md`;
- input and tool hash inventories;
- evaluator source and focused tests;
- deterministic per-response JSON and CSV;
- model comparison summary;
- test and repeatability evidence;
- `semantic-output-contract-report.md`;
- `semantic-output-contract-final-handoff.md`.

The final report must recommend whether this exact three-gate contract is ready
to freeze for subsequent experiments. It must not recommend production
integration based only on the ten-case corpus.

Do not copy the final handoff into ChatMap until Ray reviews it.

## Completion report to Ray

Return a concise report containing:

1. source commits and hash-verification result;
2. exact three-gate contract adopted for the experiment;
3. focused-test and repeatability results;
4. 7B totals for all three gates and full acceptance;
5. 14B totals for all three gates and full acceptance;
6. differences from the historical lenient figures;
7. pre-implementation and result commit hashes;
8. paths to the report and final handoff;
9. confirmation that no model was contacted and ChatMap remained untouched.
