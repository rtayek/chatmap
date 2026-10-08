# CM-EXP-SEMANTIC-02 final handoff

## Final-results verification

The frozen deterministic scorer, warning checker, and focused tests were rerun
on 2026-10-07 against the existing Phase 4 responses. Ollama was not contacted.
The score and warning artifacts reproduced byte-for-byte, every case was
compared with the 7/10 baseline, and no frozen input changed.

## Outcome

The refinement is complete and classified as partial progress. Baseline
scoring reproduced 7/10. Refined scoring remains 7/10 strict cases passing in
all runs: the seven baseline passes were preserved; S04 passed 0/3, S07 passed
2/3, and S10 passed 0/3. All 30 outputs were valid, schema-conforming JSON.

Recommendation: **Refine again**.

## Evidence location

All refinement evidence is outside the ChatMap repository at:

`C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-2026-10-07\`

Key artifacts:

- `refinement-plan.md`
- `experiment-manifest.json`
- `baseline-hashes.txt`
- `frozen-input-hashes.txt`
- `prompt-v2.txt`
- `worked-examples.json`
- `cases/` (unchanged S01-S10 fixtures)
- `run-v2.py`
- `score-v2.py`
- `warning-checker.py`
- `test-warning-checker.py`
- `checker-test-results.txt`
- `raw-v2/` (30 raw responses, 30 metadata files, and one run log)
- `scores-v2.json`
- `scores-v2.csv`
- `warnings-v2.json`
- `baseline-comparison.md`
- `semantic-refinement-report.md`

The pre-execution frozen state is independently inspectable at local Git commit
`c6b25791346c58269cee9353164fff6eb66a8062` in the evidence directory.

## Boundaries and deviation

No ChatMap production code or data was changed, and `.chatmap-local/` was not
read. The baseline scorer was initially run in place and rewrote its two score
files with byte-identical content, changing their modification times; the full
report records this procedural deviation.
