# Codex Handoff: Import the Existing ChatGPT Archive into ChatMap

## Immediate Goal

Make ChatMap import every usable conversation from Ray's existing ChatGPT export archive into the ChatMap SQLite database.

This is now the project's immediate priority. Semantic extraction, additional provider work, and other improvements come later.

## Existing Archive

The archive already exists. Do not create or request another archive.

- Windows path: `C:\Users\ray\eclipse-workspace\tmp\chatgpt-july-31.zip`
- Git Bash path: `/c/Users/ray/eclipse-workspace/tmp/chatgpt-july-31.zip`
- The `tmp` directory is directly under the Eclipse workspace.

Treat the ZIP as immutable private input:

- Do not modify, move, rename, delete, upload, or commit it.
- Do not extract it into the ChatMap repository.
- Do not print message bodies or other private chat contents in logs.
- It is acceptable to report entry names, byte sizes, conversation counts, and parse/error counts.

The archive has already passed `unzip -t` with no compressed-data errors.

### Observed Archive Layout

The export does **not** contain a single `conversations.json`. Its conversations are sharded across:

- `conversations-000.json`
- `conversations-001.json`
- `conversations-002.json`
- `conversations-003.json`

Other observed entries include:

- `codex.json`
- `chat.html`
- `conversation_asset_file_names.json`
- `library_files.json`
- `shared_conversations.json`
- many `file_*.dat` attachment blobs
- account/settings/feedback/manifest JSON files

Use the numbered conversation shards as the primary ChatGPT conversation source. Inspect `codex.json` separately and report whether it contains importable conversations; do not assume that it shares the normal conversation schema. Do not parse `chat.html` when structured JSON is available. Attachment import is not part of the first pass, but attachment references and skipped non-text content must be counted and reported.

## Repository State Already Established

Repository: `rtayek/chatmap`

Current `master` includes these two pushed commits:

- `b391ff9` — `Harden chat import identity and transactions`
- `ee937c1` — `Apply ChatMap cleanup fixes`

The first commit added provider identities, content hashing, transactional reimport behavior, schema migration, and related tests. The second added accessibility, documentation, CI, and cleanup changes.

Eclipse runs all tests successfully. The most recently reported suite contains 147 passing tests.

The archive itself and a new archive-import implementation are not present in Git.

## Hard Stop on Git Operations

Edits and local testing are authorized. Do **not** commit, push, force-push, create a pull request, or rewrite history.

At completion, report the working-tree diff and wait for Ray's review.

Preserve unrelated user files and changes. An unrelated untracked file named `xx` was previously reported; leave it untouched if it still exists.

## First: Inspect Without Extracting

1. Confirm the archive exists and is readable.
2. List its entries without extracting them.
3. Locate every `conversations-*.json` shard and order the shards lexicographically by their zero-padded numeric suffix.
4. Inspect only enough structure to verify:
   - top-level representation
   - conversation ID field
   - title field
   - create/update timestamps
   - `mapping`, parent links, and `current_node`
   - message author roles and content-part forms
5. Record counts and structural observations without logging chat text.

Inspect `codex.json` separately enough to classify it without printing its content. If either the conversation shards or `codex.json` differs materially from the expected structures, report the difference rather than designing around guesses.

## Required Import Behavior

Add a bulk ChatGPT archive importer that:

1. Reads every `conversations-*.json` shard directly from the ZIP in deterministic filename order. Also accept an older single `conversations.json` export when present.
2. Imports all usable conversations across all shards, not merely the first conversation or first shard.
3. Uses the archive's durable ChatGPT conversation ID as `externalConversationId`.
4. Uses the existing import identity/content-hash machinery so reimport is idempotent:
   - new identity: insert one chat and its messages
   - same identity and same content hash: return the existing chat unchanged
   - same identity and changed content hash: update the existing chat and replace its messages atomically
5. Preserves title and available creation/update timestamps.
6. Records a useful local `sourceUri` that identifies the ZIP and conversation without copying chat content.
7. Processes conversations independently so one malformed conversation does not discard all valid imports.
8. Returns a clear bulk result containing at least:
   - conversations discovered
   - inserted
   - updated
   - unchanged
   - skipped or failed
   - concise failure reasons without message text

Prefer streaming the outer JSON array if the current JSON library supports it cleanly. Do not add a large framework merely for streaming.

## Conversation-Graph Policy

ChatGPT exports commonly store messages as a node mapping rather than a flat list.

For the first implementation, reconstruct the active conversation shown by ChatGPT:

1. Start at `current_node`.
2. Follow parent links to the root.
3. Reverse that path into chronological conversation order.
4. Import message-bearing nodes from that active path.

Do not silently flatten alternate branches into the same transcript. If the archive lacks a usable `current_node`, use a deterministic, tested fallback and document it.

Preserve supported author roles. Handle text content parts safely. Count unsupported non-text content rather than crashing or serializing arbitrary internal JSON into visible transcript text.

## Integration Approach

Inspect the existing implementation before choosing names or duplicating code, especially:

- `ChatGptJsonImporter`
- `ImportService`
- `ChatRepository`
- `MessageRepository`
- `ChatContentHasher`
- `Source`
- the current UI import actions and controller/service boundary

Reuse the current normalization and persistence paths. Keep ZIP parsing separate from persistence.

Provide a usable JavaFX action for selecting and importing a ChatGPT `.zip` archive. A clearly labeled `Import ChatGPT Archive` action is preferable. Display the final bulk counts in the UI status/result area.

Do not broaden this task into semantic extraction, automatic project assignment, tagging, summarization, web scraping, or other providers.

## Tests as Functional Specification

Create ZIP fixtures programmatically in test temporary directories; do not check in a real or binary chat archive.

Cover at least:

- multiple conversations imported from one ZIP
- multiple numbered conversation shards imported in deterministic order
- compatibility with a single legacy `conversations.json` entry
- active-path reconstruction from `mapping` and `current_node`
- stable ChatGPT conversation IDs stored as external identities
- unchanged reimport creates no duplicate chat or messages
- changed content under the same ID updates one chat and replaces its messages
- one malformed conversation does not prevent valid conversations from importing
- absence of both `conversations.json` and all `conversations-*.json` entries produces a clear error
- unsupported or empty message content is handled deterministically
- database transaction behavior remains correct
- the existing test suite continues to pass

Run the authoritative Gradle tests as well as the Eclipse tests if practical. Report exact test counts and failures.

## Real Database Safety

Do not use the real database as the first test target.

After unit/integration tests pass:

1. Determine and report the exact database path used by ChatMap.
2. Ensure ChatMap is not running.
3. Make a timestamped backup beside the database before the real import.
4. Verify the backup exists and has nonzero size.
5. Run the archive import once.
6. Run it a second time to verify idempotency.
7. Compare database counts after both runs.

Never overwrite the backup. Do not delete it during this task.

## Completion Report

Stop without committing or pushing and report:

- archive entry and conversation counts
- observed export format
- files changed or added
- implementation summary
- exact test commands and results
- real database path and backup path, if the real import was run
- first-import inserted/updated/unchanged/failed counts
- second-import counts proving idempotency
- unsupported content or parse problems
- `git status --short --branch`
- `git diff --check`
- any remaining decisions or risks

Do not include private message text in the report.
