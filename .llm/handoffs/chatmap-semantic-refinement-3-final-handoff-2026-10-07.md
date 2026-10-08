# CM-EXP-SEMANTIC-03 final handoff

## Result

The final bounded `qwen2.5:7b` prompt-refinement experiment is complete.
CM-EXP-SEMANTIC-01 reproduced 7/10 strict cases and 21/30 passing runs;
CM-EXP-SEMANTIC-02 reproduced 7/10 strict cases and 23/30 passing runs.

The third experiment produced 30/30 schema-valid responses. Legacy and revised
scoring both report 8/10 strict cases and 24/30 passing runs:

- S01, S02, S03, S04, S05, S06, S08, and S09: 3/3 scored pass.
- S07: 0/3; decisions and rejections regressed into `facts`.
- S10: 0/3; both disputed reports were preserved, but the certain absence of
  independent verification was omitted.

Manual acceptance inspection also rejects S04 in all three runs because the
model retained `them` instead of resolving it to `the workers`. That behavior
passes the older frozen fixture but fails this experiment's stronger explicit
coreference requirement.

All ten cases were byte-identical across their three runs. The checker emitted
six useful warnings, and all seven focused tests passed. Frozen inputs remained
unchanged after execution.

## Recommendation

**Stop prompt refinement for qwen2.5:7b and compare a stronger model.**

Do not integrate semantic extraction into production from this experiment.

## Evidence

Evidence directory:

`C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-refinement-3-2026-10-07\`

Frozen pre-execution commit:

`70178b62a9378a400ff62145c026ce0a454c12b9`

The detailed report is `semantic-refinement-3-report.md`; legacy and revised
score files, checker warnings, all 30 raw responses, invocation metadata,
hash inventories, and prior-evidence copies are in the same repository.

ChatMap code and data were untouched. This handoff has not been copied into
ChatMap; repository delivery requires Ray's review first.
