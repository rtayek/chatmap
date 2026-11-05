# ChatMap cold-start handoff

Date: 2026-11-04 (America/Los_Angeles)
Status: completed checkpoint; pause and reassess before starting new work.

## Start here

Ray is retiring a long ChatGPT conversation. The first read-only MCP slice is
implemented, reviewed, corrected, merged into local master, and reported
synchronized with origin/master. No implementation task is currently pending.
Do not restart old experiments or assume this handoff authorizes a new model
run, live database access, or further development.

Before repository work, read its current AGENTS.md, then the directly relevant
project documents. Read .llm/working-context.md for current operational state;
read .llm/design.md for architectural work. Do not recursively ingest handoffs.
Repository files may have changed since this checkpoint; inspect actual state
before changing anything.

Repository: https://github.com/rtayek/chatmap
Primary local checkout: C:/Users/ray/eclipse-workspace/chatmap
Default branch: master.

## Chosen direction

Workers and external agent runtimes own execution. ChatMap owns continuity:
preserved conversation evidence, project context, and provenance. It is a
durable ledger, not a general agent orchestrator.

The immediate direction was a small MCP interface to existing reads, answering:
"What were we doing, what was decided, and where is the evidence?"
Native agent memory folders may coexist with ChatMap. Do not adopt the earlier
proposed sovereign control-plane pivot as an implemented architectural decision.
Semantic extraction, A2A productization, scheduling, graph editing, and automatic
native-memory synchronization remain outside the completed slice.

## Completed MCP implementation

Branch: feature/read-only-mcp.
Feature worktree: C:/Users/ray/eclipse-workspace/chatmap-read-only-mcp-2026-10-30

Three stdio tools:
- searchChats: configured-project search, prefix-token OR semantics; default
  limit 5, maximum 10; query capped at 256 Unicode code points; snippets at 600.
- readChatMessages: project-scoped stored normalized text with exact message
  identity; default limit 5, maximum 10; 12,000-code-point text budget; stable
  sequence/message-ID ordering; version-bound continuation for oversized text.
- readWorkingContext: only the configured project's .llm/working-context.md;
  default chunk 4,000, maximum 12,000 code points; observed hash, real-path
  containment, and mutable-working-state label.

Dedicated startup requires explicit --home and --project-id; --project-root is
optional. It opens an existing database using SQLite read-only flags plus
query_only, without normal initialization, migrations, provider/model startup,
or file logging. Normal application startup remains unchanged.
Official Java MCP SDK 2.1.0, mcp-core and mcp-json-jackson2; Java 25/Gradle.
Search and message reads still materialize all matches/messages before bounding
their responses. This is not SQL pagination or a scalability result.

Build distribution with the Gradle wrapper's installDist task. Direct Windows
launch (replace placeholders with explicitly selected paths):

```text
java -cp "build\install\chatmap\lib\*" chatmap.presentation.mcp.ReadOnlyMcpServerCli --home <existing-home> --project-id <id> [--project-root <project-root>]
```

This records the launch interface; it does not authorize using a live home.

## Review and fixes

Implementation commit: 6f1aee093b4162858d8385bdedddc61e9c805c93
Completion handoff commit: 039cef1afaf06fd1706a6f8850fb5122809f6d8a
Reviewed fix commit: b8cd525 (full SHA not supplied in the conversation).

The review found and reproduced two defects:
1. An empty message at the requested offset was silently skipped. The fix
   preserves the empty message's ID, role, timestamp, and continuation.
2. Gson's Map<String,Object> round-trip converted integer IDs to doubles.
   ID 9007199254740993 rounded to 9007199254740992. The fix recursively
   preserves integral values as Long. A stdio regression uses chat ID
   9007199254740993 and nested message ID 9007199254740995.

Both fixes and the added tests were reviewed. Focused independent probes against
the supplied source confirmed empty-message preservation and exact large IDs.
The full Java 25 Gradle suite was not independently rerun in ChatGPT's review
environment.

Local agent's final reported checks: 652 tests, 648 passed, 4 skipped, zero
failures; validateProjectMetadata and git diff --check passed. Earlier figures
of 650 total tests describe the implementation before the two new tests.

Ray's terminal showed master and feature/read-only-mcp both at b8cd525, with
master initially three commits ahead. His later status showed master up to
date with origin/master and a clean worktree. On that evidence the work was
treated as merged and pushed; no new GitHub inspection was performed afterward.

## Demonstration evidence reviewed

One fresh Codex CLI synthetic-data run made four completed ChatMap calls:
readWorkingContext, searchChats, and readChatMessages for chats 101 and 102.
The transcript contains no other tool calls. Calls were initiated in parallel
pairs; do not infer strictly serial execution from this list.

The answer correctly cited message 1002 for the later SQLite decision, message
2001 for the current MCP task, and message 2002 for deferred semantic extraction.
It distinguished message 1001 as the earlier hosted-storage proposal, and cited
.llm/working-context.md with its exact observed hash.

Independent review confirmed the saved answer matches the transcript, checklist
hash matches its manifest, document text matches its observed/manifest hash,
and the original run's text and structured results agree for its small IDs.
All four required source references were present.

Working-context SHA-256:
e4ee4c187662a1b22b334a85d3cc727448cd6ff3eb58e50a7cb7e73d10840322
Reported database SHA-256, unchanged before/after the local demonstration:
cc335bbe2d9029fc3688fe8db93d80ec2924539098e503e3c9b8bfcdd1516952
The database itself was not included in the four-file evidence ZIP, so its
before/after integrity was not independently established by transcript review.

Four text payloads totaled 15,552 code points / 22,336 UTF-8 bytes, independently
recomputed. The answer was 817 bytes. Codex reported 76,087 input tokens,
60,160 cached input tokens, and 397 output tokens. These are client-reported
totals, not a tool-only token attribution or efficiency comparison.

The current-chat response unnecessarily entered oversized message 2003, filled
the 12,000-code-point text budget, and returned a continuation cursor. Text and
structured results also duplicate content. No reduction in context cost was
demonstrated. The run proves bounded connectivity and supported answers on a
synthetic fixture, not production quality or general semantic reliability.

SQLite created coordination sidecars (32,768-byte shm and empty wal). Database
content read-only does not mean zero filesystem artifacts. No live user data
was accessed in this slice; live WAL/concurrent-writer behavior remains untested.
The demonstration was not rerun for the review fixes.

## Where the evidence lives

Repository completion document:
.llm/handoffs/chatmap-first-read-only-mcp-final-handoff-2026-10-30.md
Launch/implementation notes: .llm/implementation-notes.md

Original runtime evidence remains ignored build output in the feature worktree:
build/mcp-demonstration-2026-10-30/{codex-events.jsonl,codex-answer.md,
fixture-manifest.json,expected-checklist.json}
The original JSONL is UTF-16LE with a BOM; decode accordingly, preserving bytes.
Keep that worktree and evidence until their preservation is settled.

Files supplied and reviewed in this conversation:
- chatmap-mcp-review.zip: original committed source checkpoint.
- chatmap-mcp-demonstration-2026-10-30.zip: four original evidence files.
- chatmap-mcp-review-fixes.patch: corrections, regression tests, and report update.

## Parked semantic evaluation

Evaluator v3 is complete according to Ray's local completion report, in the
isolated sibling chatmap-semantic-evaluator-v3-2026-10-20.
Specification: 7396843f76ba05fbdc725724bb8ea03291d5a7b2
Executable freeze: a671e4581ffb0e00c0877ba2604eb8f97247b036
Receipt HEAD: d0aab8912de6dd9445b33ffeefb472bbb176e061
Reported tests: B controls 64/64, v3 regressions 88/88, v2 safeguards 80/80,
v1 regression 286/286, structural 19/19, warnings 7/7, inventory 29/29.
Matching remains lexical/fixture-declared; four explicit grouping forms and
explicit actor candidates are supported. The B corpus is development evidence,
not blind independent validation or model-ranking evidence. No v4 or new model
run is the current next action. A2A work is also parked.

## GUI reminder from Ray

When returning to the GUI, add prominent color indicators showing the active
operation/process, its stage, and whether it is running, waiting, finished, or
failed. Pair colors with clear text labels and readable contrast so users can
tell what is happening. Ray explicitly requested this reminder. Before this
handoff it was recorded only in the conversation, not added to the repository;
no scheduled automation was created.

## Next conversation

Ray chose to pause, recover, and look around after completing the MCP slice.
Start with a brief orientation and discuss what would be useful in actual use.
Do not automatically launch another experiment. Potential future questions are
response/context overhead and a carefully bounded real-data snapshot test;
neither is authorized by this handoff. A live-read test would first need an
explicit data scope, backup/concurrency safeguards, and provider-data policy.

Ray prefers concise communication, paste-safe Bourne shell commands for Git
Bash on Windows, Java/Gradle/Eclipse, and lowercase dashed filenames. Preserve
unrelated work and user files; do not push without authorization for that work.

This file is a downloadable conversation handoff. It has not been placed in
Ray's local repository by ChatGPT.
