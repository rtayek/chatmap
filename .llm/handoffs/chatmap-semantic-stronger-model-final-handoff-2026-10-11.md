# CM-EXP-SEMANTIC-04 stronger-model final handoff

Status: complete. Accepted for recording in ChatMap on 2026-10-11.

## What changed

Only the model: baseline `qwen2.5:7b` -> comparison `qwen2.5:14b`, selected via
the existing `run-v3.py --model qwen2.5:14b --tag qwen2.5-14b` argument. No
prompt, fixture, scorer, warning checker, checker test, request setting, or
acceptance rule was edited.

## What stayed frozen (SHA-256 verified)

27 inputs: `prompt-v3.txt`, `worked-examples-v3.json`, `cases/S01..S10.json`,
`legacy-scorer/cases/S01..S10.json`, `run-v3.py`, `score-v3.py`,
`warning-checker-v3.py`, `test-warning-checker-v3.py`,
`legacy-scorer/score-v2.py`. Verified byte-for-byte against
`frozen-input-hashes.txt` at the repo root and in the `stronger-model/` copies.
Request settings frozen: stream false, format json, temperature 0.0, seed 0,
num_predict 1024, 3 runs per case.

## Model identity and digest

- `qwen2.5:14b`, Ollama 0.32.15.
- config digest
  `sha256:db59b814cab753a51167db007fa6b6e0095f678ff24c9f7284753b75b34c6df3`.
- model-layer digest
  `sha256:2049f5674b1e92b4464e5729975c9689fcfbf0b0e4443ccf10b5339f370f9a54`
  (8,988,110,688 bytes).
- qwen2, 14.8B, context length 32768, embedding 5120, quantization Q4_K_M.

## Scored results

- revised scorer: 10/10 cases, 30/30 passing runs, all stable.
- legacy scorer: identical (10/10, 30/30).
- warning checker: 0 warnings (baseline 7b: 6).
- JSON parse: 30/30 lenient (frozen scorer); 27/30 strict (S09 x3 duplicate key).
- baseline reproduction before execution: 8/10, 24/30, manual 7/10, 6 warnings,
  7/7 checker tests, all byte-for-byte.

### Strict-JSON structural recheck (follow-up, no model contacted)

Separate contracts, from `strict-json-recheck.py` over the existing 30
responses (frozen scorer outputs hash-verified unchanged before/after):

- structural validity (strict, no duplicate keys): 27/30 runs, 9/10 cases
  (only S09 run1/2/3 fail: `duplicate key: facts`);
- semantic acceptance (frozen lenient scorer, unchanged): 30/30 runs,
  10/10 cases.

Outputs: `strict-json-validity.json`, `strict-json-validity.csv`.

## Manual results

- 10/10 cases accepted.
- Target cases corrected: S04 (`them` -> `the workers`), S07 (decision and
  rejection in proper categories), S10 (both disputed attributions plus the
  no-verification fact).
- No semantic regression or fabrication in the seven previously accepted cases.

## Limitations

- Single model, single 10-case corpus, deterministic (temp 0, seed 0): not a
  statistical result.
- Structural caveat: S09 emits duplicate `facts` keys in all three runs. The
  frozen lenient scorer (last-key-wins) passes it; a strict JSON validator
  rejects S09's three runs. Meaning is correct; well-formedness is not. This is
  a regression versus `qwen2.5:7b`, which produced well-formed S09 output.
- Mixed CPU/GPU execution; 324.3 s total, mean 10.8 s/response. Attempt 1 failed
  at cold model load (S01 run1 HTTP 500), was discarded, and attempt 2 ran clean
  after pre-warming.

## Classification

Strong semantic improvement (10/10 under frozen scoring and manual acceptance,
all three target cases corrected, no semantic regression) with one documented
structural caveat (S09 duplicate `facts` key). A strict "no regression of any
kind" reading downgrades the clean Strong-improvement label on account of S09.

## Recommendation

Do not integrate semantic extraction into production. The strict-JSON recheck
recommended by this experiment has been performed: well-formedness (27/30,
9/10) and semantic acceptance (30/30, 10/10) are now reported as separate
contracts, the gap being entirely S09's duplicate `facts` key. Next bounded
step: decide whether to enforce a strict output contract (reject duplicate
keys) and/or test a larger or more adversarial corpus. Production integration
remains out of scope.

## Evidence locations

Experiment repo:
`C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-stronger-model-2026-10-09\`

- `stronger-model/preflight-and-invocation.md` - preflight, isolation, metadata.
- `stronger-model/execution-notes.md` - attempt 1 failure and discard.
- `stronger-model/input-hash-inventory.txt` - frozen input/tool hashes.
- `stronger-model/raw-v3/` - 30 raw `qwen2.5-14b` responses plus meta and runlog.
- `stronger-model/scores-revised.json` / `.csv` - revised scorer output.
- `stronger-model/legacy-scorer/scores-v2.json` / `.csv` - legacy scorer output.
- `stronger-model/warnings-v3.json` - warning checker output (0 warnings).
- `stronger-model/strict-json-recheck.py` - strict duplicate-key validator.
- `stronger-model/strict-json-validity.json` / `.csv` - structural versus
  semantic totals (27/30 strict-valid runs; 30/30 semantic).
- `stronger-model/checker-test-results.txt` - 7/7 checker tests.
- `stronger-model/manual-acceptance.md` - per-case manual record.
- `stronger-model/comparison-and-decision.md` - cross-experiment comparison.
- `stronger-model/semantic-stronger-model-report.md` - full report.
- `stronger-model/result-hash-inventory.txt` - hashes of produced artifacts.

Prior evidence (unmodified):
`C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-3-2026-10-07\`
at result commit `9f95f57dd5cc0a47c2619db05d65ea231bb829fb`.

Experiment commits:

- pre-execution: `79c45fd`;
- model result: `bd38135`;
- strict-JSON recheck: `c77eede`.

## ChatMap boundary

ChatMap production code, schema, database, UI, A2A, worker-lifecycle paths, and
`.chatmap-local/` were untouched. No ChatMap repository file was written for
this experiment. The unrelated untracked `shiploop.jpeg` predated this work.
