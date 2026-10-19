# ChatMap bounded evaluator v2 handoff

Date: 2026-10-18 (America/Los_Angeles)
Status: Ray authorizes specification, implementation, testing, freeze, and retrospective rescoring in an isolated external repository.
No new model calls. No ChatMap production changes. No push.

## Assignment

Create a corrected version of the external adversarial semantic evaluator. Fix the three defects established by the audit:

1. Numeric substring matching can accept 5000 for 500 and 120 for 12.
2. The A16 ambiguity predicate can accept a definite actor.
3. Antecedent-provenance expectations conflict with the model-facing prompt.

Specify the policy before implementation, implement the smallest deterministic correction, test the audit counterexamples and conforming controls, freeze v2, and rescore the existing 144 responses. Preserve and report v1 and v2 separately.

This is retrospective tool validation. It does not establish prospective model reliability or authorize production integration. A fresh independent corpus is a later task, not part of this assignment.

## Authorization and isolation

You may create an independent sibling repository, copy required evidence, modify copied external evaluator code and v2 fixtures, add tests and reports, and make local commits there. Use the existing runtime and standard library. Do not introduce dependencies or build a general semantic framework.

Suggested destination:
C:/Users/ray/eclipse-workspace/chatmap-semantic-evaluator-v2-2026-10-18/

If it exists, inspect before continuing; never overwrite or delete earlier work. Follow applicable AGENTS.md instructions.

Keep all prior repositories, raw responses, original fixtures, frozen evaluators, historical scores, prompt bytes, response schema, and ChatMap files unchanged. Do not read .chatmap-local/. Do not touch existing untracked chan.pdf or shiploop.jpeg. Do not clean Windows Temp as part of this task.

Do not contact Ollama or any other model endpoint. No model may grade model responses. A CLI agent may implement deterministic scoring, inspect code, and document candidate semantic qualifications, but must not substitute its own LLM judgments for acceptance results.

## Required sources

Read the audit first:
C:/Users/ray/eclipse-workspace/chatmap-semantic-adversarial-evaluator-audit-2026-10-14/

Audit content commit:
a8b369542fd84ae1a4d1c1097f3913b6e8b0243e

Audit receipt commit:
0662b567c0065592b581f512583dc61033b07cc1

Directly relevant audit files:
- evaluator-audit-final-handoff.md
- evaluator-audit-report.md
- requirement-traceability.csv
- human-review-candidates.md
- byte-stability-report.md and underlying machine-readable census
- independent synthetic tests/results, inventories, and commit receipt

Evaluator source:
C:/Users/ray/eclipse-workspace/chatmap-semantic-evaluator-extension-2026-10-12/

Executable freeze:
b29afc803d5b403a312bfd356126209c7ca0bdee

Reported later receipt commit e0009b4 is not the evaluator freeze. Resolve and verify locally.

Preserved run:
C:/Users/ray/eclipse-workspace/chatmap-semantic-adversarial-model-run-2026-10-12/

Reported preflight c8acfa9, result 1a5e71f, receipt a9a9835. Resolve full SHAs and verify roles.

Read the relevant score/evaluate scripts, exact prompt/schema and gate validators, manifests, fixtures, frozen tests, comparison report, lexical qualifications, results-A.json, coverage.json, and raw-A/ artifacts. Do not recursively load unrelated handoffs.

The supplied audit reports 326 checks, reproducible scores, and traceability over 87 obligations. Verify from source; do not treat the summary as a substitute for inspection.

## Phase 1: establish the baseline

1. Verify source commits, cleanliness, and hash inventories.
2. Copy required tools/evidence and record exact source path, revision, and byte hash.
3. Reproduce v1 scores without modifying originals. Run deterministic reproduction twice.
4. Verify all 144 model/case/run slots and their content hashes.
5. Confirm audit counterexamples demonstrate the three reported defects.
6. Verify actual byte-stability groups from the audit census. Preserve any corrected reporting as an erratum, never a rewritten original report.
7. Write evaluator-v2-plan.md and evaluator-v2-contract.md, then commit these before modifying executable code.

Reported v1 reference totals:

| Gate | 7B runs/cases | 14B runs/cases |
|---|---|---|
| Strict JSON | 72/72, 24/24 | 72/72, 24/24 |
| Schema | 72/72, 24/24 | 69/72, 23/24 |
| Semantic | 22/72, 7/24 | 19/72, 6/24 |
| Full acceptance | 22/72, 7/24 | 19/72, 6/24 |

If reproduction fails, report the discrepancy and stop dependent rescoring. Continue nondependent inspection if useful.

## Phase 2: specify v2 policy

### Numeric values and units

Replace substring acceptance with fixture-declared numeric constraints that preserve value, unit, associated entity/relation, and scope. Merely checking word boundaries is insufficient if an item contains the expected number in an unrelated clause.

Declare supported lexical forms and normalization before rescoring: integers, decimal notation, signs, digit grouping, spelled-out numbers, and units as applicable to the bounded fixtures. Unsupported forms must be diagnosed, not guessed. Use exact decimal/rational comparison where needed, not floating-point tolerance invented after output inspection.

Reject 5000/500, 120/12, 1500/500, and 500.5/500 substitutions; distinguish 500 MB from 500 MiB and a per-worker limit from a global limit. Do not convert units unless an explicit preregistered policy permits it.

Do not invent a full natural-language number parser. Implement only the justified bounded grammar and disclose its limits.

### Genuine ambiguity

Define the acceptable A16 representations in existing schema fields. Preserve the unresolved approver identity; neither Rowan nor Ellis, nor both together, may be asserted as the definite approver.

Mentioning an ambiguity word somewhere must not make a definite actor pass. A statement naming both people as definite joint approvers also fails. Distinguish uncertainty over actor identity from uncertainty over whether the approval happened: the source explicitly states approval.

Require correct checklist object, supporting provenance under the declared v2 policy, and appropriate category/polarity/certainty. Do not add a schema field, new enum, or marker-token hint to the extraction prompt.

Use bounded explicit equivalence rules with paired negative tests. If the predicate cannot enforce the distinction reliably, mark A16 unresolved/not evaluable rather than claiming support. Do not hide loss of coverage.

### Provenance aligned with the actual prompt

Quote the exact model-facing citation rule in the v2 contract. Compare it with original corpus expectations and audit findings.

Default policy: require appropriate supplied source IDs for each extracted assertion. Do not universally demand an additional antecedent sentence ID when the cited assertion sentence supports the claim in the supplied passage context and the prompt never required exhaustive antecedent citation.

Specify per-item acceptable supporting citation sets before rescoring. Record whether:
- the direct assertion sentence suffices under the prompt;
- multiple sentences are necessary because the output combines distinct assertions;
- an antecedent citation is helpful context but not required;
- an irrelevant or invalid source ID causes rejection.

Keep wrong-entity/coreference errors separate from citation-policy errors. Do not accept citing all sentences as an automatic substitute for appropriate evidence. Preserve explicit no-verification statements and attributed conflicting reports.

The original specification intentionally imposed stronger antecedent requirements. Changing those requirements is an explicit v2 policy correction, not proof that v1 misimplemented its own fixtures. Report that distinction.

Do not silently change expected source meaning, polarity, certainty, status categories, or required quantities to improve totals.

### Other audit concerns and scope

Review the audit's A14/A21 grouping and facts-versus-status findings. Fix only directly necessary interactions with these three authorized corrections. For example, do not alter grouping rules merely to accept a particular observed answer.

Document other discovered issues as remaining limitations. If they prevent an honest result, report the blocker or reduced evaluable coverage. Do not expand into broad schema redesign or prompt refinement.

Retain strict JSON and schema gates unchanged. Preserve manifest discovery and fixed denominators. Version the semantic origin and each changed fixture policy.

## Phase 3: implement and test before rescoring

Implement v2 as distinct files or an explicitly versioned copy. Preserve v1 runnable and byte-identical.

Required evidence:

- All relevant frozen structural tests continue to pass.
- Audit counterexamples now fail for the intended reason.
- Correct numeric forms, units, attribution, and ambiguity controls pass.
- Numbers embedded in other numbers or unrelated clauses cannot satisfy the target obligation.
- A definite actor plus the word ambiguous still fails.
- Joint definite approvers fail A16.
- Legitimate direct-assertion citation controls pass under v2 policy.
- Wrong or nonexistent IDs, irrelevant citations, missing combined assertions, and wrong antecedents fail as specified.
- Existing omissions, reversals, certainty upgrades, category errors, unsupported facts/causality, and duplicate-key safeguards remain effective.
- One-to-one matching does not credit one vague item for multiple obligations.
- Unknown cases, empty corpora, duplicate IDs, missing runs, and case_id mismatches remain visible errors or explicit dispositions.
- Tests and scoring never rewrite evidence.
- Two test/report runs are byte-identical.

Use synthetic tests with written intended verdicts. Include unseen variations beyond the exact audit strings; do not patch individual examples with special cases. Record remaining finite lexical limitations.

If v1 tests encode the superseded provenance policy, retain them as historical v1 tests and add explicitly versioned v2 expectations. Do not delete contradictory tests to manufacture a green suite.

## Phase 4: freeze v2

Before applying v2 to preserved model responses:

1. Freeze the v2 contract, code, fixture-policy changes, tests, manifest, and reporting definitions.
2. Hash all inputs with deterministic relative-path ordering.
3. Commit the complete v2 evaluator and record its full SHA in an external receipt or later metadata-only commit.
4. Verify a clean tree and unchanged source/prompt/schema/raw hashes.

Be candid: audit authors and this specification have already seen summaries and counterexamples. This freeze precedes retrospective rescoring, not first exposure to the dataset. Do not call it a blind or prospective validation.

After freezing, do not adjust code or acceptable variants in response to scores. A discovered defect requires preserving v2's run and proposing another explicit version.

## Phase 5: retrospective rescoring

Rescore the same 144 preserved response slots with frozen v2. No model requests, retries, output repair, altered model content, or substituted responses.

Report v1 and v2 side by side with:

- Strict JSON, schema, semantic, and full-acceptance runs out of 72 and cases out of 24 per model.
- Not-evaluated/unscorable counts; schema failure never becomes full acceptance.
- Exact per-run transitions: fail→pass, pass→fail, unchanged, and unresolved.
- Item-level reason and governing policy change for every transition.
- Separate counts for numeric/ambiguity implementation repairs and provenance-policy changes, including overlaps.
- Exact evaluable coverage if any case is unresolved; retain the planned denominators.
- Residual lexical, grouping, and category limitations.
- Repeated scoring byte identity and final frozen-input hash verification.

Structural totals must remain unchanged. If they change, investigate a regression or baseline discrepancy before concluding.

Do not label higher v2 acceptance as improved model performance: the model outputs are unchanged. This measures the effect of evaluator correction. Do not claim a reliable model ranking from this retrospective exercise.

## Deliverables and stopping point

Commit in the new isolated repository:

- evaluator-v2-plan.md and evaluator-v2-contract.md
- Source provenance and baseline reproduction
- Versioned code, fixture-policy diff, and synthetic tests
- Test results and hash inventories
- Full evaluator-v2 freeze SHA and receipt
- v1 and v2 machine-readable scores
- score-transitions.json/csv with all changed verdicts explained
- evaluator-v2-retrospective-report.md
- evaluator-v2-final-handoff.md

Conclude whether v2 is ready for a separately designed fresh-corpus validation, ready with specified qualifications, or blocked. Recommend the smallest next step without creating or running another model corpus here.

No handoff is to be copied into ChatMap pending Ray's review. No push.

## Completion response to Ray

Lead with v2 readiness and a compact v1/v2 totals table. Include:
- Which defects and policy mismatches were corrected.
- Focused-test counts and remaining limitations.
- Every changed case/run verdict and why, in the linked report.
- Unchanged prompt/schema/raw evidence verification.
- Full source, specification, v2 freeze, and result commit SHAs.
- Local report and final-handoff paths.
- Confirmation that no model endpoint was contacted, no prior frozen evidence changed, ChatMap remained untouched, and nothing was pushed.

Do not claim implementation, test results, or corrections until actually completed locally.

