# Handoff: Gemini Takeout importer implementation

Date: 2026-09-10
Repository: C:/Users/ray/eclipse-workspace/chatmap
Status: implemented and validated locally; not committed or pushed

## Implemented

- GeminiConversationParser reads Workspace conversation_turns JSON, preserves
  titles and source timestamps, normalizes timestamps to UTC, maps user_turn
  to user and system_turn to assistant, and retains each complete raw turn
  including citations. Multiple response text parts are joined with blank lines.
- GeminiTakeoutImporter reads an extracted Takeout root containing
  Gemini in Workspace/Conversation History/conversation_<numeric-id>.txt.
  It validates all files before persistence, orders files deterministically,
  sorts turns by index, tolerates index gaps/nonalternation, and rejects
  duplicate, negative, fractional, or unsupported turn shapes.
- GeminiTakeoutImportService reuses ImportService for transactional writes,
  content hashing, repeat-import matching, and changed-transcript replacement.
  Empty conversations are skipped. Persistence failures are reported per chat.
- ServiceGraph wires the importer; ImportGeminiTakeoutCli and the Gradle task
  expose it without changing the web/CLI-history providers or the UI.
- Source.geminiTakeoutJson identifies this import source. No dependency or
  database-schema changes were required.

## Usage

From Git Bash in the repository:

```sh
./gradlew importGeminiTakeout -Pargs=tmp/Takeout
```

Use gradlew.bat in PowerShell or Command Prompt. Without a home override this
imports into the normal ChatMap home. For an isolated database, add:

```text
-Phome=tmp/gemini-import-test
```

Quote the entire Gradle property argument when a path contains spaces.

## Evidence and validation

The latest files were in the repository's tmp/, not the user's home tmp/.
Inspected every turn of the one conversation in
`tmp/takeout-20260909T004313Z-1-001.tgz`; the extracted file under
`tmp/Takeout/` matches the archive byte-for-byte. It has 16 alternating turns,
indices 0 through 15, one text part per response, three responses with
citations, plus title, creation_time, and last_modification_time metadata.

The checked-in fixture preserves this shape with private text and citation
labels/URLs replaced. The second fixture is synthetic, not a second observed
conversation; it exercises multiple files and multipart responses.

- 13 new Gemini tests cover parsing, provenance, sorting, malformed input,
  repeat import, changed content/search updates, per-chat rollback, and CLI use.
- Full Gradle check passed: 618 tests total, 614 executed, 4 skipped, no failures
  or errors; Checkstyle, PMD, SpotBugs, and JaCoCo completed successfully.
- Windows stale-output locks prevented reuse of build directories. Final check
  used `-PbuildDir=tmp/gemini-build-final --no-daemon`, without deleting outputs.
- Real data was imported twice into `tmp/gemini-validation-20260910/chatmap.db`.
  A separate read-only SQLite connection confirmed one chat, 8 user messages,
  8 assistant messages, and raw JSON equality for all 16 source turns.
- Normal ChatMap data was not imported into during validation.

## Decisions and limits

- Extracted-directory input only; direct TGZ/ZIP input is not implemented.
- Filename numeric ID is scoped by the geminiTakeoutJson source. Repeat imports
  of the same ID work across directory moves. ID stability across independent
  exports/accounts is not established. Use separate homes for different accounts.
- Only the observed Workspace JSON format is supported. Personal-account or
  regenerated-turn formats were not available for validation. Duplicate indices
  fail explicitly rather than selecting an arbitrary branch.
- Existing ImportService compares message roles and text, not raw JSON or
  timestamps. Citation-only and message-timestamp-only changes do not replace
  already stored messages. This existing behavior was not expanded.
- No commit or push was made. The pre-existing untracked handoff with spaces
  and a middle-dot in its filename was left untouched.
