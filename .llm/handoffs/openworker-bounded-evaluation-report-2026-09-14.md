# OpenWorker Bounded Evaluation Report

- Author: Claude Code (Opus 4.8), driven interactively with Ray
- Date: 2026-09-14 / continued 2026-09-16
- ChatMap commit examined (read-only snapshot): 1000f865e1c298b61245c6d0054d731afa460224 (master, clean)
- Evidence root (outside the ChatMap repo):
  C:\Users\ray\eclipse-workspace\openworker-eval-2026-09-14\

This evaluates OpenWorker as an external agent runtime whose executed work
ChatMap might record (continuity, provenance, lifecycle, artifacts), without
ChatMap becoming a scheduler/harness. It supplements the source-based readiness
report in the same directory. No ChatMap code, schema, or data was changed. No
connectors or cloud keys were used. The local-only run was completed; the
capable-cloud-model run (Option 1) was deliberately deferred to Ray.

## 1. Executive verdict: INCONCLUSIVE (promising integration seam; execution unproven with a local model)

- The integration seam is genuinely promising: OpenWorker persists its work to
  real, structured, hash-chained on-disk SQLite that maps cleanly onto ChatMap's
  existing lifecycle ledger, readable read-only without UI scraping.
- But no genuinely successful task run was observed. The required baseline
  (local qwen2.5:7b) proved unusable for OpenWorker's agent loop: after a real,
  fixed configuration bug was corrected, the model fabricated an analysis and
  even fabricated the act of writing the output file. Persistence-across-restart
  and the team/journal path with real data were therefore never exercised.
- Not NOT-A-FIT: nothing about OpenWorker's design or storage is disqualifying;
  the opposite. Not PROMISING outright: we have not yet watched a single correct
  run be produced and recorded. Hence INCONCLUSIVE, with a clear, cheap next
  step (one capable-model run).

## 2. Versions, platform, paths

- Product: OpenWorker, github.com/andrewyng/openworker (published under the
  andrewyng org, built on andrewyng/aisuite; widely attributed to Andrew Ng in
  secondary coverage - authorship not independently verified beyond the org).
- Version tested: v0.2.1 (open beta; releases v0.1.4 2026-07-22 .. v0.2.1
  2026-08-25). License: MIT.
- Platform: Windows 11, Intel i7-13700F with an RTX 4060 Ti; Ollama was
  observed running CPU-only.
- Model: local qwen2.5:7b via Ollama (OpenAI-compatible /v1/chat/completions).
- Install: C:\Users\ray\AppData\Local\OpenWorker\ (per-user, no admin).
- Data dir: C:\Users\ray\AppData\Roaming\coworker\ (SQLite DBs + logs).
- Read-only ChatMap snapshot given to OpenWorker:
  C:\Users\ray\eclipse-workspace\openworker-eval-2026-09-14\pinned-checkout-1000f86\
  (git archive of commit 1000f86, no .git, attached as a writable:false root).

## 3. Installation and permission observations

- Installer: OpenWorker-windows-setup.exe, 62,059,003 bytes,
  sha256 a4fa05ee25036112ca06cb484ee98663306f79ad486ce8c34d58542813b26dfa.
- Signing: UNSIGNED. The shipped .sig is a Tauri auto-updater signature, not OS
  Authenticode; SmartScreen warns. Installing is an explicit trust decision.
- Installed silently per-user via NSIS /S (see run notes). Reversible via
  uninstall.exe.
- Architecture: Tauri (Rust) shell + React UI + a bundled Python "sidecar"
  agent server (openworker-server.exe) built on aisuite, PyInstaller-frozen
  (Python 3.12). Bundled deps of note: uvicorn + websockets (ASGI HTTP+WS),
  mcp 1.29.1, SQLite, textual, pypdf, boto3, cryptography.
- First launch auto-downloaded a Whisper speech model (ggml-base.en) - a network
  fetch of OpenWorker's own model, not a connector.
- Governance modes exist: openworker-server --mode
  {discuss,plan,interactive,auto,bypass-approvals,auto-approve}. Default is
  approval-gated; the mode change to auto-approve during the run was itself
  audited (a mode_changed event).

## 4. Successful-task evidence and semantic accuracy review

Task (Worker A): read-only, explain the boundary around WorkerLifecycleService,
identify one confirmed lifecycle limitation with source evidence, produce
openworker-chatmap-readonly-report.md, distinguish fact from inference.

Outcome: FAILED on every substantive axis.

- The model never read the file. The only real tool call the entire session was
  list_files (confirmed in audit_events: list_files proposed/started/finished,
  plus mode_changed; no read and no write tool event).
- It fabricated the analysis. It claimed methods worker_init_logging(),
  handle_request(), start(), stop() and delegates LoggingService, ThreadService,
  TaskScheduler at specific lines. Verified against the real 241-line
  WorkerLifecycleService.java: each of those symbols occurs 0 times; the cited
  lines (28/45/58/65-70) are a null-check, insertAssignment(...), and a
  transition(...) call. The real class delegates to WorkerLifecycleStore,
  TransactionManager, and Clock. The invented snake_case names are not even Java
  style - the model hallucinated from the class name.
- It fabricated the file write. It emitted a writing_file(...) call as prose and
  reported "The report artifact has been created," but the sandbox
  C:\Users\ray\OpenWorker\a613f543-71a\ is EMPTY, the file exists nowhere on
  disk, and no write appears in audit_events.

Handoff warning confirmed emphatically: producing (or claiming to produce) a
file is not proof its claims are correct. Under auto-approve, OpenWorker
surfaced the fabricated success without any grounding check.

Root-cause note (important, and a genuine fix): the first runs looped 144x on
list_scheduled_tasks. Ollama's log showed OpenWorker driving the model at
n_ctx_slot=4096 (default) while the agentic prompt was ~2050 tokens and grew;
88 truncation events with n_keep=4 meant the agent lost its task/history each
turn and looped. Raising Ollama's context to 16384 (OLLAMA_CONTEXT_LENGTH via
run-openworker-16k.sh) eliminated the loop and produced coherent planning
(a 7,729-token turn, no truncation). That fixed the configuration problem; the
remaining fabrication is a model-capability failure that configuration cannot
address.

## 5. Failed / blocked-task evidence

The planned deliberate-failure task (Worker B: nonexistent file, no fabrication)
was not separately run, because Worker A already produced the stronger negative
result: on a file that DOES exist, the local model fabricated both content and a
successful write. Auto-approve (enabled mid-run by Ray) removed the approval-
denial path, so the alternative failure method would have been the nonexistent-
file one; it is now redundant. The blocked/failure question - does OpenWorker
avoid claiming success it did not achieve - was answered negatively for the
local-model configuration: it claimed a nonexistent artifact.

## 6. Persistence, transcript, approval, artifact, export findings

- Artifact: none produced (fabricated). When a real artifact is produced,
  OpenWorker writes it to the session sandbox
  (C:\Users\ray\OpenWorker\<session-id>\), not into a read-only attached root.
- Transcript: sessions.messages was empty mid-run despite n_msgs>0, so the live
  transcript is held in memory and flushed later (likely on close). A recorder
  cannot reliably tail a live transcript from disk; it must read post-hoc or via
  the API.
- Approvals: audit_events has approval provenance columns (approval, reason,
  status, stage: proposed/started/finished). In practice most rows had an empty
  approval value; one showed "once". Governance mode changes are audited.
- Persistence across restart: NOT tested with real task data (no successful run
  to persist). Structurally the SQLite stores are durable on disk, but this was
  not empirically confirmed for a completed task.
- Export: no documented export or event-stream/webhook. Observation is via the
  on-disk SQLite or the token-gated HTTP API.

## 7. A2A and MCP findings (kept separate)

- A2A: no evidence of any agent-to-agent (A2A) protocol support in the product,
  docs, or bundle. Treat as absent.
- MCP: real. The mcp 1.29.1 SDK is bundled and the README documents adding MCP
  servers (Manage -> Integrations). This is OpenWorker acting as an MCP CLIENT
  (consuming external tools). Nothing indicates OpenWorker exposes an MCP server
  that an external recorder could subscribe to. A speculative inversion (ChatMap
  exposing an MCP server that an OpenWorker task calls to log lifecycle) would
  make ChatMap a participant, not a passive recorder; not recommended.

## 8. Mapping to ChatMap's lifecycle ledger

OpenWorker's on-disk stores (C:\Users\ray\AppData\Roaming\coworker\):

- journal.db / journal_entries: append-only, hash-chained
  (seq, ts, case_id, kind, actor, actor_role, model, session_id, payload,
  prev_hash, hash; journal_meta.head_hash). Team/case-scoped: EMPTY for the
  plain single sessions we ran.
- coworker.db / audit_events: per-tool-call record (tool, stage, status,
  approval, reason, args, result_preview, call_id, tokens). This is what a
  single-session run populates.
- coworker.db / sessions: session_id, workspace, model, mode, agent, team,
  grants, messages (flushed late).
- teams.db: team_items (id, title, description, criteria, state, assignee,
  creator, case_id, refs), team_links (src, kind, dst), hash-chained team_events.
  The concrete "team board". EMPTY here (no team run).
- chat.db / chat_messages: inter-agent chat. automation.db: scheduled_tasks.

Candidate mapping:

| OpenWorker | ChatMap ledger |
| --- | --- |
| session / case_id | worker session / assignment |
| journal_entries (hash-chained) | lifecycle events (ChatMap also hash-chains) |
| audit_events.approval/reason | decision / provenance evidence |
| team_items (state, assignee, criteria) | assignment + definition-of-done |
| team_links (src, kind, dst) | successor / relationship links |
| produced file in session sandbox | WorkerArtifact (hash + source path) |

Step-4 answer (journal / team board: real files or UI-only?): REAL, structured,
hash-chained SQLite - not UI-only. ChatMap could ingest them read-only via its
existing SQLite/import abstractions. Caveats: (a) undocumented v0.2.1 beta schema
that may change between releases; (b) single-agent runs populate audit_events,
not the hash-chained journal (that is team/case-scoped); (c) the live transcript
is memory-resident until flush.

## 9. OpenWorker vs Agent Client vs A2A vs ChatMap

- ChatMap: the durable ledger of record; owns continuity, provenance, lifecycle,
  artifacts, and (later) semantic acceptance. Not a scheduler.
- OpenWorker: a full local-first execution harness - agent loop, tools,
  approvals/governance, connectors, scheduling, its own hash-chained journal. It
  overlaps ChatMap's ledger concepts but is an executor, not a system of record.
  Its journal/audit are a candidate INPUT to ChatMap, not a replacement.
- A2A: absent in OpenWorker; irrelevant as a bridge here.
- Agent Client (working-context item 5): a portable Java-to-CLI adapter for
  launching Claude/Codex/Gemini CLIs. Different problem; it does not help observe
  or record an OpenWorker desktop run. Not a substitute and not complementary to
  this particular seam.

## 10. Risks, unknowns, unverified claims

- Local qwen2.5:7b is unusable for this harness (fabricates content and success).
  Whether a capable model (cloud key, or a much larger local model on a GPU box)
  behaves well is UNTESTED - that is the deferred Option 1.
- Persistence across app restart with real task data: UNTESTED.
- The hash-chained journal + team board with real data: UNOBSERVED (empty here).
- The /v1 REST API's read surface (can it return run status/transcript/approvals,
  or only accept submissions) is undocumented and untested; the desktop token is
  in-memory, so external API calls were not attempted.
- Schema is undocumented beta and may change; reading it directly couples ChatMap
  to internals.
- Voice input reproducibly crashed the app (2/2). Unsigned installer (SmartScreen).
- Authorship attribution (Andrew Ng) taken from the hosting org + secondary
  coverage, not independently verified.

## 11. Recommendation: smallest next step

One capable-model run (Option 1), tightly bounded, to convert INCONCLUSIVE into
a real verdict:

1. In OpenWorker, select a capable model (Ray's Claude key), keep the read-only
   snapshot root and interactive (not auto-approve) mode.
2. Run Worker A accurately, then Worker B (nonexistent-file, no-fabrication),
   then close and reopen OpenWorker.
3. Observe, read-only from the SQLite stores: whether a correct artifact is
   produced and hashable; what audit_events/journal_entries/team_events record;
   whether the failure's reason and the success survive restart.

This answers the two things still missing (a genuine successful recorded run and
persistence) at the cost of sending the (Ray-owned, non-secret) ChatMap snapshot
to a cloud model provider. No connectors. If Ray declines cloud, the alternative
is a larger local model on a GPU-capable machine; on this box, where Ollama
ran CPU-only, that is
slow and unproven.

Do NOT, on this evidence, integrate OpenWorker, add connectors, enable
unattended runs, point it at real repositories, or change ChatMap's schema.

## 12. Evidence inventory (in the evidence root)

Key files (size bytes, sha256 prefix):

- openworker-bounded-evaluation-report-2026-09-14.md   (this report)
- openworker-source-based-readiness-report-2026-09-14.md  10665  2383f56d253516e0
- install-and-inspect-evidence.md                          2319  a59bddaf3b66e590
- journal-schema-findings.md                               4041  ea56b1ba6c604f18
- stability-notes.md                                       3540+ efec37f8474fe72b (appended after hashing)
- run-openworker-16k.sh                                    1991  5368f1dee59121f2
- Modelfile.qwen16k                                          40  62c763105e0658f8
- installer/OpenWorker-windows-setup.exe               62059003  a4fa05ee25036112
- installer/OpenWorker-windows-setup.exe.sig                420  (Tauri updater sig)

Directories:

- journal-snapshot/ , live-read*/ , pulse*/ : read-only copies of OpenWorker's
  SQLite DBs taken during monitoring (schema + row evidence).
- pinned-checkout-1000f86/ : 654-file git archive of ChatMap commit 1000f86
  (no .git), the read-only target given to OpenWorker.

## 13. Commands run and state changes

Read-only / no state change: all Ollama /api/tags,/api/ps and warm calls; all
SQLite reads (on copies); log reads; screenshots; process/port queries.

State changes (all outside ChatMap, all reversible):

- Installed OpenWorker v0.2.1 per-user (uninstall.exe reverts).
- Created git-archive snapshot pinned-checkout-1000f86 (no repo change).
- Created derived Ollama model qwen2.5-7b-16k (ollama rm reverts). OpenWorker did
  not list it (curated model list), so it was not used; the working fix was the
  server context env var instead.
- Restarted Ollama with OLLAMA_CONTEXT_LENGTH=16384 via run-openworker-16k.sh
  (transient; a normal Ollama restart reverts it).
- OpenWorker created sessions and downloaded its Whisper model into its own data
  dir; Ray created/deleted chats and toggled auto-approve.

ChatMap repository: untouched, clean, HEAD 1000f86. No commits. No schema/code/
data changes.
