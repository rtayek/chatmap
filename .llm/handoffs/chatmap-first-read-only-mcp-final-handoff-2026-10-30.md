# ChatMap first read-only MCP final handoff

Date: 2026-10-30 (America/Los_Angeles)
Status: authorized synthetic-data slice complete; ready for review; not pushed.

## Result

ChatMap now has a dedicated local stdio MCP composition path exposing exactly
`searchChats`, `readChatMessages`, and `readWorkingContext`. The path requires
an existing home and configured project, opens only the existing SQLite
database in read-only/query-only mode, and does not run normal initialization,
migrations, providers, models, imports, or file logging.

The work was isolated in
`C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30` on branch
`feature/read-only-mcp`. The requested handoff named baseline `4e7c16b`, but
the actual clean local baseline was the newer `a2c52ed`, whose only intervening
commit added the implementation handoff. No newer work was discarded.

Base commit: `a2c52edbe97df86c034fde3bdfbb9e3350ebf6d7`
Implementation and test commit:
`6f1aee093b4162858d8385bdedddc61e9c805c93` (`Add bounded read-only MCP tools`)
Completion-handoff commit: the subsequent local commit containing this file;
its exact SHA is reported in the completion response because a commit cannot
contain its own SHA.

## Launch contract

Build the distribution:

```text
gradlew.bat installDist
```

Direct Windows launch, suitable for an MCP client because Gradle output cannot
pollute stdout:

```text
java -cp "build\install\chatmap\lib\*" chatmap.presentation.mcp.ReadOnlyMcpServerCli --home <existing-home> --project-id <id> [--project-root <project-root>]
```

The equivalent `readOnlyMcpServer -Pargs="..."` JavaExec task is available for
manual use. `readOnlyMcpFixture -Poutput=<new-build-directory>` creates the
isolated fixture and refuses an existing destination.

The fresh demonstration used Codex CLI 0.166.0 with task-local inline config;
it did not change global Codex configuration. The exact effective MCP process
configuration was:

```text
command = C:/Program Files/Java/jdk-25/bin/java.exe
args = -cp C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30/build/install/chatmap/lib/* chatmap.presentation.mcp.ReadOnlyMcpServerCli --home C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30/build/mcp-demonstration-2026-10-30/home --project-id 1 --project-root C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30/build/mcp-demonstration-2026-10-30/project
```

The client invocation was:

```text
codex.cmd exec --ignore-user-config --ephemeral --skip-git-repo-check --sandbox read-only --json --color never -C C:\Users\ray\AppData\Local\Temp\chatmap-mcp-demo-session-2026-10-30 -c "mcp_servers.chatmap.command='C:/Program Files/Java/jdk-25/bin/java.exe'" -c "mcp_servers.chatmap.args=['-cp','C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30/build/install/chatmap/lib/*','chatmap.presentation.mcp.ReadOnlyMcpServerCli','--home','C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30/build/mcp-demonstration-2026-10-30/home','--project-id','1','--project-root','C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30/build/mcp-demonstration-2026-10-30/project']" -c mcp_servers.chatmap.startup_timeout_sec=20 -c mcp_servers.chatmap.tool_timeout_sec=30 --output-last-message C:\Users\ray\eclipse-workspace\chatmap-read-only-mcp-2026-10-30\build\mcp-demonstration-2026-10-30\codex-answer.md <prompt>
```

The exact prompt, passed as the final argument in place of `<prompt>`, was:

```text
Use only the chatmap MCP tools; do not use shell, filesystem, web, saved memory, or any other tools. Discover the available chatmap tools and answer: What is this project's current task, which storage decision governs this prototype, what is deferred, and where is the evidence? Cite exact returned message IDs and the working-context relative path plus observed hash. Do not infer claims not supported by those sources.
```

It was 420 Unicode code points / 420 UTF-8 bytes.

## SDK and dependency resolution

The implementation pins the official Java MCP SDK BOM at `2.1.0` and adds only
`mcp-core` and `mcp-json-jackson2`. It uses the SDK stdio transport and
read-only, non-destructive, idempotent, closed-world tool annotations. No MCP
Spring, Docker, or HTTP server module was added.

Gradle resolution retained repository constraints: Jackson databind resolves
to `2.22.0` under the Quarkus 3.39.1 BOM and SLF4J API to `2.0.18`. The MCP
JSON module's Jackson requests resolve to that existing newer version. The
server compiled and ran on Java 25.

## Deterministic evidence

Focused tests: 12 run, 12 passed, 0 skipped across read-only database opening,
the fixed document adapter, bounded application reads, and real MCP stdio
initialize/list/call/EOF cleanup.

Full `gradlew.bat --no-daemon check`: PASS, 650 tests run, 646 passed, 4
skipped, 0 failures/errors. Checkstyle, PMD, SpotBugs, and JaCoCo tasks passed.
The existing JavaFX unnamed-module warning and Gradle deprecation notice were
informational.

`gradlew.bat --no-daemon validateProjectMetadata`: PASS, 5 pilot documents and
6 required paths. `git diff --check`: PASS.

Read-only tests prove DML and DDL rejection, byte-identical database content,
unchanged logical schema and journal mode, missing-path non-creation, and clear
failure for incompatible schema. Two-project tests prove FTS and source reads
are confined to configured project 1; recognizable project-2 content never
appears. Missing and out-of-scope chats return outcomes without message text.
Unicode reconstruction, duplicate-sequence ordering by message ID, bounded
responses, version-bound cursors, changed-source outcomes, and fixed-document
real-path containment are covered.

The fixture database hash before and after the CLI demonstration remained
`cc335bbe2d9029fc3688fe8db93d80ec2924539098e503e3c9b8bfcdd1516952`.
The working-context hash remained
`e4ee4c187662a1b22b334a85d3cc727448cd6ff3eb58e50a7cb7e73d10840322`.
SQLite created `chatmap.db-shm` (32,768 bytes) and an empty `chatmap.db-wal`
during the read-only WAL access. This is recorded separately: logical content
and the database file were unchanged, but the slice does not claim zero
coordination-file activity on every deployment.

## Fresh-session demonstration

The run completed with exit code 0 and used only four ChatMap MCP calls:

1. `readWorkingContext(maxChars=12000)`
2. `searchChats(query="storage prototype deferred current task", limit=10)`
3. `readChatMessages(chatId=102, limit=10)`
4. `readChatMessages(chatId=101, limit=10)`

The answer correctly reported the current read-only MCP task, the later SQLite
decision, and deferred semantic extraction. It cited all checklist evidence:
message IDs 1002, 2001, and 2002 plus `.llm/working-context.md` and its exact
observed hash (4/4 required sources). It additionally distinguished message
1001 as the earlier hosted-storage proposal and labeled the document mutable
working-state evidence.

The three top-level tool descriptions total 230 Unicode code points / 230
UTF-8 bytes. The four text-content payloads total 15,552 code points / 22,336
UTF-8 bytes. The recorded compact result objects, including both text and
structured-content copies, total 45,318 UTF-8 bytes. One result was truncated:
the current-chat read returned its 12,000-code-point budget and a continuation
cursor after also entering oversized synthetic message 2003. The final answer
was 817 code points / 817 bytes. Codex reported 76,087 input tokens, including
60,160 cached input tokens, and 397 output tokens. The client does not expose a
separate token count for the 420-code-point prompt, schemas, and each result.

This single fixture run establishes connectivity and supported continuity only.
It is not a comparison, an efficiency claim, independent semantic validation,
or evidence of production quality.

## Evidence paths

All runtime evidence is ignored build output and contains synthetic data only:

* `build/mcp-demonstration-2026-10-30/fixture-manifest.json`
* `build/mcp-demonstration-2026-10-30/expected-checklist.json`
* `build/mcp-demonstration-2026-10-30/codex-events.jsonl`
* `build/mcp-demonstration-2026-10-30/codex-answer.md`
* `build/test-results/test/`
* `build/reports/tests/test/`
* `build/reports/jacoco/test/`

## Limits and smallest next step

Search and chat loading still materialize all matches/messages before the MCP
projection bounds them; this is not database pagination or a scalability
proof. Search is prefix-token OR, not semantic or exact-phrase search. Returned
message text is stored normalized text, not original bytes. Live WAL behavior,
concurrent writers, backup policy, and user data were not tested. Structured
and text result copies plus the irrelevant oversized message made this one run
large; no lower-context-cost claim is justified.

The smallest next step is review. If separately authorized afterward, first
reduce duplicated result context and stop retrieval once evidence is sufficient;
then define safeguards for a bounded live-read feasibility test before touching
any live home. Do not add semantic extraction, scheduling, A2A productization,
or native-memory synchronization as part of that step.

No normal `.chatmap-local` or other live user database was accessed. No model
other than the one fresh Codex CLI demonstration was invoked. Nothing was
pushed.

## Post-review corrections

The two review findings were corrected on the same branch after the completion
checkpoint. `readChatMessages` now returns a requested zero-length message as a
normal message chunk, including its ID, role, timestamp, and empty text. Tests
cover both an initial explicit offset and a cursor continuation positioned at
the empty message.

MCP structured content now converts integral JSON values to `Long` rather than
round-tripping them through Gson's default `Double` map representation. A real
stdio protocol test reads chat ID `9007199254740993` and message ID
`9007199254740995`, and verifies that the text and structured results retain
the same exact values.

The focused `MemoryReadServiceTest` and `ReadOnlyMcpProtocolTest` run passed 9
tests with no failures or skips. The full `gradlew.bat --no-daemon check` passed
652 tests: 648 passed, 4 skipped, and 0 failed. `validateProjectMetadata` and
`git diff --check` also passed.

The model demonstration was not rerun. The four original evidence files were
preserved and packaged at
`C:/Users/ray/Downloads/chatmap-mcp-demonstration-2026-10-30.zip`; its entries
retain their `build/mcp-demonstration-2026-10-30/` paths. The local review-fix
commit is the subsequent commit containing these corrections; its exact SHA is
reported in the completion response.
