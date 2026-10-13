# ChatMap adversarial model comparison execution handoff

Date: 2026-10-12 (America/Los_Angeles)
Experiment: CM-EXP-SEMANTIC-06 model execution
Status: Ray authorizes the bounded local model comparison described below.

## Assignment

Run qwen2.5:7b and qwen2.5:14b against the 24 frozen A01–A24 cases, three independent requests per case per model: 72 planned requests per model, 144 total.

Use the unchanged extraction prompt and response schema and the frozen extended evaluator. Preserve raw evidence, score deterministically, and report strict JSON, schema, semantic, and full-acceptance totals separately.

This authorization supersedes the prior handoffs' preparation-only/no-model restriction for this bounded comparison. It does not authorize prompt tuning, scorer changes, extra models, production integration, or changes to ChatMap.

## Source and isolation

Source repository:
C:/Users/ray/eclipse-workspace/chatmap-semantic-evaluator-extension-2026-10-12/

Exact frozen input commit:
b29afc803d5b403a312bfd356126209c7ca0bdee

Reported metadata-only receipt commit:
e0009b4

Create a separate independent sibling repository for execution:
C:/Users/ray/eclipse-workspace/chatmap-semantic-adversarial-model-run-2026-10-12/

Use an independent local clone or copies pinned to the exact freeze. Keep the source repository unchanged. Preserve the receipt separately as provenance if needed; verify its contents rather than assuming that its later commit changed no executable input.

If the destination exists, inspect and reuse only if it is this same experiment; otherwise choose a nonconflicting sibling. Never overwrite prior raw responses, delete existing work, or use a ChatMap worktree.

Read only the directly relevant frozen files: evaluator-extension-final-handoff.md, evaluator-extension-plan.md, representability-review.md, test-report.md, historical-reproduction.md, experiment-manifest.json, freeze-receipt.md, input-hash-inventory.txt, corpus-A.json, the selected case/input fixtures, prompt-v3.txt, score-ext.py, evaluate-ext.py, and required runner code. Follow applicable local AGENTS.md instructions.

ChatMap code, schema, database, UI, A2A, worker-lifecycle paths, and .chatmap-local/ remain untouched. Do not copy a result handoff into ChatMap until Ray reviews the result. No push is authorized.

## Preflight without model generation

1. Verify the exact freeze exists; check source cleanliness, recorded hashes, and prompt/schema byte preservation.
2. Resolve the full receipt SHA and confirm its metadata-only scope.
3. Confirm the manifest selects exactly 24 unique cases and three runs for each model. Verify source passages and expected fixtures remain separate.
4. Run the frozen extension tests and unchanged structural tests once. Expected reported counts are 286/286, 19/19, and 7/7; verify actual counts and any reused-test details.
5. Confirm the frozen scorer's documented lexical limits, particularly temporal status, ambiguity, per-worker scope, and attribution. Do not add marker-token instructions to the model prompt.
6. Inspect the prior runner and invocation metadata. Use its established Ollama extraction path, prompt placement, JSON mode, temperature 0, seed 0, and inference options. Do not infer context size or output limit from model names.
7. Verify both local models are installed. Record their exact names/digests and Ollama version, endpoint, hardware/runtime details, and actual inference options. Model metadata inspection is permitted; do not download or substitute a missing model.
8. Define one deterministic request schedule, preferably all 7B cases/runs followed by all 14B cases/runs. Run requests sequentially to avoid model-loading contention. Declare that model order is fixed rather than randomized.
9. Verify each model receives only the unchanged instruction prompt plus the selected case ID and exact source passage, using the runner's established input format. Never send expected fixtures, witnesses, mutations, case titles, evaluator markers, or trap descriptions.

If source verification or tests fail, preserve evidence and report the blocker before generation. If one model is missing or an invocation option cannot be reproduced, report the concrete issue rather than silently changing the comparison.

## Runner preparation and execution freeze

Reuse an existing runner if it supports explicit A-case selection, run IDs, byte-preserving evidence, and the frozen invocation envelope.

If its S-only discovery prevents execution, authorize only a small runner adapter in the new execution repository. This adapter may select the explicit corpus, issue fixed requests, and preserve evidence. It must not alter the extraction instructions, schema, expected fixtures, scorer, or gate logic.

Verify the adapter without model calls, using a local fake transport or recorded test envelopes. Test that each scheduled request maps to one unique artifact path, prompt bytes are unchanged, no expected answers leak, and bytes survive save/readback. Tests are not counted as model requests.

Before generation, commit the execution plan, exact request envelope/options, runner/adapter, selected corpus, frozen tools, provenance, and hash inventory. Record the full execution preflight commit SHA in a later receipt or external file. The evaluator freeze remains b29afc803d5b403a312bfd356126209c7ca0bdee; separately identify the runner/protocol freeze.

Do not contact the models until that preflight freeze is complete and inputs are verified. Ray's current authorization covers execution after these checks; no further conversational approval is needed.

## Model requests

Issue exactly one planned generation request for each model/case/run slot.

- Fresh independent request; no prior-case conversational memory.
- No adaptive retries, repair prompts, output cleanup, or best-of selection.
- No feedback from scorer into prompt or execution.
- No context/output-option changes mid-run.
- No parallel generation requests.
- Preserve model outputs as evidence, never accepted production knowledge.

Persist the full request bytes, full Ollama response/envelope bytes, extracted model-content bytes, transport status, actual options, start/end timestamps, model digest, and relevant timing/token metadata. Describe how the JSON envelope is decoded to extract content. Preserve extracted content exactly as returned after envelope decoding; no LF/CRLF conversion, fence removal, trimming, or JSON reserialization.

Record transport failures, timeouts, empty content, truncation indicators, and unexpected envelopes without fabricating a response. Do not substitute a successful retry. Keep planned denominators fixed.

If the process stops, resume only slots verified never to have been issued. If a request may have been issued but its response was lost, mark that slot indeterminate; do not regenerate it under the same run ID. Report an incomplete comparison if necessary.

Freeze violations or runner defects discovered after generation invalidate the affected execution. Preserve it, stop, and report; do not repair and rerun silently.

## Scoring and reporting

Run evaluate-ext.py and score-ext.py unchanged against preserved evidence. Use their frozen manifest and documented interfaces. Do not rewrite model responses or expected fixtures.

For each model report:

- Scheduled slots, issued requests, completed responses, transport failures, and indeterminate slots.
- Strict JSON acceptance out of 72 runs and 24 cases.
- Schema acceptance out of 72 and 24, plus not-evaluated counts.
- Semantic acceptance out of 72 and 24, plus unscorable/not-evaluated counts.
- Full acceptance out of 72 and 24.
- A case passes a gate only when all three runs pass that gate.
- Missing/failed slots never reduce denominators or count as passes.
- Per-case and per-run exact reasons, omissions, unmatched/unsupported assertions, polarity, certainty, category, and provenance errors.
- Byte-level stability separately from semantic-score stability and semantic-content agreement; equal scores alone do not prove equal meaning.
- Results for the six originally questioned cases and other attribution/causality stress cases.
- Exact inference settings and any environment limitations.

If the frozen aggregator cannot represent transport failures or missing slots directly, retain its unchanged outputs and add a separately labeled deterministic coverage summary over all planned slots. Do not count absent responses as evaluator passes.

Run scoring twice without new model requests and verify byte-identical machine-readable results. Verify all frozen input hashes afterward.

Historical S-case figures remain contextual and separate. Do not combine them with A-case totals or claim percentages are directly comparable across corpora.

## Human inspection and lexical qualifications

After frozen scoring is saved, inspect each distinct output variant against its source and expected meanings. At minimum inspect every failing case and all six representability concern cases, including their apparent passes. Inspection is read-only.

No LLM may grade model responses. Manual inspection by the executing agent may document observations and candidate scorer limitations; it does not certify new semantic ground truth or change frozen scores. If an independent human semantic verdict is needed, provide the exact source/output pair for Ray.

Create lexical-qualifications.md listing:

- Apparent correct paraphrases rejected by the bounded lexical rules.
- Apparent incorrect assertions accepted by those rules.
- Ambiguous or overly restrictive expected fixtures.
- Representative exact outputs, citations to local case/run paths, and explicit reasoning.
- Whether these qualifications undermine the model ranking or require a separate evaluator audit.

Do not add synonyms, marker tokens, alternate groupings, or new rules after seeing output. Preserve frozen measured totals even when a qualification seems persuasive. Distinguish algorithmic acceptance from reviewed semantic observations.

## Completion and recommendation

A completed comparison contains all 144 planned slots with honest disposition, preserved evidence, deterministic reports, and unchanged frozen inputs. Transport failures remain part of the result; they do not justify extra generation requests.

Recommend exactly one evidence-driven next step: independent evaluator audit, a further independent corpus, a separately specified prompt/model investigation, or stopping this line. Do not recommend production integration from this challenge set alone.

Commit results and a concise final handoff in the execution repository. Keep raw evidence outside ChatMap. No push unless Ray separately authorizes it.

## Required artifacts

- execution-plan.md and experiment-manifest.json.
- Input/tool provenance and sorted hash inventories.
- Full execution preflight SHA and receipt.
- Request schedule and per-slot status inventory.
- Exact requests, envelopes, extracted content, transport errors, and invocation metadata.
- Unchanged evaluator outputs and fixed-denominator coverage report.
- Repeatability and post-run input-hash evidence.
- adversarial-model-comparison-report.md.
- lexical-qualifications.md.
- adversarial-model-comparison-final-handoff.md.

## Completion response to Ray

Lead with a compact table of strict JSON, schema, semantic, and full-acceptance runs/cases for 7B and 14B. Include request coverage, key failure cases, stability, lexical qualifications, exact model digests/options, evaluator and execution freeze SHAs, result commit, evidence paths, and the recommended next step.

Confirm whether ChatMap remained untouched and whether any frozen input changed. State any incomplete or invalidated execution plainly; do not label a partial run complete.

