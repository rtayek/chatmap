# ChatMap — Outstanding Review Findings Handoff
*August 10, 2026*

## Context

A series of architecture and code-smell reviews were run against this repo
over several sessions. Several findings have already been fixed (duplicate
imports of `PromptService` recordings, `ChatConsolidatorCli` discarding its
work, connection thread-safety, a duplicate-project race, a Chrome tab leak —
see `git log` for the commits). This handoff lists everything still open.

Items are ordered roughly by consequence, not by ease. Each item names the
files involved, what's wrong, why it matters, and a suggested direction — not
a mandated implementation. Use your own judgment on the actual fix; flag
anything where the suggested direction conflicts with something you find in
the code that this handoff didn't account for.

Work these independently unless a dependency is noted. Commit each one
separately with a message that states the problem being fixed, following the
style already established in this repo's recent history (see commits like
`7884b93` and `6ff77ac` for the tone/detail level to match).

---

## 1. Plain text and Markdown imports have no deduplication

**Files:** `src/chatmap/service/ImportService.java`, `src/chatmap/domain/Source.java`

`persistInTransaction` only dedups a chat two ways: by `externalConversationId`
if present, or by content hash — but only when `incoming.source().isProvider()`
is true. `Source.plainText` and `Source.markdown` have `isProvider() == false`
and never have an `externalConversationId` (there's nothing in a `.txt`/`.md`
file to derive one from). Re-importing the identical file always creates a
full duplicate `Chat` + `Message` rows, unconditionally, forever.

**Why it matters:** data integrity — this is the one place "import" isn't
idempotent, for two of three file-based formats.

**Suggested direction:** drop the `isProvider()` gate on the content-hash
check, or make content-hash dedup apply to *any* source lacking an
`externalConversationId`, not just provider sources. Check `ChatContentHasher`
to confirm hashing is meaningful for these formats (it should be — it hashes
role+text pairs, format-agnostic). Add regression tests: import the same
`.txt` and `.md` file twice, assert one `Chat` row exists both times.

---

## 2. Live "latest chat" tries fragile web scraping before reliable local readers

**File:** `src/chatmap/backend/providers/DefaultChatProviders.java`

`ordered()` puts `ClaudeWebChatProvider`, `ChatGptWebChatProvider`, and
`GeminiWebChatProvider` (CDP browser automation against claude.ai/chatgpt.com/
gemini.google.com) ahead of the three local CLI-history readers. The class
comment says this is deliberate: web is "the 'latest live chat' this feature
was built around." The `ClaudeWebAdapter` selectors are documented in-code as
best-effort and already broke once against a DOM change (see earlier commit
history). This also runs against `first-principles.md`'s stated preference
for deterministic processing over scraping.

**Why it matters:** operational reliability — the default path for a core
feature depends on three vendors' unversioned web UIs staying stable.

**Suggested direction:** this is a product decision as much as a bug — don't
just flip the order without checking whether that changes user-visible
behavior in a way that wasn't intended (CLI history readers may return older
or different sessions than the live web chat). At minimum: make the ordering
configurable, or have web providers surface *why* they failed (see item 3)
so a silent DOM-selector break is distinguishable from "not logged in."

---

## 3. One background executor conflates fast DB work with slow external I/O

**Files:** `src/chatmap/app/SerializedTaskExecutor.java`, `src/chatmap/app/ChatMapRuntime.java`, `src/chatmap/ui/ChatMapApp.java`

Search, chat-list loading, import, summarization (shells out to the `claude`
CLI, can take minutes per its own doc comment), and live web fetches (drives
a real Chrome via CDP) all funnel through one single-threaded
`SerializedTaskExecutor`. A long-running summarize or live-fetch call
head-of-line-blocks every unrelated DB-only action behind it.

**Why it matters:** operational reliability, extensibility — this gets worse
as more AI-backed features are added, not better.

**Suggested direction:** split into two lanes: a DB-serialized executor
(single-threaded, matches the storage layer's connection-locking model) and a
separate pool (or per-call thread) for AI-backend/CDP calls that don't hold
the DB lock while waiting on external I/O. Since storage locking is now
structural (`synchronized(conn)` in every repository method, see item 8),
the DB-serialization guarantee doesn't actually require funneling everything
through one executor anymore — that dependency may already be looser than
the current design assumes. Verify before changing.

---

## 4. `ExportService.loadProjectHandoff` isn't a consistent snapshot

**File:** `src/chatmap/service/ExportService.java`

`loadProjectHandoff` makes four sequential, independently-locked repository
calls (`projects.findById`, `chats.findByProject`, `messages.findByChatIds`,
`tags.findByChatIds`). Each is individually thread-safe now (per-method
`synchronized(conn)`), but the lock isn't held *across* the four calls, so a
write landing between any two of them produces a torn export. `design.md`
calls this export "structured extraction... deterministic" — that guarantee
currently only holds when nothing else is writing at the same moment.

**Why it matters:** data integrity — this is the one export explicitly sold
as deterministic, and the one not wrapped in a transaction.

**Suggested direction:** wrap the four calls in
`chats.transactions().inTransaction(() -> { ... })` (see
`TransactionRunner.java` — this is exactly the API it exists for; other
call sites like `ChatRepository.delete` already use this pattern). Add a
test that mutates project data from a second connection mid-export and
asserts the handoff is internally consistent.

---

## 5. AI-backend-generated chats share `Source` identity with genuine imports

**Files:** `src/chatmap/backend/ai/StandardCliBackend.java`, `src/chatmap/backend/ai/AiBackend.java`, `src/chatmap/domain/Source.java`

`StandardCliBackend.source()` maps the Claude CLI backend to `Source.claudeCode`
— the same value `ClaudeCodeHistoryProvider` uses for real imported
`~/.claude/projects/*.jsonl` session transcripts. `AiBackend`'s default
`source()` returns `Source.plainText`, shared with literal text-file imports.
A two-message Q&A run through `PromptService` is indistinguishable in storage
from a real multi-turn session pulled from disk.

**Why it matters:** system responsibilities/boundaries, data integrity — the
`source` column no longer answers "where did this come from" unambiguously
for the app's own generated content, and this also affects item 1's dedup
scoping and any future filtering/grouping by provider.

**Suggested direction:** either add a new `Source` value (or a separate
boolean/enum column, e.g. `originatedBy: IMPORTED | GENERATED`) so
PromptService recordings are provenance-distinct from real imports of the
same provider. This is a schema change — needs a migration in
`Database.applyMigrations` (see recent migrations there for the pattern) and
touches `Chat`, `ChatRowMapper`, and anywhere that filters by `Source`.

---

## 6. `findMostRecent()` ignores the `archived` flag

**File:** `src/chatmap/storage/ChatRepository.java`

`findMostRecent()` — the fallback `LiveChatFetchService.resolve()` uses when
no live provider succeeds — is `ORDER BY importedAt DESC, id DESC LIMIT 1`
with no `WHERE archived = 0`. An explicitly archived chat can still be
silently selected as "the latest chat" and acted on by automated flows like
summarization.

**Why it matters:** correctness — a stored user decision ("archive" means
"deprioritize this") is ignored by the one query that decides what counts as
"the chat to act on."

**Suggested direction:** add `WHERE archived = 0` to the query. Check callers
of `findMostRecent()` to confirm none of them actually want archived chats
included (unlikely, but verify before changing). Small, low-risk, good
first fix if you want to warm up before the bigger items.

---

## 7. Nine near-identical provider/adapter classes

**Files:**
`src/chatmap/backend/providers/ClaudeCodeHistoryProvider.java`,
`CodexCliHistoryProvider.java`, `GeminiCliHistoryProvider.java`,
`src/chatmap/backend/web/ClaudeWebChatProvider.java`,
`ChatGptWebChatProvider.java`, `GeminiWebChatProvider.java`,
`ClaudeWebAdapter.java`, `ChatGptWebAdapter.java`, `GeminiWebAdapter.java`

Each triplet is the same class copy-pasted three times, differing only in
provider name, `Source` constant, root path / URL, and format-specific
parsing. Diffing `ChatGptWebChatProvider.java` against
`GeminiWebChatProvider.java` shows the only differences are string literals.

**Why it matters:** extensibility — every new provider means copying a file
and hoping every string that needed to change, changed.

**Suggested direction:** this is a large refactor — don't attempt all three
triplets in one pass. Start with the CLI-history providers (smallest, least
risky, no CDP involved): extract a `LocalCliHistoryProvider` base class or a
config-record approach (`ProviderConfig(name, source, rootPath, parser)`)
that the three become thin declarations of. Confirm test coverage for all
three exists and passes before and after. Leave the web-provider and
web-adapter triplets for a follow-up once this pattern is validated — they're
riskier to refactor blind since they're harder to test without a live
browser.

---

## 8. `ChatMapApp` is a 554-line, 62-method god class

**File:** `src/chatmap/ui/ChatMapApp.java`

Mixes widget/layout construction, dialog and file-chooser prompts,
background-task orchestration (`runInBackground` wrapping), and detail
rendering in one class. `ChatMapViewBuilder` and `ChatDetailRenderer` already
exist as separate files, showing this split is a known-good pattern the rest
of the class never got.

**Why it matters:** extensibility, maintainability of the UI layer.

**Suggested direction:** extract dialog/prompt helpers (`requestName`, the
various `showAndWait` calls) into a `ChatMapDialogs` class. The background-task
wrapping is already half-abstracted via `BackgroundActionRunner` — check
whether `ChatMapApp` can delegate to it more fully rather than calling
`runInBackground` inline at each site. Do this incrementally, one extraction
per commit, running the full test suite between each — this file is load-
bearing for the whole GUI and a bad extraction here is very visible.

---

## 9. `ChatProvider.throws Exception` forces broad catches everywhere downstream

**File:** `src/chatmap/backend/providers/ChatProvider.java`

`latestChat()`, `listChats()`, and `fetch()` are all declared
`throws Exception` — the root checked exception type. Every one of the 9
implementations either declares `throws Exception` too or catches it broadly.
Codebase-wide there are ~29 `catch (Exception e)` blocks across 19 files;
this interface is a significant root cause. Catching `Exception` also catches
`NullPointerException`/`ClassCastException` from actual bugs, folding them
into the same "provider unavailable" handling as expected I/O failures.

**Why it matters:** code quality / debuggability — masks real bugs behind
"expected failure" handling.

**Suggested direction:** introduce a `ChatProviderException` (checked,
wrapping the real cause) and change the interface to `throws ChatProviderException`.
This is a signature change touching all 9 implementations plus every caller
(`LiveChatFetchService`, `ConversationInventoryService`, CLI entry points) —
budget for a mechanical but wide-reaching change. Do this *after* item 7's
refactor if you get to both, since consolidating the triplicate classes first
means fewer places to touch here.

---

## Also worth doing, lower priority (code-smell items, not architectural)

These don't materially affect correctness but are worth cleaning up if you're
already in the relevant files:

- **Fragile positional `Chat` construction.** At least 9 call sites across
  `chatmap.backend.providers`, `chatmap.backend.web`, `chatmap.service`, and
  `chatmap.importer` build `Chat` via the raw positional constructor instead
  of `toBuilder()`. Four consecutive `String` params (`title, createdAt,
  updatedAt, importedAt`) are transposable with no compiler error. Migrate
  call sites to `toBuilder()` as you touch each file for other reasons.
- **Untyped message roles.** `Message.role()` is a bare `String`; `"user"`/
  `"assistant"` are hand-typed literals in 10 files, 25 occurrences, no
  shared constant or enum. A `Role` enum or `Message.ROLE_USER`/`ROLE_ASSISTANT`
  constants would make typos a compile error instead of a silent bad row.
- **Duplicated CLI boilerplate.** `RunPromptCli`, `SummarizeChatCli`,
  `ImportChatGptArchiveCli`, `ConversationInventoryCli` each hand-roll the
  same catch-`IllegalArgumentException`-print-usage-`System.exit(1)` pattern.
  A shared `CliRunner.run(args, usageText, action)` helper would collapse this.
- **`slf4j-nop` dependency.** Nothing in `src/` references `org.slf4j`
  directly — it's runtime-only, present to silence a transitive warning
  (likely from `sqlite-jdbc`). Worth confirming what pulls it in before
  deciding whether it's safe to drop.

---

## Suggested order if working sequentially

1. Item 6 (archived flag) — smallest, fastest, good warm-up.
2. Item 4 (export snapshot) — the fix pattern already exists in the codebase.
3. Item 1 (dedup gap) — high value, contained to one file.
4. Item 5 (source provenance) — needs a migration; do after 1 since both touch `Source`/dedup logic.
5. Item 3 (executor split) — do after 4/5 are settled, since it changes how those interact with the DB lock.
6. Item 7 (triplicate classes) — start with CLI-history providers only.
7. Item 9 (typed exceptions) — after 7, for the reason noted above.
8. Item 8 (`ChatMapApp` extraction) — ongoing, incremental, independent of the rest.
9. Item 2 (provider ordering) — flag for a product decision rather than doing unilaterally; the fix isn't purely technical.

Update this file's status (or delete it) once items are closed, following
the pattern of the other docs in `handoffs/`.
