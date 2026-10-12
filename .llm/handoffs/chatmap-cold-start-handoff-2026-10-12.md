# ChatMap Cold-Start Handoff

Status: current as of 2026-10-12. Use this handoff to start a new ChatMap chat.

## Start Here

Read:

1. `AGENTS.md`
2. `.llm/design.md`
3. `.llm/working-context.md`
4. this handoff

Do not recursively read all handoffs. Consult the specific experiment handoffs named below only when their detailed evidence is needed. Do not read `.chatmap-local/`.

## Project Purpose and Boundary

ChatMap is a local application for importing, preserving, searching, organizing, and exporting AI conversations. Its longer-term purpose is to extract durable semantic knowledge, keep that knowledge current, and preserve provenance and history.

ChatMap is a durable ledger and status system, not a general scheduler or agent harness. External runtimes own execution. ChatMap owns continuity, provenance, accepted evidence, and durable history. External model or agent output remains evidence until it passes an explicit acceptance contract.

## Work Completed in the Previous Chat

### Semantic extraction

The bounded semantic-extraction sequence CM-EXP-SEMANTIC-03 through 05 is complete.

- Prompt refinement for local `qwen2.5:7b` stopped after persistent semantic failures.
- `qwen2.5:14b` showed a strong semantic improvement on the frozen ten-case corpus.
- The 14B model nevertheless emitted duplicate `facts` keys in all three S09 runs, demonstrating that semantic correctness and structural validity must be evaluated separately.
- CM-EXP-SEMANTIC-05 froze a three-gate acceptance contract:
  1. strict JSON validity, including rejection of duplicate keys;
  2. schema conformance; and
  3. semantic acceptance.
- Full acceptance was 24/30 runs and 8/10 cases for 7B, versus 27/30 runs and 9/10 cases for 14B.
- The ten-case corpus is too small to justify production integration.

Detailed evidence:

- `.llm/handoffs/chatmap-semantic-refinement-3-final-handoff-2026-10-07.md`
- `.llm/handoffs/chatmap-semantic-stronger-model-final-handoff-2026-10-11.md`
- `.llm/handoffs/chatmap-semantic-output-contract-final-handoff-2026-10-12.md`

### OpenWorker ownership transition

The OpenWorker investigation began as a ChatMap architectural experiment and has now moved to the separate `rtayek/openworker` repository.

Established conclusions:

- OpenWorker is a possible executor or harness.
- Its SQLite journal, audit, session, team, and artifact records are promising candidate evidence for passive ChatMap ingestion.
- OpenWorker does not replace ChatMap's durable ledger.
- The 7B OpenWorker run was not trustworthy: after a context-size correction stopped looping, the model fabricated source analysis, a file write, and success.
- ChatMap and OpenWorker test different layers:
  - OpenWorker asks whether an agent can perform work honestly and preserve operational evidence.
  - ChatMap asks whether the resulting meaning is structurally and semantically acceptable.
- OpenWorker is paused. Its only optional remaining action is one bounded 14B success/failure/restart-persistence test. That experiment belongs to OpenWorker, not ChatMap.

Detailed transition:

- `.llm/handoffs/handoff-chatmap-to-openworker-ownership-transition-2026-10-12.md`

No OpenWorker code or integration was added to ChatMap.

### Repository cleanup

The OpenWorker self-test branch was merged into OpenWorker `master`. Its completed Big Brother and cold-start handoffs were moved to that repository's archive. ChatMap retains only its own evidence and the ownership-transition record.

## Recommended Next ChatMap Task

Design and freeze an independently written, larger and more adversarial semantic-extraction corpus before making any new model requests.

The new corpus should:

- contain roughly 20 to 30 cases;
- be written without modifying or tuning the frozen prompt or evaluator;
- include negation, temporal revisions, disputed attribution, contradictory sources, decisions versus rejections, unresolved questions, pronoun resolution, omissions, and structurally adversarial outputs;
- specify expected semantic results before model execution;
- preserve exact input hashes and provenance;
- use the unchanged three-gate contract; and
- report strict JSON, schema, semantic, and full-acceptance totals separately.

Freeze the corpus specification and expected answers in a pre-execution commit. Only then run the selected models. Production integration remains out of scope.

## Other Open Agenda Items

These remain available but are not the recommended immediate task:

- bounded instruction-and-skill provenance using existing assignment fields;
- selecting and testing a small useful skill set;
- evaluating Agent Client as a portable Java-to-CLI adapter;
- deciding whether `A2aTaskRecorder` remains experimental or enters a production path;
- investigating real-time capture for live web-CDP providers;
- making caller-chain escalation structurally queryable, which requires explicit architectural approval;
- changing remaining filenames to lowercase.

## Opening Prompt for the New Chat

Continue the ChatMap project from
`.llm/handoffs/chatmap-cold-start-handoff-2026-10-12.md`. Read
`AGENTS.md`, `.llm/design.md`, `.llm/working-context.md`, and that handoff.
Do not recursively read other handoffs or `.chatmap-local/`. First review the
proposed larger adversarial semantic corpus experiment and recommend the
smallest specification-and-freeze step before any model is run or any
production code is changed.
