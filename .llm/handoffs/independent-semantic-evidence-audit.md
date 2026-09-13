# Independent Audit: ChatMap Semantic-Evaluation Evidence (CM-EXP-SEMANTIC-01)

- Auditor / runtime: Claude Code (Opus 4.8), acting as independent evidence auditor
- Date: 2026-09-13
- Source evidence (treated as immutable, not written to):
  `C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-2026-09-13\`
- Audit output directory:
  `C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-audit-2026-09-13\`
- Repository commit at audit time: `00a307fee5f97194e65568e781a22e99eb385719`
  (the experiment reports it examined `22a06e05e3332e6e0c65ef00213633d345e988c4`,
  which is an ancestor of the current HEAD; the two later commits recorded the
  experiment result and the item-6 verification, not experiment inputs).
- Discovery followed AGENTS.md -> .llm/index.md -> human/persona/working-context.
  `.chatmap-local/` was not read. The specification and completion handoff were
  both read. No repository file was edited, staged, committed, or pushed.
- No LLM was called during this audit. No model graded any response.

## Final verdict: VERIFIED WITH QUALIFICATIONS

The preserved files support the reported result: 10/10 format success (with one
disclosed, defensible qualification about empty-object handling), 7/10 semantic
success, and the REFINE recommendation. Two reproducibility qualifications apply
(the pre-relaxation scorer is not preserved; raw responses are stored with CRLF
line endings). Neither materially undermines the reported semantic result.

## Commands executed (abbreviated)

- `git rev-parse HEAD`
- `find . -type f`, `stat -c%s`, `sha256sum` over all evidence files
- `sha256sum prompt.txt`; Python reproduction of the corpus-input hash and the
  manifest `cases_files_concat` hash
- Strict `json.loads` of all 30 raw responses
- Per-case grouping of `prompt_sha256` from the 30 `*.meta.json`
- mtime comparison of `cases/` vs `raw/`
- Copied the entire evidence directory to `evidence-copy/` and ran
  `python score.py` there; `diff` and `sha256sum` of reproduced vs original
  `scores.json` / `scores.csv`
- Scan of all 30 raw responses for empty `object` fields; simulation of a strict
  non-empty-object rule to identify which cases would flip

## 1. Inventory and integrity

- Files present: 10 case fixtures (`cases/S01.json`..`S10.json`), 30 raw
  responses (`raw/S0x-qwen2.5-7b-run{1,2,3}.json`), 30 per-run
  `*.meta.json`, 1 `raw/_runlog-qwen2.5-7b.json`, plus `experiment-manifest.json`,
  `prompt.txt`, `scores.json`, `scores.csv`, `run.py`, `score.py`,
  `semantic-evaluation-report.md`. Full listing with sizes and SHA-256 in
  `audit-file-inventory.csv`.
- Counts: exactly 10 fixtures, exactly 30 raw responses (3 per case S01-S10).
  No missing, extra, or duplicate evidence files.
- Hash reproduction (see `audit-hashes.txt`):
  - `prompt.txt` SHA-256 = `cab901b8...5f7777` -- MATCHES manifest and handoff.
  - Corpus-input SHA-256 = `4c3af448...ac2c35` -- MATCHES manifest and handoff.
    Procedure was not written by any preserved script; it is the concatenation
    of `case_id + "\n" + input + "\n"` for S01..S10 in id order (the same case
    text `run.py` feeds the model). I reproduced it by that method and it
    matched exactly. Minor limitation: because the procedure is not codified in
    a preserved script, it was inferred, not read.
  - Manifest secondary hash `cases_files_concat_sha256` also reproduced exactly.
- Integrity qualification (raw line endings): every raw response is stored with
  CRLF, not the LF that Ollama emits (S01 is 317 bytes on disk vs 302 characters
  reported at run time; the 15-byte delta is one CR per line). This is
  platform text-mode translation by the writer. JSON content and semantics are
  unchanged and all 30 still parse, but the raw files are therefore not a
  byte-exact copy of the wire response. Classify: content-faithful, not
  byte-faithful.

## 2. Manifest and protocol

Classification of each claim by strength of evidence:

- Model = `qwen2.5:7b`: SUPPORTED BY LOGS. All 30 `*.meta.json` and the run log
  record `qwen2.5:7b`. The digest `845dbda0...` appears in the manifest only
  (an executor assertion); it is not repeated per run, so specific weights are
  not cryptographically bound to each response.
- Temperature 0 and seed 0: SUPPORTED BY LOGS. All 30 meta files record
  `options = {temperature: 0.0, seed: 0, num_predict: 1024}` (one distinct value
  set across all runs).
- JSON output mode requested: SUPPORTED BY SCRIPT. `run.py` sets
  `"format": "json"` in the request payload. It is not echoed in the per-run
  meta, so this rests on the driver source, not the logs.
- No conversational memory between cases: SUPPORTED BY SCRIPT. `run.py` issues
  independent `/api/generate` calls with a freshly built prompt and passes no
  context/session field.
- Same frozen instruction prompt for every run: DEMONSTRATED. The run log's
  `prompt_sha256` equals the `prompt.txt` hash, and each case has exactly one
  distinct full-prompt hash across its three runs (10 distinct full-prompt
  hashes total = one per case). The instruction prompt is constant; only the
  per-case input varies.
- Expected fixtures created before model execution: SUPPORTED BY TIMESTAMPS.
  The latest `cases/` mtime (11:16:37) precedes the earliest `raw/` mtime
  (11:18:56); all fixtures predate all responses. mtimes are mutable, so this is
  corroboration, not proof. The manifest also asserts it.
- No LLM participated in scoring: DEMONSTRATED. `score.py` imports only
  `csv, json, re, sys, pathlib`; there is no network/HTTP/socket/model call
  anywhere in the file.

## 3. Scorer review

- Deterministic and local: yes. Inputs are only `cases/*.json` and
  `raw/*.json`; outputs are `scores.json` / `scores.csv`. No randomness, no
  network, no model.
- Expected values live in the frozen fixtures, not the scorer. `score.py`
  reads `case["expected"]`, `case["prohibitions"]`, and
  `case["supplied_source_ids"]`. The only semantics hard-coded in the scorer are
  the enum vocabularies (`affirmed/negated`, `certain/uncertain/disputed`) and
  the four category names, which match the specification.
- Silent normalization/repair: `load_json_lenient` will strip a code fence or
  extract the first balanced `{...}` if strict parsing fails. This could in
  principle mask malformed output, but it was never exercised: all 30 responses
  parse under strict `json.loads`. Token matching runs on a normalized
  concatenation of an item's string fields (casefold; non-alphanumeric except
  hyphen -> space). This is generous but symmetric across cases.
- Coverage of required measures: invocation success, JSON parse, schema
  conformance, required-items/correct-items, omissions, reversals,
  certainty errors, unsupported inferences (unmatched model items plus
  prohibition hits, which include the S06 no-causal rule), provenance errors,
  and cross-run stability are all implemented. Note: `certainty_errors` counts
  any certainty mismatch (not only uncertain->certain upgrades), and a
  prohibition violation is bucketed under `unsupported_inferences`. Both are
  reasonable and, if anything, stricter than the spec; neither changes any
  pass/fail outcome.
- Pass criteria consistent: `case_pass` requires `format_pass` and
  `correct == required` and every error count zero -- exactly the specification's
  definition.
- Reproduction: running `score.py` in the isolated copy reproduced
  `scores.json` and `scores.csv` BYTE-FOR-BYTE (identical SHA-256):
  `scores.json` = `65b8de7e...`, `scores.csv` = `3e0ee108...`. Console output
  preserved in `score-reproduction-output.txt`; regenerated files preserved as
  `reproduced-scores.json` / `reproduced-scores.csv`.

## 4. Manual case audit

I inspected all ten fixtures and all thirty raw responses independently of
`score.py`. Because all three runs of every case are byte-identical (verified by
SHA-256), run 1 represents each case.

| Case | Property                 | JSON/schema | Manual verdict | Notes |
|------|--------------------------|-------------|----------------|-------|
| S01  | Identity                 | valid       | PASS  | project named ChatMap; affirmed/certain/s1 |
| S02  | Quantity                 | valid       | PASS  | "three read-only workers" preserved |
| S03  | Date                     | valid       | PASS  | kept surface form "September 12, 2026"; fixture accepts it |
| S04  | Negation                 | valid       | FAIL  | "did not launch" tagged polarity=affirmed -> reversal |
| S05  | Uncertainty              | valid       | PASS  | certainty=uncertain preserved |
| S06  | Chronology w/o causality | valid*      | PASS  | both facts correct, no causal link; empty object (see sec 5) |
| S07  | Decision vs rejected     | valid       | FAIL  | both emitted in facts[]; decisions/rejected arrays empty |
| S08  | Open question            | valid       | PASS  | correctly placed in open_questions |
| S09  | Supersession             | valid       | PASS  | "old plan" vs "current plan" distinguished (see caveat) |
| S10  | Conflicting reports      | valid       | FAIL  | reports tagged certain not disputed; no-verification put in open_questions |

`*` S06 schema validity depends on accepting an empty `object` string; see
section 5.

- S04 (special attention): CONFIRMED failure. The denial survives only as the
  free-text relation "did not launch"; the machine-readable `polarity` is
  `affirmed`. A downstream consumer reading `polarity` would wrongly conclude a
  launch occurred. Genuine model defect, not a scoring artifact.
- S07 (special attention): CONFIRMED failure. The decision and the rejected
  alternative are both in `facts`; the `decisions` and `rejected_alternatives`
  arrays are empty. The rejected item does carry `polarity: negated`, so the
  meaning is present in prose, but the status distinction the schema exists to
  capture is lost.
- S10 (special attention): CONFIRMED failure. Worker A's and Worker B's
  conflicting reports are `certainty: certain` rather than `disputed`, and "No
  independent verification was performed" is filed under `open_questions` rather
  than as a certain fact. The model did not pick a winner and kept attribution
  in the subject (both correct), but did not mark the conflict disputed and
  mis-categorized the verification statement.
- S09 caveat: the pass is legitimate but generous. The required schema has no
  supersession/temporal-status field, so "superseded" is detected only because
  the model happened to carry the adjectives "old" / "current" in the subject
  text. A production pipeline would need an explicit field; the report already
  states this.

Independent count: 7 passing (S01, S02, S03, S05, S06, S08, S09) and 3 failing
(S04, S07, S10). This MATCHES the reported result and the reproduced scores.

## 5. S06 post-hoc correction assessment

- What changed: only `validate_schema` in `score.py`. The final file requires
  `subject` and `relation` to be non-empty strings and requires `object` to be
  a string that MAY be empty (lines 101-108, with an explanatory comment). The
  disclosed prior behavior required all three of subject/relation/object to be
  non-empty.
- Was the pre-change scorer preserved? NO. The evidence directory is outside the
  Git repository and holds only the final `score.py`; there is no versioned or
  backup copy of the strict validator. Therefore the exact prior code cannot be
  independently reconstructed from preserved evidence -- it is known only from
  the handoff's disclosure and the in-code comment. I did not infer it from the
  final file beyond that disclosure.
- Did any expected fixture change? NO. The empty-object rule lives entirely in
  `score.py`; the fixtures encode only tokens, polarity, certainty, source_ids,
  and prohibitions. No `cases/*.json` file references object emptiness.
- General and consistent? YES. `validate_schema` runs uniformly on every fact in
  every case; S06 is not special-cased.
- Agrees with the written schema? YES. The specification and `prompt.txt`
  describe `object` as a string ("text") and never require it to be non-empty.
  Treating an empty `object` for an intransitive predicate ("Eclipse was
  closed") as conforming is consistent with the schema as written.
- Affects any case other than S06? NO. Empty `object` occurs only in S06 (both
  facts, all three runs). A simulated strict non-empty-object rule flips only
  S06.
- Should 10/10 format stand? STAND WITH QUALIFICATION. Under the relaxed and
  defensible rule, all 10 cases are format-valid. Under the stricter prior rule
  the count would be 9/10 (S06 format-fail). Because the relaxation is faithful
  to the literal schema, applied uniformly, leaves fixtures untouched, and was
  disclosed, the 10/10 headline is acceptable provided it is read as "10/10
  format-valid when an empty object string is treated as schema-conforming, a
  decision made after observing output." It does not touch the 7/10 semantic
  result: S06's two facts are semantically correct regardless of the
  object-emptiness rule.

## 6. Reported conclusions, evaluated separately

- Transport/structure reliable on this corpus: SUPPORTED. 30/30 valid JSON,
  correct enums, schema-conforming (modulo the disclosed empty-object rule).
- Stable across three repetitions: STRONGLY SUPPORTED. The three runs of every
  case are byte-identical, which is stronger than the semantic-signature
  equality the report claims.
- Semantic correctness 7/10: CONFIRMED by independent manual audit and by
  byte-identical score reproduction.
- No unsupported fact or causal relation introduced: SUPPORTED. No fabricated
  facts; S06 contains no causal link between closing Eclipse and the check
  passing.
- Failures cluster in machine-readable control fields: SUPPORTED. All three
  failures are field/category errors (polarity for negation, category placement
  for status, certainty for disputed attribution) on content the model otherwise
  captured.
- REFINE better supported than STOP or EXPAND: AGREE. Format is solved and
  content is captured, so STOP is unwarranted; three of ten fail on the
  highest-value properties, so EXPAND is premature. The failures are of a kind
  that targeted prompting or a deterministic post-parse check could plausibly
  address.
- Nothing here justifies production integration: AGREE. Ten hand-authored short
  cases on one local model cannot establish general semantic reliability.

## Limitations and unverified claims

- Corpus-hash procedure is not codified in any preserved script; reproduced by
  inference and confirmed by match (section 1).
- Raw responses are stored CRLF, not the LF Ollama emits; content-faithful but
  not byte-faithful to the wire (section 1).
- The pre-relaxation `score.py` is not preserved; the exact S06 code change
  cannot be independently reconstructed from evidence, only from disclosure
  (section 5).
- Model identity, temperature, seed, JSON mode, and no-memory rest on
  executor-generated logs and driver source, not cryptographic proof. Nothing
  contradicts them, but they are asserted/logged rather than provable.
- "Fixtures frozen before execution" rests on mutable mtimes plus the executor's
  assertion. Consistent with the claim; not provable.
- Absence of the above proofs is classified as unverified, not as evidence the
  claims are false.

## Bottom line

Reproduced totals: format 10/10 (9/10 under a strict non-empty-object rule);
semantic 7/10 (pass S01,S02,S03,S05,S06,S08,S09; fail S04,S07,S10);
scores reproduced byte-for-byte. The reported result stands. Verdict:
VERIFIED WITH QUALIFICATIONS.
