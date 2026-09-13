---
id: CM-CTX-01
lifecycle: working
status: active
provenance: git-history
---
# ChatMap Working Context

**Updated:** 2026-09-13
**Authority:** current operational state; update or replace this file as work changes

## Purpose

ChatMap imports, preserves, searches, organizes, and exports conversations. Its
long-term purpose is to produce durable semantic knowledge from those
conversations and keep that knowledge current while retaining provenance and
history.

## Current State

- Gemini Workspace Takeout directory import is implemented through
  `importGeminiTakeout`, using the existing transactional import service and
  a separate `geminiTakeoutJson` source. One real 16-turn conversation passed
  isolated import/reimport and raw-turn preservation checks; the full Gradle
  quality gate passed (618 tests, 4 skipped). Direct archive input and filename
  identity stability across separate exports remain unverified or unsupported.
  See `handoffs/handoff-chatmap-gemini-takeout-implementation-2026-09-10.md`.
- The deterministic Java/SQLite application supports multi-source acquisition,
  import, search, project/tag organization, Markdown export, optional LLM
  prompting, and handoff collection.
- The worker-lifecycle vertical slice is incorporated into ChatMap. It proves
  durable assignments, sessions, lifecycle events, artifacts, semantic
  handoffs, retirement, and successor chains. It does not prove semantic
  preservation across handoffs.
- `handoff.HandoffWatcher` is transport-only. It collects stable files; it does
  not interpret, route, commit, or update the database.
- The shell LLM relay is a completed external experiment in `rtayek/bin`, branch
  `archive/llm-relay`.
- An independent review of commit `f786093` found two remaining defects. Strict
  Ollama response validation was repaired in `031aba9`. Archive-staging failure
  propagation was repaired and regression-tested in `7832eec` and `a56d463`;
  a local full Gradle check passed after the repair.
- Caller-owned migration transactions are now protected by a JDBC savepoint.
  Commit `63a7ad6` rolls back only migration work on failure while leaving the
  caller's surrounding transaction under caller control.
- Project guidance and handoffs now live under `.llm/`, with `index.md` as the
  repository-controlled discovery registry. `CLAUDE.md` routes Claude through
  `AGENTS.md`; `AGENTS.md` routes agents through `.llm/index.md`; and the
  index routes them to human, persona, durable, working, and selected handoff
  documents. A fresh read-only discovery test in Codex, Claude Code, and
  Anti-Gravity produced substantive agreement on purpose, ownership,
  architectural boundaries, current work, undecided questions, and the
  `.chatmap-local/` exclusion. Anti-Gravity reported `AGENTS.md` first;
  Codex reported `.llm/index.md` before `AGENTS.md`; Claude did not report
  actual read order. Semantic discovery is validated, while exact automatic
  startup order is not fully proven for every client.
- Confirmed on a 2026-09-10 Claude Code cold start: the harness auto-injects
  `CLAUDE.md` and its `@AGENTS.md` reference, but does NOT automatically read
  `.llm/index.md` or the files it routes to. The AGENTS.md "MUST read
  `.llm/index.md` before doing anything" step is therefore not self-executing in
  Claude Code; the index was only read after a prompt. Candidate fix: a
  client-side startup hook (e.g. `.claude/settings.json` SessionStart) that reads
  the index, since repo Markdown alone cannot force the order.
- The bounded metadata pilot now separates Markdown bodies, document-local YAML,
  and repository-wide JSON rules. The deterministic read-only validator makes
  the document scope and front-matter enforcement explicit. The manifest's
  `validation` block declares the required strict policy; it is not a set of
  switches, and missing or unsupported values fail validation. Duplicated
  document-path lists remain an accepted small drift risk for now. The earlier
  speculative Agent OS proposal is preserved under `.llm/handoffs/archive/`;
  `evo.md` records only adopted ChatMap evolution.
- The full Gradle quality pipeline passed after the A2A merge. The consolidated
  A2A server and same-task continuation client also passed their runtime check.
  Live provider tests remain intentionally opt-in.
- The bounded A2A experiment proved Agent Card discovery, completed, failed,
  input-required, and same-task continuation behavior. Its source now lives in
  `chatmap.a2a.experiment` on `master`. A bounded recorder projects visible
  task snapshots, states, messages, history, and text artifacts into the existing
  worker-lifecycle ledger. The continuation client uses an isolated temporary
  ChatMap home; production data and the UI remain untouched. The read-only
  `workerLifecycleRecord` command successfully reopened that database and
  displayed the persisted assignment, session, four transitions, decision
  details, and three artifacts after the A2A client exited. The temporary
  A2A and worker-lifecycle worktrees and branches have been removed; the primary
  ChatMap worktree is the only active worktree.
- The model-backed A2A path was proven with local `qwen2.5:7b`. The server
  advertised an Ollama Agent Card and returned a completed `worker-result`
  artifact. The `a2aModelRecord` client then stored a real model task in an
  isolated lifecycle ledger. A separate process reopened session 1 and verified
  two lifecycle events plus the raw task snapshot and model-result artifacts.
- Manual semantic probes showed that the 7B model can obey a well-scoped
  request, but can also overstate causality, violate sentence structure, and
  reverse a supplied fact. The `a2aSemanticProbe` command then required one
  fixed four-field factual contract. Local `qwen2.5:7b` returned the exact
  ordered values with no extra prose, and Java accepted the response.
- Three native read-only subagents ran concurrently against a pinned ChatMap
  revision while the existing worker-lifecycle service recorded a coordinator,
  three sibling workers, their separate artifacts, and a synthesis session in
  an isolated database. Database reopen and chain traversal passed. ChatMap
  recorded the externally executed work; it did not launch or schedule the
  workers. See `handoffs/2026-09-12-CM-parallel-ledger-recorded-run-handoff.md`.
- Follow-up repair `c68d433` preserves a SQL NULL root predecessor across
  readback and introduces an explicit failure report containing the reason and
  preserved partial work. The A2A recorder now uses that failure path. Eight
  focused repository, service, and A2A recorder tests passed through a direct
  JUnit launch using the vendored dependencies. Ray subsequently ran the full
  Gradle quality gate locally and reported a green result.
- The bounded semantic-evaluation experiment (CM-EXP-SEMANTIC-01) ran against
  commit `22a06e0` using local `qwen2.5:7b` at temperature 0, three runs per
  case, scored deterministically with no LLM in the grading loop. Transport and
  format were total: 10/10 valid, schema-conforming JSON, bit-for-bit identical
  across all three runs. Semantic correctness was 7/10. The three failures were
  negation (denial left `polarity:"affirmed"`), decision-vs-rejected status
  (both emitted as `facts`, status arrays empty), and disputed attribution
  (conflicting reports downgraded to `certain`). No fabricated facts and no
  invented causality occurred. All evidence lives outside the repository at
  `C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-2026-09-13\`; only the
  final handoff was added inside the repo. ChatMap production code, schema,
  database, and UI were untouched. See
  `handoffs/chatmap-semantic-evaluation-experiment-handoff-2026-09-13.md`.
- An independent read-only audit of that experiment's evidence returned
  VERIFIED WITH QUALIFICATIONS. It reproduced the scores byte-for-byte,
  recomputed and matched the prompt and corpus SHA-256 hashes, confirmed the
  7/10 semantic result by manual inspection of all ten fixtures and thirty raw
  responses, and confirmed all three runs per case are byte-identical. No LLM
  was used in the audit. Qualifications, none of which change the result: the
  pre-relaxation scorer is not preserved so the disclosed S06 empty-object fix
  cannot be reconstructed from evidence alone (it affects only S06, and format
  is 10/10 under the relaxed rule or 9/10 under a strict non-empty-object rule);
  raw responses are stored CRLF rather than the LF Ollama emits (content-
  faithful, not byte-faithful); and the corpus-hash procedure is not codified in
  a preserved script. The next safe action remains REFINE, then rerun the same
  frozen corpus. Audit evidence is outside the repository at
  `C:\Users\ray\eclipse-workspace\chatmap-semantic-eval-audit-2026-09-13\`.

## Closed Work

- Shell LLM relay experiment: successful, tested, and archived.
- Worker-lifecycle experiment: successful and incorporated; no longer a
  separate project.
- Agent-facing Markdown pilot: completed. Keep client entry points simple;
  distinguish auto-discovered skills from ordinary operational Markdown.
- Agent-protocol survey and bounded A2A experiment: completed. A2A is the
  selected agent-to-agent wire protocol; MCP is complementary; ACP is absorbed
  into A2A; ANP is deferred.
- Project-memory discovery test: completed with substantive agreement across
  Codex, Claude Code, and Anti-Gravity.
- Metadata pilot audit and validator: completed. The validator enforces the
  manifest's strict policy profile and rejects missing or unsupported policy
  declarations. The `validation` block is machine-readable policy, not a set
  of optional toggles.
- Independent code review: completed. Previously reported transaction,
  worktree-preservation, structured-output, process-reader, and platform-Codex
  defects no longer reproduced.
- Archive-staging failure propagation: repaired and regression-tested. A failed
  inbox `git add` now returns partial failure, preserves the archived artifacts
  for recovery, and does not attempt the archive commit.
- Recorded parallel-subagent experiment: completed. It proved that ChatMap can
  durably describe an externally executed fan-out and synthesis without
  becoming the scheduler. Structural fan-in, run identity, execution timing,
  failure recovery, and semantic correctness remain unproven or deferred.

## Worker, Skill, and Parallel Work

Keep these four concepts distinct:

1. **Subagents:** temporary runtime instances launched by Codex, Claude Code,
   Anti-Gravity, or another external agent runtime.
2. **Worker definitions:** portable Markdown contracts describing a worker's
   role, task, inputs, tools, constraints, evidence, and definition of done.
3. **Skills:** reusable capabilities consisting of instructions and, when
   needed, scripts or supporting resources.
4. **Parallel workflows:** fan-out, isolation, result collection, failure
   handling, and fan-in synthesis across multiple workers.

External runtimes execute the subagents. ChatMap records assignments, worker
definitions, skills supplied, states, artifacts, decisions, failures, and
provenance. Stable reusable worker definitions and skills will probably belong
in `dotmdfiles` after ChatMap experiments establish their useful form.

## Active Agenda

1. Subagents: repeat the bounded parallel exercise with one worker deliberately
   failing. Verify that its reason and partial work survive and that successful
   sibling results still reach synthesis.
2. Worker definitions: extract the smallest portable Markdown worker contract
   from the successful experiments. Avoid personality-heavy role catalogs.
3. Skills: identify a small useful set, test how each runtime supplies them to
   workers, and distinguish project-specific skills from reusable templates.
4. Parallel workflows: record the proven fan-out, isolation, failure, and
   synthesis procedure. Do not add a scheduler, fan-in schema, or run identifier
   until an experiment demonstrates the need.
5. Portable CLI execution: soon conduct a bounded, isolated evaluation of Mark
   Pollack's Agent Client as a common Java adapter for Claude Code, Codex,
   Gemini CLI, and possibly Anti-Gravity. Determine whether it can replace
   provider-specific CLI launching while ChatMap continues to own lifecycle
   recording and external runtimes continue to own execution. Pin one released
   version, use conservative permissions in disposable worktrees, and assess
   its Business Source License, dependency footprint, failure normalization,
   session portability, and trajectory mapping before considering integration.
   This is a recommended investigation, not the next action or an architectural
   commitment.
6. Caller-chain escalation model (worker returns an unresolved decision to its
   caller; each caller resolves within its authority or propagates upward).
   VERIFIED 2026-09-13, no schema change made. Findings: (a) caller identity is
   recorded only via the single `workerAssignments.predecessorSessionId` edge
   plus free-text `workerSessions.workerIdentity` labels; that edge is
   overloaded (it means both "who called me" and "who I succeed") and cannot
   represent fan-in with multiple parents -- the parallel harness names the
   three parents in prose, not structurally. (b) Decision RAISING is recorded
   (`workerLifecycleEvents.question`/`reason`, and handoff free-text
   `decisionsAndReasons`/`unresolvedProblems`/`requiredUserDecisions`), and
   `escalationBehavior` stores the policy, but decision RESOLUTION provenance is
   not modeled: no resolver identity, no resolved-at-level, no per-decision rows,
   and no link from a resolution back to the raising event. The escalation model
   is therefore not queryable today. Making it queryable is an architectural
   change (distinct caller/parent edge separate from succession, a fan-in link,
   and per-decision raisedBy/resolvedBy/resolvedAtLevel rows); do not propose or
   implement it without Ray's go-ahead.
7. Resolved by CM-EXP-SEMANTIC-01: the ten-case corpus was run and the
   recommendation is Refine, not Stop and not Expand. Next semantic step is to
   add worked examples for negation polarity, decision/rejected/open-question
   categorization, and disputed attribution, plus a deterministic post-parse
   check flagging a negating cue in `relation` paired with
   `polarity:"affirmed"`, then re-run the same frozen corpus against the
   preserved baseline before authoring a larger, independently written corpus.
   Do not integrate semantic extraction into production on this corpus.
8. `A2aTaskRecorder` exists and is tested, but is only exercised by
   `ModelRecordingClient`, which records into an isolated temporary ChatMap
   home. Decide whether to wire it into the production path or keep A2A
   recording bounded to the experiment.
9. Draft a Claude Code SessionStart hook in `.claude/settings.json` that reads
   `.llm/index.md` and its routed files at cold start. Config change: get Ray's
   go-ahead before editing settings. On hold: as of 2026-09-13, project-level
   `.claude/settings.json` SessionStart hooks are reported to crash Claude
   Code's background/Agent-View sessions on some versions (even a bare
   `echo test` reproduces it) -- verify this doesn't affect Ray's actual
   Claude Code usage before implementing.
10. Change all filenames to lower case.
11. Investigate real-time capture for the live web-CDP providers (Claude,
    ChatGPT, Gemini web). Today `latestChat()` is a single on-demand
    snapshot per run of `importAllChats`; nothing watches continuously.
    CDP itself supports a persistent connection with polling or page
    mutation events instead of fetch-once-and-close, using the existing
    `CdpBrowserConnection`/`CdpPage` classes as a base. This is continuity
    (recording), not a harness (autonomous action), so it doesn't cross
    the scheduler/harness line in Deferred below -- still just an
    investigation, not committed to build yet.

## Deferred

- Handoff-watcher provenance, content-hash duplicate detection, explicit queue
  states, Git-checkout move-versus-copy behavior, and fetch/acknowledgement
  policy
- Semantic-preservation tests; existing lifecycle and soak tests validate
  durable structure rather than preservation of meaning
- Worker-lifecycle expansion such as mandatory handoffs before retirement,
  multiple sessions per assignment, and cross-worker queries
- A2A implementation beyond the bounded experiment
- a general scheduler, router, permissions framework, or agent harness
- full semantic-extraction implementation
- embeddings, semantic search, and broad UI redesign
- layer-boundary enforcement (e.g. domain cannot import infrastructure) via
  an ArchUnit test or Checkstyle ImportControl, in preference to a Gradle
  multi-project split, which Ray does not want
- portability of `.llm/human.md`, `.llm/persona.md`, and `AGENTS.md`, which
  are now symlinks to absolute Windows paths under `C:/Users/ray/real-md-files/`.
  Not portable to a second machine, CI, or a fresh clone -- acceptable for now
  since this is a single-machine setup. Ray is planning to move these into a
  System project and have System scan dependents for valid pointers; revisit
  portability if that reorganization doesn't resolve it

## Next Action

Perform one bounded parallel run with a deliberately failed worker and verify
preservation of its failure evidence and the successful sibling results. Use
the result to draft the minimum portable worker definition and parallel
procedure; do not change the schema or add a scheduler.
