---
id: CM-EXP-SEMANTIC-01-HANDOFF
lifecycle: working
status: complete
provenance: externally-assigned-experiment
date: 2026-09-13
---
# ChatMap Bounded Semantic-Evaluation Experiment - Final Handoff

## What this is

Completion handoff for the bounded semantic-evaluation experiment specified in
`.llm/handoffs/chatmap-semantic-evaluation-experiment-specification.md`
(CM-EXP-SEMANTIC-01). The experiment measured whether a local LLM preserves
explicit meaning when converting short passages into structured facts.

This was an external research experiment. No ChatMap production code, schema,
database, UI, A2A, or worker-lifecycle path was modified. The only file added
inside the repository is this handoff.

## Where all evidence resides

All generated evidence is OUTSIDE the repository, in one self-contained
directory:

    C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-2026-09-13\

Contents:

- `experiment-manifest.json` - commit, environment, model id/digest,
  parameters, prompt hash, corpus hash, timestamps.
- `prompt.txt` - the frozen instruction prompt
  (sha256 cab901b823a77dd1167550b61ec555e3dbfb0ec7670e1352fa3d433bff5f7777).
- `cases/S01.json .. S10.json` - the ten immutable input + expected-meaning
  fixtures, frozen before any model run
  (corpus inputs sha256 4c3af448f0fc09bdead8b0dbb9d6940add2903e91d7125bd83c885fb90ac2c35).
- `raw/` - 30 unedited model responses (10 cases x 3 runs) plus per-run
  `.meta.json` timing/token records and `_runlog-qwen2.5-7b.json`.
- `scores.json` and `scores.csv` - deterministic per-run scores and cross-run
  stability.
- `semantic-evaluation-report.md` - full findings, failure detail, limitations,
  and recommendation.
- `run.py` - driver (calls local Ollama; the only network-touching step).
- `score.py` - deterministic scorer; no LLM, no network. Reproduces
  scores.json/scores.csv from cases/ + raw/ alone.

To reproduce scoring without any model:
`cd <dir> && python score.py`.

## Setup

- ChatMap commit examined: 22a06e05e3332e6e0c65ef00213633d345e988c4 (master).
- Model: local `qwen2.5:7b` (Q4_K_M, 7.6B) via Ollama /api/generate, the same
  local model path as the existing bounded probe.
- Parameters: temperature 0, seed 0, format=json, 3 runs/case. No cross-case
  memory. No model grading of its own output.
- Scoring: Python stdlib (deterministic). Deviation from the human's JShell/
  shell preference is noted in the report; Java 25 ships no JSON parser and the
  deterministic-no-LLM property is what the spec requires. Raw responses are
  preserved so any independent scorer can re-derive the results.

## Results

- Format/transport: 10/10 cases returned valid, schema-conforming JSON with
  correct enum vocabulary. Every case was bit-for-bit identical across its three
  runs (fully deterministic at temperature 0, seed 0).
- Semantic correctness: 7/10 cases pass.
  - PASS: S01 identity, S02 quantity, S03 date, S05 uncertainty,
    S06 chronology-without-causality, S08 open question, S09 supersession.
  - FAIL: S04 negation (denial left `polarity:"affirmed"`),
    S07 decision-vs-rejected (both emitted as `facts`; status arrays empty),
    S10 conflicting reports (disputed downgraded to `certain`; the
    no-verification statement mis-filed as an open question).
- No fabricated facts and no invented causality in any case.

The three failures share a theme: the model captures the content but does not
reliably populate the machine-readable control fields (polarity, category,
certainty) that a deterministic downstream pipeline would key on.

## One post-hoc measurement correction (disclosed)

The scorer's schema check initially rejected an empty `object` string, failing
S06 even though both facts were correct ("Eclipse was closed" is intransitive).
The validator was relaxed to allow an empty `object` while still requiring
non-empty `subject` and `relation`. No expected semantic fixture was changed.
This affected only S06's format_pass.

## Recommendation: REFINE

Not Stop (substrate is sound: deterministic, valid, faithful to content, no
fabrication). Not Expand (three of ten fail on the highest-value properties).
Refine the contract/prompting - add worked examples for negation polarity,
decision/rejected/open-question categorization, and disputed attribution, and
consider a deterministic post-parse check that flags a negating cue in
`relation` paired with `polarity:"affirmed"` - then re-run this same immutable
corpus against the preserved baseline before authoring a larger, independently
written corpus.

Do not integrate semantic extraction into ChatMap production on the basis of
this ten-case corpus.

## Relation to the Active Agenda

Answers working-context Active Agenda item 7 ("decide whether semantic
evaluation should stop at the bounded probe or proceed to a small corpus"):
the ten-case corpus was run; the answer is Refine, not Stop and not Expand.
`working-context.md` was intentionally not modified by this experiment; update
it separately if desired.
