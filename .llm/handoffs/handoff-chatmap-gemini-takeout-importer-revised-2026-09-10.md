# Handoff: Gemini Takeout Importer

From: chatmap (planning session)
Date: 2026-09-10 (revises
handoff-chatmap-gemini-takeout-importer-2026-09-10-2030.md -- Claude Code
reviewed that version and flagged four gaps, folded in below; no
implementation has happened yet)
Repo: github.com/rtayek/chatmap
Local clone: C:\Users\ray\eclipse-workspace\chatmap

## Context

Ray pulled a Google Takeout export containing Gemini conversation history.
Inspected structure (one sample file only -- see "Validate the real
archive" below):

    Takeout/Gemini in Workspace/Conversation History/conversation_<id>.txt
    Takeout/Gemini/gemini_gems_data.html          (11 bytes, empty stub)
    Takeout/Gemini/gemini_scheduled_actions_data.html  (11 bytes, empty stub)

The Gems and scheduled-actions files are empty in this export and can be
ignored (may not be for every user -- do not assume permanently empty).

The conversation file has a .txt extension but is actually JSON:

    {
     "conversation_turns": [
      {
       "user_turn": {
        "prompt": "...",
        "turn_index": 0,
        "turn_last_modified": "2026-08-15T23:41:12.049807+00:00"
       }
      },
      {
       "system_turn": {
        "text": [ { "data": "..." } ],
        "turn_index": 1,
        "turn_last_modified": "2026-08-15T23:41:12.049807+00:00"
       }
      }
     ]
    }

Apparently flat and already-ordered, alternating user_turn / system_turn.
Apparent, not confirmed -- see below.

Real archive files on disk:
~/tmp/takeout-20260909T004313Z-1-001.tgz (has the actual conversation data)
~/tmp/takeout-20260909T004313Z-001.tgz (just archive_browser.html, skip)

## This is a new, fourth import path

ChatMap currently has two separate mechanisms. Do not conflate this task
with either existing one:

1. infrastructure/importer/ -- one-shot file importers for archive exports:
   ChatGptArchiveImporter, ChatGptConversationParser, ChatGptJsonImporter,
   ChatGptMapping, ChatGptImportCounter, plus generic MarkdownImporter,
   PlainTextImporter, RolePrefixedTranscriptParser. Reachable via a specific
   Gradle task, e.g. importChatGptArchive.

2. infrastructure/provider/ -- the live six-source ChatProvider system behind
   importAllChats: three CLI-history readers (ClaudeCodeHistoryProvider,
   CodexCliHistoryProvider, GeminiCliHistoryProvider) and three web scrapers
   over CDP (ClaudeWebChatProvider, ChatGptWebChatProvider,
   GeminiWebChatProvider).

GeminiCliHistoryProvider and GeminiWebChatProvider already exist but do NOT
read Takeout data -- the CLI one reads
~/.gemini/tmp/<project-hash>/chats/session-*.jsonl, and the web one scrapes
gemini.google.com live over CDP. Neither touches a bulk Takeout export. This
task follows pattern (1), the file-import pipeline -- confirmed as the
right direction on review.

## Before writing any import code: four things to tighten

Raised on review of the prior version of this handoff. Resolve these before
finalizing the parser, not after.

### 1. Validate the real archive, not just the one sample

Only a `head -20` of one conversation file has been inspected. That does
not establish: whether every conversation file follows the same shape,
whether turns are always strictly alternating user/system, whether
turn_index is always contiguous from 0, or whether Gemini ever emits
regenerated/edited turns that would break a flat ordered-array assumption.
Extract ~/tmp/takeout-20260909T004313Z-1-001.tgz in full and look at more
than one conversation file, and more than the first 20 lines of each,
before finalizing the parser's assumptions.

### 2. Conversation identity and repeat-import behavior

Not addressed in the prior handoff at all. Re-running the importer on the
same export must not duplicate chats. Look at how ChatGptArchiveImporter /
ChatGptConversationParser derive externalConversationId (design.md's Chat
model has externalConversationId, contentHash, sourceUpdatedAt,
lastImportedAt specifically for this) and do the equivalent for Gemini.
The numeric id embedded in the filename (conversation_1786837272.txt) is
the likely candidate for externalConversationId, since there is no
conversation-level id field inside the JSON body itself -- confirm this
holds across more than one sample file per point 1 above.

### 3. Role mapping: system_turn is not MessageRole.system

MessageRole has both assistant and system as distinct values (system means
a system prompt in this domain model, not "the model's answer"). Gemini's
system_turn.text contains Gemini's actual response, so it must map to
MessageRole.assistant, despite the field being named "system_turn" in
Google's export. Do not map the field name literally.

### 4. Choose supported input formats: directory vs. .tgz

ChatGptArchiveImporter handles ZIP specifically. Decide explicitly whether
the Gemini importer accepts: a .tgz directly, an already-extracted
directory, or both. This is a real scope decision, not a detail -- pick one
and say so, rather than trying to support every combination speculatively.

## Task

Build a Gemini Takeout importer, following the file-importer pattern above
-- specifically closest to ChatGptArchiveImporter / ChatGptJsonImporter /
ChatGptConversationParser, not the ChatProvider pattern.

Concretely, once points 1-4 above are resolved:

- A new Source enum value, e.g. geminiTakeoutJson (follow existing
  lowerCamelCase dbValue convention, e.g. plainText, chatgptJson).
- A GeminiConversationParser: reads conversation_turns, maps user_turn to
  MessageRole.user and system_turn to MessageRole.assistant (see point 3),
  pulls prompt (user) or text[].data (system_turn) as message body, uses
  turn_index for ordering and turn_last_modified for timestamps.
- A GeminiTakeoutImporter (or similarly named) that walks the input (per
  point 4) and locates conversation_*.txt files under
  "Gemini in Workspace/Conversation History/", deriving externalConversationId
  per point 2.
- A Gradle task, e.g. importGeminiTakeout, mirroring importChatGptArchive.

## Constraints

- TDD/DDD preferred -- test against real sample conversation_*.txt fixtures
  (more than one, per point 1), same pattern as ChatGptJsonFixture /
  ChatGptJsonImporterTest / SampleImportFilesTest. Include a repeat-import
  test proving no duplication (point 2).
- Naming: kebab-case files, lowerCamelCase identifiers, no underscores/
  snake_case, no PascalCase for non-class names.
- Do not wire this into GeminiCliHistoryProvider or GeminiWebChatProvider --
  it is a separate provider/importer, not a modification of either existing
  Gemini path.
- system_turn.text is an array of {data: ...} objects -- confirm whether it
  is ever more than one element in real data before assuming a single
  concatenation is always correct.

## Decision

- This is new code, not an extension of an existing importer or provider.
- Follows the ChatGPT JSON-archive pattern, not the CLI-history/CDP
  ChatProvider pattern, since Takeout is a bulk export file, not a live or
  local-session source.
- system_turn maps to MessageRole.assistant, not MessageRole.system.

## Open

- Whether "Gemini in Workspace" implies a Workspace-specific export format
  distinct from a personal-account Gemini Takeout -- confirm with Ray if a
  personal-account export differs, once more than one real sample has been
  inspected.
- Whether turn_index values are ever non-contiguous or reused across
  branches/regenerations in Gemini's export -- resolve via point 1, not by
  assumption.
- Input format support (directory vs. .tgz vs. both) -- resolve via point 4
  before writing the importer, not during.

## Output

Provide the result as a downloadable .md file per the standing rule (no
exceptions):
handoff-<from-project>[-to-<to-project>]-<YYYY-MM-DD-HHMM>.md.
