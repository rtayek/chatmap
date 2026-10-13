# ChatMap evaluator-extension experiment handoff

Date: 2026-10-12 (America/Los_Angeles)
Status: authorized specification, implementation, testing, and freeze of external evaluation tooling.
Scope: no model requests; no ChatMap production changes.

## Assignment and authorization

Ray approved a separate evaluator-extension experiment after CM-EXP-SEMANTIC-06 preparation stopped at its compatibility gate. Proceed with the smallest external tooling change that permits explicit corpus selection and deterministic, fixture-driven semantic scoring.

You are authorized to modify copies of external scorer/evaluator code, add test fixtures, implement the documented extension, run deterministic tests without a model, and make local commits in an isolated experiment repository. Preserve the extraction prompt and response schema byte-for-byte. Do not change ChatMap code, schema, data, UI, A2A, or worker-lifecycle paths. Do not read .chatmap-local/. Do not push unless separately authorized.

This handoff supersedes the original preparation spec's prohibition on changing copied executable evaluation tooling, solely within the new evaluator-extension experiment. All prior frozen evidence remains immutable. It does not authorize a response-schema change, prompt tuning, or model execution.

## Inputs and provenance

Read the following in the local preparation repository:

- chatmap-adversarial-corpus-specification-handoff-2026-10-12.md — all A01–A24 source passages and expected meanings.
- corpus-compatibility-report.md.
- prior-reproduction.md.
- experiment-manifest.json.
- input-hash-inventory.txt.
- preparation-final-handoff.md.
- checkpoint-receipt.md.

Preparation repository:
C:/Users/ray/eclipse-workspace/chatmap-semantic-adversarial-corpus-2026-10-12/

Ray's supplied execution report names commits 0d95ac5 (checkpoint) and 7fccf52 (receipt). Resolve these locally to full SHAs; verify their content and hashes. They are reported evidence until inspected. Do not infer their full SHAs.

Prior external sources:

- 03: chatmap-semantic-eval-refinement-3-2026-10-07, result commit 9f95f57dd5cc0a47c2619db05d65ea231bb829fb.
- 04: chatmap-semantic-eval-stronger-model-2026-10-09, strict-recheck commit c77eede.
- 05: chatmap-semantic-output-contract-2026-10-11, result commit 8edd9c3; specification commit 2ac5794.

The preparation report says prior totals reproduced byte-for-byte, frozen tests passed 19/19 and 7/7, and no model ran. Verify recorded evidence before relying on it.

Create an independent sibling repository:
C:/Users/ray/eclipse-workspace/chatmap-semantic-evaluator-extension-2026-10-12/

Use ordinary copies or an independent local clone; do not use a ChatMap worktree. Do not rename, clean, overwrite, or delete existing repositories. If that destination already exists, inspect it and continue only if its identity and purpose match; otherwise select a new nonconflicting sibling.

## Problem to resolve

The reported frozen score-v3.py and evaluate-contract.py discover only S*.json and S-prefixed response names. No supported corpus-registration mechanism exists. A01–A24 may therefore be silently ignored.

Gate 3 in the historical contract evaluator reads previously computed semantic results. It is an aggregation/acceptance gate, not a semantic inference engine. New output needs an actual semantic score before aggregation.

Six cases were flagged as potentially unrepresentable:

| Cases | Meaning at issue |
|---|---|
| A04, A06, A09, A24 | Temporal scope, correction, or supersession |
| A16 | Genuine ambiguity of the approver |
| A21 | Per-worker rather than global memory limit |

A missing dedicated schema field does not by itself prove that a meaning cannot be expressed. Free-text subject, relation, and object fields might carry it. Distinguish representability from prompt guidance, scorer support, and general reliability.

## Phase 1: inspect and preregister

Before changing executable code:

1. Verify source revisions and cleanliness. Copy only the needed evidence and tools. Record provenance and byte hashes.
2. Inspect the exact prompt-v3.txt schema, actual category item types, frozen scorer logic, discovery logic, and 05 aggregation behavior. Do not infer the schema from an old prose summary.
3. Write evaluator-extension-plan.md specifying the proposed interfaces, expected-fixture format, normalization/equivalence rules, provenance policy, status handling, errors, tests, and reporting.
4. Write representability-review.md for all 24 cases, with detailed findings for the six listed above. Also review attribution/certainty in A08, A09, and A24 and causal representation in A18/A19. Do not assume the other 18 are automatically supported.
5. Commit the specification before implementation. No model requests.

### Representability evidence

For each questioned case, construct at least one synthetic output witness using only the exact existing response schema. Apply the unchanged strict JSON and schema validators. Show where every required meaning is represented, including relevant qualifiers, supporting source IDs, and category placement.

A schema-valid witness must be semantically reviewed against the source; arbitrary text that happens to pass type checking is not enough. Witnesses are evaluator test data, never new prompt examples.

For each questioned distinction, construct a schema-valid counterexample that changes or omits that distinction. Examples: treating an old location as current; selecting Rowan as certain approver; making 500 MB a global limit. Explain precisely how the proposed fixture scorer distinguishes witness from counterexample.

Classify each case separately:

- Representable and deterministically scoreable under the unchanged schema.
- Representable, but requires scorer support explicitly included in this extension.
- Expectation ambiguous or not reliably scoreable within this bounded approach.
- Not representable under the actual frozen schema.

Keep all 24 passages and intended meanings in the specification. Do not silently remove difficult cases or weaken their expected meanings. If needed, mark cases deferred with reasons and report evaluated coverage out of 24. Do not claim a complete 24-case freeze when some cases remain deferred.

If an expected meaning contradicts the exact frozen prompt or cannot be encoded without changing the response schema, document it and scope that case out of execution readiness. Preserve the original case and request a separate decision in the completion report; continue independent evaluator work on compatible cases.

## Phase 2: explicit corpus discovery

Implement the smallest deterministic interface appropriate to the existing scripts:

- Select a corpus through an explicit path or manifest enumerating case IDs and source/expected fixture paths.
- Preserve historical S01–S10 evaluation through the unchanged legacy tools or an explicit legacy mode.
- Derive valid case IDs from the selected corpus, rather than baking S or A families into regular expressions.
- Reject duplicate case IDs, invalid manifest entries, missing expected fixtures, unexpected selected response IDs, and mismatched response case_id values.
- Define stable sorting and deterministic output paths.
- Make an empty selected corpus an error. Never report zero cases as successful completion.
- Print/report selected and discovered case counts so silent exclusion is visible.
- Handle missing expected run artifacts according to an explicit report policy; never silently shrink the denominator.

A complete reusable framework is unnecessary. Prefer a small adapter around existing code when it satisfies the specification. Use the existing runtime and standard library; no new dependencies.

## Phase 3: fixture-driven semantic scoring

Build a bounded deterministic scorer that reads expected fixtures and actual response contents. Do not merely add prewritten per-run pass/fail scores for future results.

Preregister the following:

- Required meaning items and category.
- Required participant/object arguments.
- Polarity and certainty.
- Attribution and literal-value preservation.
- Temporal/supersession or ambiguity qualifiers carried by existing text fields where needed.
- Exact source-ID requirements, including antecedents and combined support.
- Explicit acceptable lexical variants or canonical representations.
- Permitted grouping/splitting of meanings, if any.
- Forbidden assertions and unsupported/unmatched items.
- Treatment of duplicates, repeated content, omitted items, and extra provenance IDs.

Avoid loose substring checks that let “historical” and “not historical,” different quantities, or different entities match. If finite lexical variants are used, document their coverage limits. Do not claim that string matching proves general semantic equivalence.

Use one-to-one matching or an explicitly specified coverage algorithm so one output item cannot accidentally satisfy several distinct obligations. For a legitimate combined fact, require all covered meanings and their provenance.

Evaluate all categories; misplaced facts must fail even when their wording matches. Missing or changed supporting IDs must fail the corresponding item. Unsupported additional assertions must not disappear from the result.

Report exact item-level matched, omitted, unmatched, reversed, misclassified, certainty, and provenance diagnostics where the fixture design supports them.

The same LLM must not grade its own response, and no other LLM may grade model outputs. This task uses no model at all; synthetic witnesses are authored test data, not observed model successes.

## Phase 4: preserve the three gates and history

Strict JSON and schema acceptance remain unchanged. A response must pass all three gates to be fully accepted.

The new semantic scorer computes new semantic outcomes; the acceptance aggregator combines those outcomes with the first two gates. Mark the semantic score origin and evaluator version in result metadata.

If strict JSON fails, do not silently salvage a response with last-key-wins parsing. Report semantic scoring as not evaluated/unscorable unless a separately named historical inspection route is being reproduced. Schema failure likewise prevents full acceptance.

Historical reproduction must use frozen legacy tools over copied evidence. Preserve their byte-identical outputs and previously accepted totals:

| Historical result | 7B | 14B |
|---|---|---|
| Semantic | 24/30 runs, 8/10 cases | 30/30 runs, 10/10 cases |
| Full acceptance | 24/30 runs, 8/10 cases | 27/30 runs, 9/10 cases |

Do not force a new fixture scorer to mimic a known historical false positive. If it is also applied to historical responses, label its results as a separate re-evaluation and explain differences. Never replace historical scores with new figures.

## Phase 5: synthetic acceptance tests

Before any model runs, require:

1. A synthetic positive witness for every ready A-case.
2. For each required item, a mutation omitting it.
3. Mutations of polarity, certainty, category, source IDs, and entity/quantity qualifiers where applicable.
4. Unsupported added facts and causal assertions.
5. Targeted counterexamples for every representability concern.
6. Agreed versus conflicting attribution, decision versus implementation, conditional versus unconditional status, and literal source text versus response structure.
7. Manifest discovery of all selected A-cases, historical S-cases, and at least one additional arbitrary valid manifest case ID to prove discovery is not just [SA].
8. Missing/duplicate fixture and case-ID mismatch errors.
9. Malformed JSON, decoded duplicate root/nested keys, identical duplicates, trailing prose, fences, non-object top level, and schema failures. Reuse frozen structural tests where sufficient.
10. Two runs producing byte-identical reports.
11. No fixture mutation or response rewriting during scoring.
12. Schema-skipped and semantic-unscorable outcomes remaining distinct from pass.

All positive witnesses must pass; all documented negative mutations must fail for the intended reason. An evaluator exception is an execution error, not a scored model rejection. Acceptance test results must identify actual counts and failures.

Tests should challenge the scoring logic, not merely mirror implementation branches. Record which distinctions remain dependent on a narrow lexical contract. Synthetic success demonstrates evaluator behavior only.

## Phase 6: freeze and stop

Freeze only after specification, representability review, fixtures, code, and tests agree:

- Preserve the original 24 passage bytes and expectations.
- Verify copied prompt and schema hashes remain identical to their originals.
- Hash and commit extension specification, explicit corpus manifest, ready fixtures, deferred-case inventory if any, witnesses and mutations, scorer/adapter, unchanged strict/schema validators, tests, reporting protocol, and provenance.
- Record the full pre-execution commit SHA in an external receipt or later metadata-only commit. Avoid self-referential commit/hash inventories.
- Verify clean Git status and repeated test/report output.
- Preserve prior source repositories and their hashes unchanged.
- Stop before any model request.

No need to ask again about the bounded evaluator implementation authorized here. A proposed response-schema or prompt change is outside this authorization; document it rather than making it.

## Deliverables

In the isolated evaluator-extension repository:

- evaluator-extension-plan.md.
- representability-review.md, covering all 24 and detailed witnesses for questioned cases.
- corpus manifest, exact sources, expected fixtures, and explicit deferred-case inventory.
- Extension code and unchanged legacy sources with provenance.
- Synthetic witnesses, negative mutations, and focused test evidence.
- Historical-reproduction evidence.
- Deterministic synthetic-results.json and a concise human-readable test report.
- Hash inventories and experiment-manifest.json.
- Freeze receipt.
- evaluator-extension-final-handoff.md.

Do not copy a final handoff into ChatMap until Ray reviews the result.

## Completion response

Return a concise report with:

- Source commits and verification.
- Exact changed tool files and interfaces.
- Prompt/schema preservation.
- Representation and scorer conclusions for A04/A06/A09/A16/A21/A24 and any additional blockers.
- Ready and deferred coverage out of 24, with reasons.
- Synthetic positive/negative and structural test counts.
- Historical reproduction and repeatability.
- Specification commit and full executable freeze SHA, or an explicit reason no freeze was possible.
- Local paths to the report and final handoff.
- Confirmation that no model was contacted and ChatMap remained untouched.

The next decision after this task is whether the tested, frozen evaluator and ready corpus justify running the selected models. Do not run those models in this assignment.

