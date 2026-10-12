# CM-EXP-SEMANTIC-05 output-contract final handoff

Status: complete. Accepted for recording in ChatMap on 2026-10-12.

## What was done

Specified, implemented, tested, and ran a three-gate semantic-extraction output
acceptance contract over the preserved CM-EXP-SEMANTIC-03 (`qwen2.5:7b`) and
CM-EXP-SEMANTIC-04 (`qwen2.5:14b`) responses. Standard library only. No model
contacted; no prompt, fixture, raw response, or frozen scorer output changed.

## The contract

Three ordered independent gates; full acceptance requires all three; a later
gate never masks an earlier failure.

1. Strict JSON validity — one UTF-8 top-level object, no trailing content, no
   fence salvage, no duplicate member names at any depth (compared after escape
   decoding, recursive, failing even on identical values; case-distinct names
   stay distinct).
2. Schema conformance — the frozen `prompt-v3.txt` SCHEMA; applied only to
   gate-1-valid responses; a superset of the frozen scorer's internal
   `validate_schema`.
3. Semantic acceptance — the frozen scorer's result, read unchanged.

Spec: `semantic-output-contract.md` (committed before implementation).

## Results

| Gate | 7B runs/cases | 14B runs/cases |
|---|---|---|
| Strict JSON | 30/30, 10/10 | 27/30, 9/10 |
| Schema | 30/30, 10/10 | 27/30, 9/10 |
| Semantic (frozen) | 24/30, 8/10 | 30/30, 10/10 |
| Full acceptance | 24/30, 8/10 | 27/30, 9/10 |

- 7B fails on semantics only (S07 x3, S10 x3); structurally clean.
- 14B fails on structure only (S09 x3 `duplicate key: facts`); semantically clean.
- No schema-only failures for either model.

## Difference from historical lenient figures

The frozen lenient scorer reported 14B semantic acceptance at 30/30, 10/10
(preserved, unchanged). The three-gate contract refuses last-key-wins laundering
of S09's duplicate key, so 14B **full acceptance is 27/30 (9/10)**. The gap is
entirely S09. For 7B the contract confirms structure was never the bottleneck;
full acceptance equals semantic acceptance at 24/30 (8/10).

## Verification

- Focused tests 19/19 (`test-results.txt`).
- Two consecutive evaluator runs byte-identical (`repeatability-evidence.txt`).
- 03 and 04 frozen outputs reproduced byte-for-byte in the isolated copy; both
  prior repositories clean at their recorded commits.
- Evaluator reproduces 04 strict validity 27/30, 9/10 (S09 only) and 04 semantic
  30/30, 10/10.

## Recommendation

Freeze this exact three-gate contract for subsequent experiments; report all
four totals side by side. Do not integrate into ChatMap production on this
ten-case corpus. Optional next bounded step: widen the corpus or add adversarial
structural cases, still without production integration.

## Commits

- Source (unchanged): 03 `9f95f57`, 04 `c77eede`.
- Experiment repo `chatmap-semantic-output-contract-2026-10-11`:
  - pre-implementation (spec) commit: `2ac5794`;
  - result commit: `8edd9c3`.

## Evidence locations

`C:\Users\ray\eclipse-workspace\chatmap-semantic-output-contract-2026-10-11\output-contract\`
- `semantic-output-contract.md` — frozen specification.
- `evaluate-contract.py` — evaluator (stdlib only).
- `test-evaluate-contract.py`, `test-results.txt` — focused tests (19/19).
- `repeatability-evidence.txt` — two-run byte-identical proof.
- `contract-results.json`, `contract-results.csv` — per-response rows + totals.
- `model-comparison.md` — cross-model three-gate comparison.
- `semantic-output-contract-report.md` — full report.
- `input-hash-inventory.txt`, `result-hash-inventory.txt` — hashes.

## ChatMap boundary

ChatMap production code, schema, database, UI, A2A paths, worker-lifecycle
paths, and `.chatmap-local/` were untouched. No ChatMap repository file was read
or written for this experiment.
