# ChatMap adversarial evaluator audit handoff

Date: 2026-10-14 (America/Los_Angeles)
Status: Ray authorizes this isolated audit.
Scope: no model requests, no changes to frozen evidence or ChatMap.

## Assignment

Audit the CM-EXP-SEMANTIC-06 evaluator, expected fixtures, and preserved comparison evidence before using its totals to rank models. Verify reproducibility and reporting, distinguish policy requirements from source meaning, and test whether the scorer detects the distinctions it claims to detect.

Use a fresh audit session where practical. This is an independently reasoned audit, not another run of the original author's summary. Disclose if the auditor also authored the evaluator or fixtures. Do not call it independent merely because its repository is separate.

No LLM may grade or certify model responses. The CLI agent may inspect code, trace deterministic outcomes, test synthetic examples, and identify candidate semantic qualifications with exact evidence. Human adjudication of disputed meanings remains separate. No Ollama or other model endpoint is to be contacted.

## Authorization and boundaries

Create an independent audit repository and ordinary copies of required evidence. You may write audit scripts, synthetic tests, reports, and local commits there. Preserve all source repositories, frozen prompt/schema/scorer/fixtures, raw responses, and historical outputs unchanged.

Do not implement a revised production or experiment scorer, rewrite original results, rerun models, tune prompts, alter ChatMap, read .chatmap-local/, or push. A proposed evaluator correction is a deliverable, not authorization to apply it.

Suggested audit repository:
C:/Users/ray/eclipse-workspace/chatmap-semantic-adversarial-evaluator-audit-2026-10-14/

If it already exists, verify its purpose and continue without destroying earlier evidence. Otherwise use a nonconflicting sibling. Follow applicable AGENTS.md instructions.

## Sources to verify

Evaluator repository:
C:/Users/ray/eclipse-workspace/chatmap-semantic-evaluator-extension-2026-10-12/

Evaluator freeze:
b29afc803d5b403a312bfd356126209c7ca0bdee

Its later receipt commit is reported as e0009b4. Resolve the full SHA locally and verify that it is metadata-only; do not confuse it with the executable freeze.

Run repository:
C:/Users/ray/eclipse-workspace/chatmap-semantic-adversarial-model-run-2026-10-12/

Reported commits:
- Execution preflight: c8acfa9.
- Result: 1a5e71f.
- Later receipt: a9a9835.

Resolve abbreviations to full SHAs and verify actual commit roles.

Read the directly named comparison report, lexical-qualifications.md, final handoff, execution plan/manifest/receipts/hash inventories, corpus-A.json, inputs-A/, cases-A/, witnesses/, score-ext.py, evaluate-ext.py, unchanged gate validators, tests/builders, results-A.json, coverage.json, and raw-A/ evidence needed for this audit. No recursive reading of unrelated ChatMap history.

Reported environment: Ollama 0.32.15; qwen2.5:7b digest prefix 845dbda0ea48; qwen2.5:14b prefix 7cdf5a0187d5; temperature 0, seed 0, num_predict 1024, format json. Verify from recorded metadata rather than contacting services.

## Phase 1: preservation and reproduction

1. Verify source revisions, cleanliness, inventories, and hashes. Record provenance for every copied artifact.
2. Reproduce frozen scoring from copies twice; verify byte-identical outputs and totals.
3. Verify all 144 planned slots have distinct requests, envelopes, extracted content, and metadata. Independently check status claims: no missing, empty, truncated, failed, or indeterminate slots.
4. Confirm expected fixtures, witnesses, and marker-token hints were absent from model requests. Check exact prompt placement and options.
5. Confirm gate ordering and not-evaluated accounting. The three 14B A19 schema failures must not count as semantic passes.
6. Inspect model-order and environment records; record any limitations without inventing causes.
7. Re-run existing evaluator tests once and record actual results.
8. Commit audit-plan.md and baseline reproduction evidence before proposing or testing alternative fixture policies.

Reported baseline:

| Gate | 7B runs/cases | 14B runs/cases |
|---|---|---|
| Strict JSON | 72/72, 24/24 | 72/72, 24/24 |
| Schema | 72/72, 24/24 | 69/72, 23/24 |
| Semantic | 22/72, 7/24 | 19/72, 6/24 |
| Full acceptance | 22/72, 7/24 | 19/72, 6/24 |

If reproduction fails, report the exact discrepancy and stop any downstream numerical comparison based on unreproduced scores. Continue nondependent inspection where useful.

## Phase 2: independent byte-stability census

Hash the exact extracted content bytes for each of the three runs in every model/case group. Report all 48 groups, unique-hash counts, and exact differing run IDs. Compare content bytes, not envelope timestamps or metadata. Verify save/extraction fidelity and record the byte basis.

The supplied completion report claims 22/24 byte-identical groups per model but names only A07 for 7B and A17 for 14B. Determine the actual counts and every varying case from the files. Do not assume the named cases or count is correct.

Distinguish byte instability, score instability, and semantic-content difference. Equal scores alone establish neither byte identity nor equal meaning. Do not ascribe variation to temperature without evidence.

## Phase 3: requirement traceability

For every A-case required item and prohibition, map:

- Exact source sentence(s).
- Original corpus expectation.
- Exact frozen prompt/schema rule, if any.
- Fixture encoding and scorer predicate.
- Claimed distinction and synthetic positive/negative evidence.
- Whether a passing match actually enforces that distinction.

Classify discrepancies as source-meaning obligation, explicit provenance/representation policy, undocumented evaluator constraint, missing evaluator coverage, or unresolved human judgment. These classes may coexist.

Do not automatically call an extra antecedent citation rule a bug: the original specification deliberately requires it. Determine whether the model-facing prompt communicates that rule and whether reports wrongly describe a provenance-policy rejection as semantic misunderstanding.

## Phase 4: focused concerns

### Provenance

Inspect A12–A15, A19–A21, and A24 and all similar fixtures:

- Exact-set versus minimum-support-set matching.
- Antecedent/combined citations versus a direct assertion citation.
- Legitimate single-sentence support versus missing referent evidence.
- Extra irrelevant IDs and the risk of citing every sentence.
- Whether the same provenance rule is applied to both models.

Separate correct meaning with policy-deficient citations from lost or fabricated meaning. Any proposal to accept alternative citation sets changes policy; label it explicitly rather than treating the historical policy as nonexistent.

### Categories and grouping

Inspect A05/A10–A13/A24: decisions, proposals, rejections, unresolved questions, historical facts, and implemented behavior. Verify category matching does not reward flattening all entries into facts.

For A14/A21, trace whether one-to-one matching unnecessarily rejects a faithful grouping or allows one vague item to satisfy multiple obligations. A02 may legitimately require two opposite-polarity facts under the existing schema; no combined alternative is required merely to improve its score.

### Ambiguity and temporal scope

Inspect A04/A06/A09/A16/A24:

- Does scoring enforce uncertainty about the actor and prevent selecting a named actor as certain?
- Must outputs contain arbitrary marker words absent from prompt instructions?
- Can historical/current/corrected status be lost while enough marker tokens remain?
- Do dates refer to the reported event or reporting time correctly?
- Are substantively equivalent representations rejected solely for wording?

### Other defects

Inspect A08 agreement/disagreement and source certainty, A18/A19 cause/object representation, and A23 literal payload versus duplicate-key rejection reason. Confirm alleged model errors using exact source/output pairs and code traces; candidate semantic judgments remain qualifications for human review.

## Phase 5: adversarial tests of the evaluator

Author deterministic synthetic counterexamples independent of the original builders. Include meaningful distortions that retain expected keywords, numbers with similar text, negated temporal markers, wrong entities, irrelevant provenance, arbitrary ambiguity tokens followed by a definite actor, flattened categories, and null objects.

Also test independently authored schema-valid equivalent paraphrases or groupings against documented equivalence rules. Expected synthetic verdicts must have a written rationale before execution. Ambiguous synthetic expectations remain unresolved, not automatic successes.

For each test record construction, intended distinction, schema result, actual frozen score, exact match trace, and whether the failure reflects evaluator implementation, finite lexical coverage, or explicit policy.

A scorer falsely accepting an incorrect synthetic example is more consequential than simply rejecting a paraphrase. Do not silently weaken rejection safeguards to improve model totals.

Synthetic tests demonstrate predicate behavior; they are not an LLM grading procedure over model outputs.

## Phase 6: conclusions without retrospective tuning

Preserve the original measured totals.

Produce a failure-driver inventory from deterministic diagnostics, allowing overlapping causes: structure/schema, provenance policy, lexical mismatch, category mismatch, missing expected item, unsupported item, and unresolved semantic issue. Do not present diagnostic categories as mutually exclusive if they are not.

Identify concrete evaluator defects and candidate improvements, with risks and tests. Do not implement new acceptance rules or calculate “corrected semantic accuracy” through LLM judgments.

If useful, perform a clearly labeled diagnostic ablation using an external audit wrapper, such as isolating the contribution of provenance predicates, only after committing its exact definition. No frozen-file changes. Such counts are post hoc sensitivity measurements, not revised acceptance, true semantic accuracy, or a new model ranking.

Any future corrected evaluator requires a separate version, specification, tests, and freeze. Re-scoring these preserved responses would be retrospective evaluation, not prospective validation. A later independent corpus would be needed to test transfer.

Conclude with one verdict:
- Verified for its explicitly bounded purpose.
- Verified with qualifications; unsuitable for model ranking until specified issues are resolved.
- Not verified due to reproducibility or evaluator defects.

Choose the verdict from evidence. Neither passing synthetic tests nor strict JSON success proves general semantic reliability.

## Deliverables and completion

Commit only in the audit repository:

- audit-plan.md and source-provenance.json.
- baseline-reproduction.md and copied-output hash evidence.
- byte-stability.json and byte-stability-report.md.
- requirement-traceability.csv.
- Independent synthetic tests and deterministic results.
- failure-driver-inventory.json.
- evaluator-audit-report.md.
- human-review-candidates.md with exact source/output pairs and candidate qualifications.
- evaluator-audit-final-handoff.md.
- Hash inventory and audit commit receipt.

Lead the completion response with the verdict, reproduced totals, actual stability counts and varying cases, key evaluator findings, and one next step. Distinguish verified code behavior from unresolved semantic judgments. Include full source/audit SHAs and evidence paths.

Confirm no model endpoint was contacted, no frozen evidence changed, and ChatMap remained untouched. Do not copy the handoff into ChatMap or push pending Ray's review.

