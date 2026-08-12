# ChatMap Handoff: Reconcile and Import All Six Chat Sources

Work in the current ChatMap repository. The immediate goal is to determine how many conversations are available from every configured source and import everything that is safely importable.

Do not begin semantic extraction, keyword classification, embedding work, or another general refactoring pass.

Preserve all existing work. Do not commit or push.

## Sources

Cover all six providers:

1. Claude web
2. ChatGPT web
3. Gemini web
4. Claude Code history
5. Codex CLI history
6. Gemini CLI history

Use the actual provider names returned by the current code when invoking `--source`.

## Starting checks

Run:

```bash
git status --short --branch
git diff --check
./gradlew test
```

If the current logging/path corrections are uncommitted, preserve them. Stop and report if tests are not green or if overlapping work makes proceeding unsafe.

## Select the correct database

Resolve and print:

```bash
printf 'CHATMAP_HOME=%s\n' "$CHATMAP_HOME"
```

Confirm that ChatMap selects the intended project-local database, normally:

```text
<repository>/.chatmap-local/chatmap.db
```

Report the absolute selected home and database paths.

Do not create or use a new database under:

```text
~/.chatmap
```

Confirm that no ChatMap GUI or competing import process is using the database before writing.

## Phase 1: read-only inventory

Run the conversation inventory before importing:

```bash
./gradlew conversationInventory
```

If useful, run providers individually using the current supported `--source` syntax.

For each source record:

```text
provider
discovered
already imported
missing
inventory complete or incomplete
diagnostic/limitation
```

Do not fetch full transcripts merely to count candidates when metadata discovery is sufficient.

### Browser providers

Use the current direct-CDP implementation. Do not add Playwright.

Before querying the web providers:

* Confirm one Chrome instance is available through the configured CDP endpoint.
* Confirm the user is already logged into Claude, ChatGPT, and Gemini.
* Avoid repeatedly launching Chrome or opening many PowerShell windows.
* Process providers sequentially.
* Preserve any diagnostic explaining an incomplete sidebar or inaccessible conversation.

Do not claim that a web inventory is complete unless the provider can actually enumerate the complete history.

## Phase 2: database baseline and backup

Before importing, record:

* Database file size and modification time
* Total chats
* Total messages
* Chat count by `Source`
* Chats with external identities
* Duplicate non-null `(source, externalConversationId)` identities
* Orphan-message count

Create one timestamped backup beside the selected database and verify that it is nonzero and readable.

Do not modify or remove previous backups.

## Phase 3: import missing conversations

Run the bulk importer:

```bash
./gradlew importAllChats
```

Individual providers may be run separately when that makes failure isolation clearer:

```bash
./gradlew importAllChats -Pargs='--source <provider name>'
```

Use the syntax actually supported by the current Gradle task and CLI; do not guess if it differs.

For each source record:

```text
discovered
inserted
updated
already imported/unchanged
no importable content
failed
```

A candidate successfully read but containing no real user or assistant turns belongs in `no content`.

Browser, CDP, selector, parsing, permission, and I/O problems are genuine failures. Do not hide them in the no-content count.

Continue after an individual candidate failure, but retain enough source identity and diagnostic information to investigate it without printing message bodies.

## Phase 4: repeat-import verification

Run the inventory and importer a second time.

Expected second-import behavior:

```text
inserted = 0
updated = 0
all previously imported candidates = unchanged/already imported
```

Exceptions must be explained. A provider whose remote content changed during the experiment may legitimately produce an update.

Verify that repeated importing creates no duplicate external identities and no orphan messages.

## Phase 5: final database checks

Record the same database counts taken before import and compare them with the final state.

Verify:

* Foreign-key integrity
* No orphan messages
* No duplicate non-null `(source, externalConversationId)`
* Imported chats have at least one message
* Existing chats and organizing state were preserved
* Database remains readable through ChatMap repositories
* Search returns at least one newly imported conversation when new chats were added

Do not print private message bodies in the report.

## Reports

Place the operational reports under the selected ChatMap home, for example:

```text
<CHATMAP_HOME>/reports/conversation-reconciliation-<timestamp>/
```

Produce:

```text
reconciliation-summary.md
provider-counts.csv
failures.csv
```

The summary should clearly distinguish:

* Complete CLI inventories
* Incomplete web inventories
* Harmless no-content candidates
* Genuine failures requiring code or environment work
* Conversations that remain unaccounted for

These reports are local data and must remain ignored by Git.

## Code changes

Prefer completing this task without repository changes.

If a genuine importer or provider defect blocks reconciliation:

1. Reproduce it with a focused test.
2. Make the smallest appropriate repair.
3. Run the complete test suite.
4. Clearly separate code changes from database operations in the final report.

Do not redesign provider architecture during this pass.

## Final validation

Run:

```bash
./gradlew test
git diff --check
git status --short --branch
```

If code changed, also run:

```bash
./gradlew check
./gradlew eclipse
./gradlew eclipse
```

The two Eclipse-generation runs must be idempotent.

## Final report

Report:

* Selected ChatMap home and database
* Backup path and verification
* Before/after chat and message counts
* Per-provider reconciliation table
* First-import results
* Second-import/idempotence results
* Genuine failures and likely causes
* Which inventories are incomplete and why
* Report-directory path
* Repository changes, if any
* Test and static-analysis results
* Recommended next step

Stop without committing or pushing.
