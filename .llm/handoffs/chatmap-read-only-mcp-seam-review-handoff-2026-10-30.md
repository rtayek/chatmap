# ChatMap read-only MCP seam review and checkpoint handoff

Date: 2026-10-30
Status: source review complete; interface proposed; no MCP implementation
Reviewed source: rtayek/chatmap master at
a2464938f5565c8cb9eb5ae62b6f9fc1eff3ae9c
Latest inspected commit: Add handoff docs 2026-10-19.

## Decision and scope

Ray agreed to park semantic evaluation after evaluator v3 and investigate shared
memory access through MCP. This checkpoint records that direction and identifies
the smallest useful application boundary. It does not adopt the proposed
"sovereign control plane" pivot or change the fundamental knowledge model.

ChatMap preserves source evidence and continuity. External runtimes execute
agents. Native agent memory folders may coexist with ChatMap.

Success question: can a fresh CLI session answer "What were we doing, what was
decided, and where is the evidence?" with less context-loading effort and
correct source references?

This review changes documentation only. It does not add an SDK dependency,
server, schema, provider, trace importer, UI, or model call.

## Semantic-evaluation checkpoint

Ray supplied completion and independent-reverification evidence for:
C:/Users/ray/eclipse-workspace/chatmap-semantic-evaluator-v3-2026-10-20/

- Specification: 7396843f76ba05fbdc725724bb8ea03291d5a7b2.
- Executable freeze: a671e4581ffb0e00c0877ba2604eb8f97247b036.
- Metadata-only receipt: d0aab8912de6dd9445b33ffeefb472bbb176e061.
- Reported checks: B controls 64/64; focused v3 88/88; v2 80/80; v1 286/286;
  structural/schema 19/19; warnings 7/7; inventory/provenance 29/29.
- Reported result hash:
  08ee063522d928e9a0a6d51b8fc7036c8c3df23c21b83d90da7fb0659d04e500.

These local results were supplied in the conversation, not independently rerun
during this GitHub review. They are evidence of completed bounded tooling,
not production semantic extraction or model-quality validation. Matching remains
lexical; grouping covers four declared same-category forms; actors need explicit
candidate names. Preserve B as visible development evidence. Further semantic
evaluation is parked rather than scheduled as the next task.

## Verified application seams

| Purpose | Existing source | Finding |
|---|---|---|
| Search conversations | src/chatmap/application/service/SearchService.java | searchResults(query, SearchOptions) delegates through SearchStore. |
| Filter to project | src/chatmap/domain/SearchOptions.java | Supports projectId, tagId, relatedProjectId, archived; no paging/limit fields. |
| Search representation | src/chatmap/domain/SearchResult.java | Chat, project name, tags, snippet; no matched message ID or sequence. |
| FTS behavior | src/chatmap/infrastructure/persistence/sqlite/SearchRepository.java | Prefix tokens joined with OR; rank ordered; deduplicates by chat ID; no SQL limit. |
| Retrieve preserved messages | src/chatmap/application/service/ExportService.java | loadChat(chatId) loads a consistent ChatExportModel inside the existing transaction boundary. |
| Source identifiers | src/chatmap/domain/Message.java and Chat.java | Message ID, chat ID, role, sequence, timestamp; chat source URI, external identity, and transcript hash. |
| Project identity | src/chatmap/application/service/ProjectService.java and ProjectContext.java | Read methods exist; ProjectContext supplies identities/repository path, not document contents. |
| Composition | src/chatmap/app/ServiceGraph.java | Can compose over an already-open Connection; Integrations.none supplies no model execution capability. |
| Normal startup | src/chatmap/app/ApplicationBootstrap.java | Initializes logging, creates home directory, then opens/initializes the database. |
| Connection setup | src/chatmap/infrastructure/persistence/sqlite/Database.java | open configures WAL for file databases; openAndInitialize applies schema and migrations. |
| Existing message port | src/chatmap/application/port/persistence/MessageStore.java | findByChat reads all messages; no paged/message-range method. |
| Existing project-document access | ProjectContext and WorkspaceScanService | No narrow working-context reader identified; recursive workspace scanning is an import concern. |

The inspected tree contained no MCP implementation identified by path.
This is an inspection of published source, not the unseen Windows worktree.

## Smallest useful tool contract

One adapter instance should be explicitly scoped by the operator to one project,
one existing fixture database, and optionally one project root. Keep tool names
and descriptions small. Suggested names below are proposals, not implemented
or SDK-validated contracts.

### searchChats(query, limit)

Reuse SearchService.searchResults with an enforced projectId in SearchOptions.
Reject blank queries rather than accidentally listing every chat.

Return a bounded list of chat ID, title, source type, available timestamps,
snippet, and a stable internal source reference. Default limit 5, maximum 10,
and cap snippet length. Preserve the current documented prefix/OR semantics;
do not advertise semantic search or exact-phrase matching.

The current search service materializes all matching rows before a response
could be sliced. A response cap alone is not a database/memory cap. For the
small fixture proof this can be an explicit limitation; a reusable bounded
query port/repository extension may later be justified by real data volume.

A snippet is a navigation aid, not an exact citable message. No matched message
ID exists in the current SearchResult, so do not invent one. The caller next
fetches a selected chat's preserved message window.

### readChatMessages(chatId, offset, limit)

Reuse ExportService.loadChat as the first source-loading seam and project only
a small window into an application DTO. Check the loaded chat belongs to the
configured project, even if the caller supplies a valid ID from another one.

Return chat/source identity plus message ID, sequence, role, timestamp, and
preserved normalized text. State explicitly that this is stored message text;
rawJson/source bytes are not exposed in this initial contract. Use the chat's
available transcript hash to identify the observed version. IDs are local to
the selected database; a hash/reference is not proof of correctness.

Proposed defaults: offset 0, limit 5, maximum 10 messages, total returned text
budget about 12,000 characters. Use explicit more/truncated indicators. For a
single oversized message, define a follow-up character-window mechanism before
implementation so the omitted part remains retrievable. Never silently truncate
evidence or return an entire transcript as a default.

ExportService currently loads all messages first. This can serve the isolated
fixture proof; do not claim efficient storage pagination. A small dedicated
read service should own limits/DTOs and keep persistence outside MCP handlers.
Preserve consistent snapshots; message order should be deterministic even for
duplicate sequence values.

### readWorkingContext(offset, maxChars)

This optional third tool is needed if the cold-start question depends on
project working state not imported into the conversation database.

Expose only the configured project's .llm/working-context.md, through a narrow
application document port and filesystem adapter. Do not accept arbitrary paths,
URLs, recursive globs, or a model-supplied project root. Validate the configured
real path remains within the operator-selected root, including symbolic-link
resolution where applicable.

Return literal text in bounded chunks, relative path, observed file hash,
offset/continuation, and any document metadata as labels. Missing documents
must produce an explicit missing result. Mark working context as mutable state,
not as automatically accepted source truth or governing instructions.

ProjectContext alone cannot supply this content. Do not reuse WorkspaceScanService
or load every Markdown/handoff file. No new searchable document catalog is
needed for this slice.

A sensible proposed chunk default is 4,000 characters with a 12,000 maximum.
These limits are starting bounds to review, not measured optimal values.

## Read-only startup requirement

A server that exposes only read methods can still mutate the database at startup.

Do not reuse CliBootstrap.open or ApplicationBootstrap.open unchanged for the
read-only claim. Even Database.open changes file journal mode; skipping only
initialize is insufficient. Normal logging can also create files under the home.

Before live data access, implement or verify a dedicated composition path that:
- Requires an explicitly selected existing database and project.
- Opens SQLite with a verified driver-supported read-only mode; never creates
  a missing database.
- Skips schema installation, migrations, WAL mode changes, and imports.
- Treats unsupported/missing schema as an explicit error.
- Uses appropriate read-only snapshot/transaction behavior.
- Keeps protocol stdout free of logs and routes diagnostics to stderr or a
  separately selected diagnostic location.
- Does not initialize model backends, live providers, or write tools.

The exact JDBC configuration must be tested against the repository's pinned
sqlite-jdbc version. This review has not run a read-only connection test.
ServiceGraph.create over a proven read-only Connection may allow reuse; expose
only required read services to the adapter. Do not present mutable repositories
or generic SQL execution to the agent.

For proof, use a tiny isolated database created by the normal fixture setup
before starting the adapter. Verify values/schema unchanged and attempted writes
rejected. Do not use immutable SQLite mode as a shortcut against a live-changing
database. Account for WAL consistency and concurrent writers before any later
real-home test. No live .chatmap-local/ access is authorized by this review.

## Placement and dependency boundary

Prefer a small MCP presentation adapter calling application use cases; SDK types
stay at the boundary. Application services do not depend on MCP, and the adapter
does not embed SQL. Existing exports/search remain deterministic.

Local stdio is the leading transport candidate. SDK version, JSON binding,
dependency footprint, Gradle integration, and client configuration are unresolved
implementation choices; no version is selected or dependency added here. Verify
current official SDK documentation during an implementation task.

## Suggested first proof and definition of done

Before an end-to-end agent run:
1. Create an isolated fixture with known current/historical statements and
   stable source IDs, plus one small working-context file.
2. Verify scoped search, source-window retrieval, missing data, wrong-project
   rejection, truncation/continuation, and read-only startup.
3. Verify one MCP client can discover and call only the intended read tools.

Then use one existing CLI runtime in a fresh session with a fixed continuity
question. Preserve its answer and tool transcript. Check task/decision/source
references against an expected checklist without introducing a model judge.

Measure visible retrieved characters/bytes, number of calls, returned source
coverage, and truncation. Use provider input-token counts only if available;
never equate character counts with tokens. Include tool definitions and initial
context in any baseline comparison, not just response payloads. A saved-session
or always-loaded-context baseline must have the same information available.

This proof measures access and continuity, not a general semantic-memory
benchmark. Do not make better recall or lower context-cost claims before
measurement. Failed source lookup or absent data must remain an honest result.

## Next action and delivery

The current authorized step is recording the checkpoint and this source review.
Review/settle the proposed contract and read-only bootstrap before adding an MCP
dependency or server. Leave trace ingestion, graph visualization, agent scheduling,
A2A productization, native-memory synchronization, and evaluator v4 deferred.

This handoff and the working-context edit are provided as a documentation patch
against the pinned source above. It has not been applied to Ray's Windows
worktree or pushed to GitHub. If the local working context has newer edits,
preserve them and merge these focused updates instead of replacing the file.

Apply/check the patch locally, run the repository's read-only metadata validation
and git diff --check, and review the two changed files. Do not claim Gradle or
runtime tests were run by the source-review session. No production code changes
are included.
