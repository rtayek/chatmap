# ChatMap fresh 12-case corpus preparation handoff

Date: 2026-10-19 (America/Los_Angeles)
Status: source passages and intended meanings authored; local fixture encoding and freeze pending.
Authorization: prepare, test compatibility, and freeze a new corpus against unchanged evaluator v2. No model requests.

## Goal and independence disclosure

Prepare twelve new cases B01–B12 to test transfer beyond the A-corpus used to repair evaluator v2. Cover exact numeric values/units, ambiguous versus identifiable actors, appropriate citations, and decisions/status. Include independent synthetic paraphrase and grouping controls to expose the remaining lexical limitations.

These passages were authored in the same ChatGPT conversation that proposed the A-corpus and evaluator repairs. They are newly written and have no model responses yet, but they are not independently authored or blind. Disclose that history. Have a fresh local session review their meaning and compatibility where practical; that review alone does not make this an independently authored benchmark.

Call this a fresh transfer corpus. Do not claim independent or blind validation. A stronger independence claim would require another author/reviewer and documented separation.

## Frozen evaluator and source evidence

Evaluator v2:
C:/Users/ray/eclipse-workspace/chatmap-semantic-evaluator-v2-2026-10-18/

Exact v2 freeze:
35019ac0624f162320bc43d7d0f2658b4378fac8

Reported result:
f82b9a207fd572973cc85568cc114086d2df5882

Reported specification/baseline:
c9181a92ddbda2da4b5b9592491c800138593c36

Reported receipt:
60f96a8b90f9c962d2ab229e8959b40ba8164dfa

Verify each locally. Read evaluator-v2-final-handoff.md, evaluator-v2-contract.md, evaluator-v2-plan.md, the versioned scorer/aggregator, exact prompt/schema, fixture format, tests, and directly needed manifests/hash inventories. Do not assume filenames or interfaces not present in the repository.

Reported v2 retrospective acceptance is 31/72 runs and 10/24 cases for 7B, 25/72 and 8/24 for 14B. The fifteen verdict changes were citation-policy changes; numerical and ambiguity repairs changed no preserved model verdict. These are background, not targets for the new cases.

## Isolation and permissions

Create an independent sibling:
C:/Users/ray/eclipse-workspace/chatmap-semantic-fresh-corpus-12-2026-10-19/

Inspect an existing destination before reusing it. Never overwrite unrelated work. Follow applicable AGENTS.md instructions.

You may create the new manifest, exact input fixtures, expected fixtures, synthetic controls, compatibility tests, reports, inventories, and local commits. Read prior evidence or use ordinary copies only as needed. Keep the v2 evaluator, prompt, schema, original fixtures/results/raw responses, and all prior repositories unchanged.

Do not modify executable scoring rules to accommodate this corpus. Do not add synonyms or marker hints to the extraction prompt. Do not contact Ollama or another model. Do not create production integration, change ChatMap, read .chatmap-local/, clean unrelated temporary files, or push.

## Source and expectation conventions

Only the exact source passage and case ID may be supplied to a future model through the established runner interface. Never include headings, expectations, citation commentary, synthetic controls, or evaluator markers.

Sentence IDs are case-local. Source passages below are authoritative bytes after UTF-8/LF encoding, excluding Markdown fences.

Expected meanings are schema-neutral obligations, not invented response fields. Encode them using the unchanged response schema and supported v2 fixture interface. Equivalent split or combined representations are intended to be acceptable when they preserve every obligation and source support; whether the frozen scorer can recognize them is an explicit compatibility question.

Citations follow the actual v2 prompt-aligned rule. The lists below identify natural direct support; an exact allowed-set policy must be verified against v2 before encoding. Do not reintroduce mandatory exhaustive antecedent citations or silently accept irrelevant IDs. Required meaning and citation policy must be separately diagnosable.

All cases forbid unsupported facts, wrong numbers/units/entities, certainty upgrades, invented implementation, and lost negation. Do not derive totals or other facts as if stated in the source.

## The twelve new passages

### B01 — Exact counts

```text
[s1] The Lumen batch imported 48 records.
[s2] The Lumen batch skipped 6 records.
```

Required:
- Lumen batch imported exactly 48 records; certain, affirmed; direct support s1.
- Lumen batch skipped exactly 6 records; certain, affirmed; direct support s2.

Forbidden: 480 imported, 60 skipped, zero skipped, or an inferred total of 54 presented as a source assertion.
Controls: wrong-number mutations; correct wording with the same values; wrong value next to an unrelated correct number.

### B02 — Per-process limit and unit

```text
[s1] The experiment started three sandbox processes.
[s2] Each process had a memory limit of 640 MB.
```

Required:
- Three sandbox processes started; certain, affirmed; s1.
- 640 MB memory limit per process, applying to those processes; certain, affirmed; direct assertion s2 in passage context.

Forbidden: 6400 MB, 640 MiB, a global 640 MB limit, or a derived aggregate limit presented as stated evidence.
Controls: faithful grouped versus split representation; MB/MiB and per-process/global substitutions.
Do not require an s1 citation on the second assertion merely because it identifies the antecedent unless v2's published policy actually requires it.

### B03 — Number words and decimal value

```text
[s1] The tray held seventeen samples.
[s2] Each sample weighed 2.5 g.
```

Required:
- Seventeen/17 samples in the tray; certain, affirmed; s1.
- Each sample weighs exactly 2.5 g; certain, affirmed; direct support s2 in context.

Forbidden: 170 samples, 25 g, 2.5 kg, or a computed total weight as if explicitly stated.
Controls: declared number-word equivalence, decimal substitution, sign change, and unit change.
If v2 lacks the number-word or decimal grammar needed for a valid equivalent, mark the coverage limitation. Do not extend its parser.

### B04 — Approval certain, actor unresolved

```text
[s1] Blair and Drew discussed the patch.
[s2] One of them approved the patch.
[s3] The record does not identify which person approved it.
```

Required:
- Blair and Drew discussed the patch; certain, affirmed; s1.
- Approval of the patch occurred, with the approver unresolved between Blair and Drew; s2 in context.
- The record does not identify the approver; certain, negated identification; s3.

Forbidden: Blair alone as certain approver, Drew alone as certain approver, both as definite joint approvers, or doubt that approval happened solely because actor identity is unresolved.
Controls: genuine ambiguous representation; definite actor with the word ambiguous elsewhere; definite joint actors.
Existing fields must preserve actor uncertainty without inventing an enum or schema field.

### B05 — Both event and actor uncertainty

```text
[s1] Erin and Jules were assigned to the release.
[s2] One of them may have signed the release.
[s3] No signature check has been performed.
```

Required:
- Erin and Jules assigned to the release; certain, affirmed; s1.
- Possible signature of the release by an unresolved one of Erin/Jules; uncertain event, unresolved actor; s2 in context.
- Signature check not performed; certain, negated; s3.

Forbidden: certain signature, a selected certain signer, both as certain signers, certain absence of a signature, or completed verification.
Controls: independent event-certainty and actor-identity mutations.
Representability does not imply scorer coverage; document both.

### B06 — Unambiguous actor control

```text
[s1] Priya reviewed the patch alone.
[s2] She rejected the patch.
```

Required:
- Priya reviewed the patch alone; certain, affirmed; s1.
- Priya's rejection of the patch in rejected_alternatives, with patch identity retained; direct support s2 in context.

Forbidden: unknown rejecter, joint review, accepted patch, or accepted implementation.
Controls: correct resolved actor versus an invented ambiguous actor; category inversion.
Check the frozen prompt's rejection-category semantics before encoding this witness. If an actual rejection event versus rejected alternative has incompatible treatment, record an expectation/contract conflict without forcing a score.

### B07 — Direct assertion citation after identification

```text
[s1] The project is named Northstar.
[s2] Northstar exports plain-text files.
```

Required:
- Project named Northstar; certain, affirmed; s1.
- Northstar exports plain-text files; certain, affirmed; s2.

Forbidden: mandatory s1+s2 for the export claim when s2 directly names Northstar; a different export format or project name.
Controls: s2-only export citation; irrelevant-only citation; invalid ID.
A combined item covering identity and export must preserve both meanings with corresponding support, rather than hiding an omission.

### B08 — Pronoun resolution without invented action

```text
[s1] The courier received the sealed parcel.
[s2] It logged the parcel without opening it.
```

Required:
- Courier received the sealed parcel; certain, affirmed; s1.
- Courier logged the parcel; certain, affirmed; s2 in context.
- Courier did not open the parcel during that logging action; certain, negated, with scope preserved; s2 in context.

Forbidden: parcel as logging actor, an opened parcel, a claim that it was never opened at any other time, or omission of the explicit no-opening qualification.
Controls: direct s2 citation; required-object preservation; negation and temporal-scope mutations.

### B09 — Combined support and explicit causation

```text
[s1] The intake stopped.
[s2] The intake stopped because the access token had expired.
```

Required:
- Intake stopped; certain, affirmed; s1 or s2 provides direct evidence.
- Expired access token caused the stop; certain, affirmed; s2 provides the explicit causal statement.

Forbidden: invented disk/network cause, merely co-occurring token expiry with the explicit cause omitted, or a renewed token.
Controls: s2 supporting both obligations; faithful split/combined representations; unsupported cause.
The duplicated stopping assertion should not force duplicate output items. Record whether one-to-one matching creates a scoring limitation.

### B10 — Decision without implementation

```text
[s1] We decided to enable encryption for backups.
[s2] Encryption has not yet been enabled for backups.
```

Required:
- Accepted decision to enable backup encryption in decisions; s1.
- Current backup encryption not enabled; certain, negated; s2.

Forbidden: implemented encryption, rejected encryption, or treating the current negative fact as cancellation of the decision.
Controls: decision/implementation separation; category flattening; polarity reversal.

### B11 — Rejected option and pending choice

```text
[s1] We rejected adopting the Vega parser.
[s2] We have not decided whether to retain the existing parser.
```

Required:
- Vega adoption in rejected_alternatives; s1.
- Whether to retain the existing parser in open_questions; s2.

Forbidden: accepted Vega adoption, accepted retention, rejected retention, or an invented replacement.
Controls: wrong category and decision status; original parser identity preserved without guessing its name.

### B12 — Superseded plan and postponed action

```text
[s1] The earlier plan scheduled the migration for Friday.
[s2] We replaced that plan with a decision to migrate on Monday.
[s3] We decided to postpone deletion of the old store until after migration verification.
[s4] Migration verification has not started.
```

Required:
- Historical/superseded Friday migration plan; s1.
- Accepted replacement Monday migration decision, with supersession; s2 in context.
- Accepted postponement of old-store deletion until after migration verification; s3.
- Verification not started; certain, negated; s4.

Forbidden: Friday as simultaneous current plan, completed migration, completed verification, deleted old store, or permanent rejection of deletion.
Controls: historical/current reversal; loss of after-verification condition; grouping that preserves versus drops statuses.
Do not invent calendar dates or assume Monday follows Friday within a particular week.

## Local preparation procedure

### 1. Verify frozen sources

Resolve revisions and verify hashes and clean state. Copy needed code read-only into the new repository. Record actual exact prompt/schema hashes. Reproduce the documented v2 tests without model calls. Do not read all old model outputs to design this corpus.

### 2. Commit intended meanings before fixture work

Commit this document and the extracted exact input passages before encoding expected fixtures. Record provenance and the same-conversation authorship limitation. Preserve the intended source meanings even if scorer coverage is poor.

### 3. Check fixture expressiveness and encode

Create an explicit B01–B12 manifest and machine-readable expected fixtures through v2's supported interface. No hardcoded B-prefix source edit is allowed.

For every required obligation, map source support, response category, participant/object, polarity, certainty, qualifiers, acceptable grouping, and matching predicate. Keep semantic intent distinct from supported matcher variants.

Encode equivalents only through existing data-driven options. New fixtures are allowed; changing code, adding hidden prompt instructions, or weakening expected meaning is not.

If numeric/ambiguity checks are hardcoded to A-cases or new fixtures require a code extension, document the exact function/interface limitation and stop those cases at the compatibility gate. Do not claim v2 transfer merely because bypassed predicates accept them.

### 4. Prepare synthetic controls

For each case:
- At least one schema-valid positive witness.
- At least one independently worded positive paraphrase.
- A focused negative for every required distinction.
- Citation controls using the actual v2 policy.

For B02/B07/B09/B12, include a faithful combined representation and faithful split representation where the schema permits both. Treat legitimate grouping rejected by the frozen scorer as a coverage limit, not a model error.

Synthetic responses are local test fixtures, not model output. Write expected test verdicts and reasoning before execution. Ambiguous expectations remain unresolved. Do not silently change them after tests fail.

### 5. Report coverage honestly

Produce a 12-row compatibility matrix:
- Schema representability.
- Supported v2 predicates.
- Canonical witness result.
- Paraphrase/grouping results.
- Negative-counterexample results.
- Provenance support.
- Ready, qualified, or blocked.
- Exact reason and code/fixture evidence.

A case is ready for model evaluation only if its required distinctions are checked and canonical controls pass with intended negatives rejected. A failed paraphrase/grouping control may qualify or block the case depending on whether correct outputs can be unfairly rejected; state the policy.

Do not suppress a case or force the number twelve to be executable. Preserve all twelve in the specification and report ready/qualified/blocked counts out of twelve.

If a required distinction is accepted incorrectly, block that case. If the scorer is limited to a narrow canonical form absent from the prompt, do not call canonical-witness success proof of fair semantic measurement.

### 6. Freeze compatible preparation and stop

Commit the corpus manifest, source/expected fixtures, control inputs/expectations/results, compatibility report, unchanged copied evaluator/prompt/schema, provenance, and deterministic input hashes.

Verify source passages match this document byte-for-byte after extraction. Run checks twice and verify deterministic machine-readable results. Recheck all frozen source hashes.

Record the full corpus preparation freeze SHA in an external receipt or later metadata-only commit; avoid self-referential hashes. Clearly state whether it is:
- A complete 12-case execution-ready freeze.
- A qualified/partial preparation freeze with explicit blocked cases.
- A blocker report with no executable corpus freeze.

Do not invoke models. Proposed future design is three repetitions for each ready case per selected model; actual run count, digests, and invocation plan belong to a later execution authorization.

## Required artifacts

- This preparation handoff.
- inputs-B/ with twelve exact passages.
- Explicit corpus manifest and supported expected fixtures.
- requirement-map.csv.
- Synthetic canonical/paraphrase/grouping and negative controls.
- compatibility-report.md and deterministic control-results.json.
- preparation-manifest.json with full source revisions and hashes.
- input-hash-inventory.txt and preparation freeze receipt.
- fresh-corpus-12-final-handoff.md.

Use existing naming conventions and interfaces where possible; document actual paths. Do not create unnecessary duplicate frameworks or generators.

## Completion response

Report actual ready/qualified/blocked coverage out of twelve, positive/negative control results, residual lexical/grouping limits, unchanged v2/prompt/schema verification, full commits, and local report/handoff paths.

State that source authorship was not blind or independent. Confirm no model was contacted, no previous frozen evidence or ChatMap was changed, and nothing was pushed.

Recommend only the smallest next step supported by compatibility results. Do not run a partial or complete corpus without a subsequent execution instruction.

