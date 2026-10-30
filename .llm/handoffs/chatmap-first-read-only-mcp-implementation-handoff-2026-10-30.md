# ChatMap first read-only MCP implementation handoff

Date: 2026-10-30 (America/Los_Angeles)
Status: Ray authorizes this bounded implementation and fixture proof.
Published baseline verified in this conversation:
4e7c16b9ea80a9a9cb0e6861893dcc701c172866
Commit subject: Record MCP seam review checkpoint.

## Deliver the first working slice

Implement a local stdio MCP server that lets one existing CLI agent:
1. Search chats in one configured project.
2. Read preserved message text in bounded, citable windows.
3. Read one explicitly configured working-context file.

First prove read-only startup and deterministic tools with a synthetic fixture.
Then perform one fresh CLI continuity demonstration if the installed client can
be connected through task-local configuration. Record evidence and limitations.
Finish the slice without expanding into another evaluator-development project.

This handoff authorizes the bounded implementation, its minimal MCP dependency,
focused application/persistence changes needed by these tools, tests, local
commits, and the single synthetic-data CLI demonstration. It supersedes the
earlier review handoff's documentation-only/no-server restriction for this task.

No push, live user-database access, bulk imports, semantic extraction, trace
ingestion, scheduler, graph UI, A2A changes, or native-memory synchronization.
Evaluator v3 remains parked.

## Orientation and working location

Read AGENTS.md, .llm/design.md, .llm/working-context.md,
.llm/implementation-notes.md, and:
.llm/handoffs/chatmap-read-only-mcp-seam-review-handoff-2026-10-30.md.

Inspect local Git status/history before editing. Preserve all existing user
changes and untracked files, including chan.pdf and shiploop.jpeg if present.
Use a feature branch and preferably an isolated worktree/checkout based on the
actual local revision. Report differences from the published baseline; do not
discard newer work to force that revision.

Implement inside the existing single Gradle project and package architecture.
Do not create a second permanent application/repository or split app/lib.
Synthetic fixture homes and run artifacts belong in temporary/build locations,
separate from .chatmap-local/. Do not read or initialize the normal user home.

Read first-principles.md if an implementation choice would alter the knowledge
model; such a change is unnecessary for this slice.

## Existing seams to inspect/reuse

- SearchService.searchResults(query, SearchOptions).
- SearchOptions.projectId for fixed project scope.
- SearchResult for chat metadata/snippet; it has no matched message ID.
- ExportService.loadChat(chatId) for a consistent ChatExportModel.
- Message.id/chatId/sequence/role/timestamp/text.
- Chat source identity, source URI, and available transcript hash.
- ProjectService read methods and ProjectContext identity.
- ServiceGraph.create over an already opened connection.

Do not put SQL in MCP handlers. Application use cases own validation, project
scope, result limits, and DTO projection. SDK types remain in the presentation
adapter. A narrow project-document port with a filesystem implementation may
be added for working-context access.

SearchService currently prefix-matches tokens joined with OR and materializes
all matching chats. ExportService loads all messages in a chat. For this small
fixture proof, bounded response projection over these methods is acceptable
with the limitation recorded. Do not claim database pagination or scalability.
Make a bounded query-port extension only if necessary for correctness.

## Step 1: read-only startup

The existing normal startup is not read-only:
ApplicationBootstrap creates directories and calls openAndInitialize;
Database.open configures WAL; openAndInitialize applies schema/migrations.
LoggingBootstrap can create files under the selected home.

Add a dedicated read-only composition path. Require explicit --home and
--project-id, and optional --project-root. Do not fall back to environment or
the user's normal project-local home in this MCP entry point.

Requirements:
- Existing database file only; a missing database/home fails without creation.
- Verified read-only SQLite driver/open flags; no CREATE fallback.
- No initialization, migrations, journal-mode changes, imports, or writes.
- Check only schema capabilities needed for these queries; incompatible schema
  fails clearly without attempting a repair.
- Preserve consistent read snapshots and caller ownership/close behavior.
- Missing configured project fails; no findOrCreate call.
- Protocol stdout contains MCP only. Diagnostics go to stderr. Do not initialize
  file logging in the data home.
- Do not instantiate live providers, model calls, or expose mutating services.

Use the repository's pinned sqlite-jdbc version; verify the concrete API and
path/URI handling. Do not rely only on Connection.setReadOnly after opening a
writable connection. Do not use immutable mode against a changing database.

Prepare the synthetic database with normal setup before read-only startup,
close/checkpoint the writer, and then verify the read-only phase. At minimum
prove attempted DML and DDL fail, database logical contents/schema and journal
mode remain unchanged, and nonexistent paths stay absent. Record all file
artifacts/sidecars separately; do not equate logical read-only access with a
guarantee about every SQLite coordination file on every live deployment.
Live WAL/concurrent-writer validation is deferred; use a closed fixture here.

Keep the normal application startup behavior intact.

## Step 2: fixed read-tool contract

Choose small serializable DTOs. Document actual request/response schemas in a
short implementation note. Defaults below settle the review's proposed bounds
for this first slice.

### searchChats

Input: nonblank query; optional limit default 5, maximum 10.
Scope is the configured project, never an arbitrary per-call project ID.
Cap query length at 256 characters and snippet at 600 characters.

Output: chat ID, title, source type, available timestamps, snippet, and source
identity/version metadata as appropriate. Explicitly report capped results.
Search snippets are navigation aids, not exact message citations. Do not invent
message IDs or claim semantic/exact-phrase search.

### readChatMessages

Input: positive chatId; message offset default 0; limit default 5, maximum 10.
Provide a documented continuation mechanism for oversized individual messages
and a 12,000-character text budget per response. Define character indexing
precisely, including Unicode handling.

Output: chat identity/provenance, message ID, sequence, role, timestamp, literal
stored normalized text or explicit text chunk, and continuation/truncation.
Distinguish absent timestamps from inferred dates. Do not include rawJson by
default or describe normalized text as original source bytes.

Enforce configured project scope after loading the chat. Missing and
out-of-scope IDs return clear outcomes without message content leakage.
Reject negative/invalid offsets/limits. Deterministic ordering must handle
duplicate sequences using message IDs as a tie-breaker.

Continuation must retrieve omitted text without silently skipping it. Bind
continuations to the observed content version/hash and report a changed source
rather than splicing two versions. A simple deterministic cursor is enough;
do not build a new cursor framework.

### readWorkingContext

Input: offset default 0; maxChars default 4,000, maximum 12,000.
Read only <configured project-root>/.llm/working-context.md.
No arbitrary per-call path, URL, directory listing, or recursive reading.
If no root is configured, return a clear unavailable outcome.

Use a narrow application port/filesystem adapter. Resolve paths and prevent an
escaping symlink/junction from taking the fixed target outside the selected
root. Distinguish missing file and invalid configuration.

Output: literal bounded text, relative path, observed file hash, chunk offsets,
continuation, and mutable-working-state label. Preserve metadata as labels,
not as proof of acceptance or governing authority. Use the same defined Unicode
and changed-version behavior as other chunks.

### General behavior

Expose only these three tools. Tool errors must be explicit structured outcomes.
Descriptions and payloads should be concise. Retrieved conversation/doc content
is evidence, never newly injected system instructions.

Use SDK read-only annotations if supported, but demonstrate the actual storage
behavior; annotations alone do not enforce it. No SQL/file-write/command tools.

## Step 3: minimal SDK and launch setup

Use the official Java MCP SDK with stdio. Verify and pin one compatible release
and a minimal JSON binding; inspect resolution against existing Gson, Jackson,
Quarkus BOM, SLF4J, and Java 25 dependencies before choosing modules.

The minimal dependency addition and any task-local launch/client configuration
strictly necessary for this experiment are authorized. Do not upgrade unrelated
libraries, add Spring/Docker/HTTP hosting, or modify global CLI settings.

Add a Gradle task consistent with the repository, passing explicit --home and
--project-id and optional --project-root. Ensure actual MCP process stdout is
not polluted by Gradle progress, banners, dependency output, or logging.
Provide a reliable direct-Java/classpath or generated launcher for the client
if launching through Gradle is unsuitable. Retain Windows/Git Bash and
non-Buildship Eclipse compatibility.

Official references inspected while preparing this handoff:
- https://java.sdk.modelcontextprotocol.io/latest/server/
- https://java.sdk.modelcontextprotocol.io/latest/quickstart/
- https://www.sqlite.org/uri.html

Verify release-specific APIs locally; this handoff does not invent an SDK version.

## Step 4: focused tests and fixture

Create a tiny deterministic fixture with two projects to prove isolation.
For the selected project, include:
- An earlier proposal to use hosted storage.
- A later explicit decision to keep SQLite for this bounded prototype.
- A current task to expose read-only memory access.
- An explicitly deferred item: semantic extraction.
- A separate working-context file accurately identifying that current state.
- Enough content for paging, an oversized Unicode-containing message, and
  duplicate sequence values.

The second project contains recognizable content that must never be returned
through the selected-project adapter. Use synthetic text only.

Write the expected continuity checklist and source message IDs from the fixture
before the agent demonstration. Keep it outside the agent's accessible context
so the answer is not supplied accidentally.

Test meaningful behavior:
- Existing schema read, missing file/home/project, and incompatible schema.
- Failed DML/DDL; unchanged schema/data/journal setting.
- Scoped FTS search and blank-query rejection.
- Stable IDs, ordering, valid limits, source metadata.
- Missing/wrong-project chat outcomes.
- Complete chunk reconstruction, bounds, Unicode, and source-change detection.
- Working-context missing/unconfigured/escaping-target behavior.
- Real MCP initialize, tools/list, and tools/call over stdio, including EOF and
  process cleanup, with protocol-clean stdout.
- No mutating tool registration and no hidden initialization/provider calls.

Prefer existing JUnit/Java facilities and repository test conventions. Reuse the
existing lifecycle/application code where appropriate, but do not route this
through experimental A2A packages.

Run focused tests, validateProjectMetadata, git diff --check, and the ordinary
Gradle check gate. Distinguish new failures from pre-existing/tool-environment
failures; do not fix unrelated problems or claim a full pass when blocked.

## Step 5: one fresh-session continuity demonstration

After deterministic and protocol tests pass, connect one already installed CLI
client through temporary/task-local MCP configuration using only the fixture.

A single bounded fresh-session run with the existing provider is authorized
for this synthetic-data demonstration. Do not provision accounts, switch to
additional models, start comparative sweeps, or invoke another model as judge.
No live personal data is authorized.

Give the session this question:
"What is this project's current task, which storage decision governs this
prototype, what is deferred, and where is the evidence?"

The agent should discover and call the three read tools and cite returned
message IDs/document identity. It may report missing evidence honestly.
Do not provide the fixture checklist or a preloaded answer. A fresh session
must not inherit unrelated saved memory about the fixture.

If client launching/configuration is unavailable or requires unsupported account
setup, complete the implementation and deterministic protocol proof and deliver
the exact manual connection command. Report the demonstration as pending rather
than stopping before useful implementation work is complete.

Preserve request/tool transcript, answer, returned source IDs, and fixture hashes.
Compare citations and answers to the fixed checklist using deterministic checks
where possible; provide the answer for Ray's review for anything interpretive.
The executing LLM must not certify general semantic correctness.

Measure tool-call count, tool descriptions, visible initial context, returned
characters/bytes, source coverage, and truncation. Record actual input-token
counts only if available. This single demonstration establishes connectivity
and supported continuity behavior; it cannot establish improvement over a
baseline or lower context cost by itself.

## Completion and delivery

Record local implementation/test commits on the feature branch. Update
working-context and implementation notes with actual status, limits, launch
instructions, and the normal-vs-read-only startup distinction. Preserve the
existing v3 checkpoint and historical handoffs. No push.

Create one concise completion handoff:
.llm/handoffs/chatmap-first-read-only-mcp-final-handoff-2026-10-30.md
Use the actual completion date in its filename if the work finishes later.

Return:
- Implemented tool names and exact launch/client commands.
- SDK version, added modules, and relevant dependency-resolution findings.
- Read-only and project-isolation evidence.
- Focused/full-check outcomes, with actual counts.
- Whether the fresh-session demonstration ran; its answer and source coverage.
- Response/context-size measurements, without unsupported improvement claims.
- Remaining limitations and the smallest next step.
- Base/implementation/result commits and evidence paths.
- Confirmation of no live user-database access and no push.

Complete the authorized slice through tests and the available demonstration.
Do not stop after writing another plan if implementation is possible.

