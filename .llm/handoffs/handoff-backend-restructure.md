# Handoff: restructure chatmap.backend into subpackages

Repo: chatmap (github.com/rtayek/chatmap). Local folder may be named
cjatmanager in Eclipse -- same project.

## Problem

chatmap.backend has 42 source files with no subpackage structure and only
8 test files -- the worst file-count and test-ratio of any package in the
codebase. It actually contains five distinct sub-domains flattened
together, which makes the package harder to navigate and likely harder to
test in isolation.

## Target structure

```
chatmap.backend.ai/        AI prompt backends
  AiBackend, AiRequest, AiResponse, BackendId,
  AiBackendException, AiBackendExecutionException,
  AiBackendStartupException, AiBackendUnsupportedRequestException,
  AgyCliBackend, ClaudeCliBackend, CodexCliBackend, OllamaCliBackend,
  StandardCliBackend, CommandBackedAiBackend, CommandBackedRun,
  DefaultAiBackends, PromptProfile

chatmap.backend.providers/ chat/history retrieval
  ChatProvider, ClaudeWebChatProvider, ChatGptWebChatProvider,
  GeminiWebChatProvider, ClaudeCodeHistoryProvider,
  CodexCliHistoryProvider, GeminiCliHistoryProvider,
  DefaultChatProviders, HttpChatProvider

chatmap.backend.web/       browser/CDP scraping infrastructure
  PlaywrightWebAdapter, ClaudeWebAdapter, ChatGptWebAdapter,
  GeminiWebAdapter, CdpTranscriptAdapter, ChromeCdpLauncher,
  WebTranscripts

chatmap.backend.command/   shelling out
  CommandExecutor, CommandRequest, CommandResult, CommandRunner,
  CommandExecutionException

chatmap.backend/           stays at top level -- shared/cross-cutting
  SessionLines, LocalCliSessions, ProviderIdentity, ClaudeTurn
```

(Verify this file-to-group mapping against the actual current contents of
chatmap.backend before starting -- the package may have grown since this
handoff was written. Treat the grouping logic as authoritative, the file
list as a starting point.)

## Task -- do this in order, one subpackage per commit

1. **chatmap.backend.command first.** Fewest inbound dependencies from the
   rest of backend/, safest to validate the process on. Move the 5 files,
   update package declarations and imports, run `./gradlew check`, commit.
2. **chatmap.backend.web next.** Depends on nothing else in backend/ that
   hasn't already moved; providers/ will depend on it, not the reverse.
   Move, update imports, `./gradlew check`, commit.
   IMPORTANT: this group is what actually talks to Chrome via CDP and
   scrapes live pages (Claude/ChatGPT/Gemini web). A passing `./gradlew
   check` only proves it compiles and unit tests pass -- it does NOT prove
   live fetch still works, since that depends on a real browser connection
   that tests likely don't exercise. After this move, manually run a real
   live fetch (whatever the normal UI or CLI path is for pulling a chat
   from the web) and confirm it still succeeds before moving on to step 3.
3. **chatmap.backend.ai next.** Largest group (17 files). Check whether
   anything here depends on command/ (CommandBackedAiBackend likely does)
   -- that's fine, command/ already moved. Move, update imports,
   `./gradlew check`, commit.
4. **chatmap.backend.providers last** of the four. Likely depends on both
   web/ and possibly identity helpers left at the top level. Move, update
   imports, `./gradlew check`, commit.
   Re-run the same live web fetch check from step 2 after this move too --
   providers/ is the layer the UI/CLI actually calls into, so this is the
   point closest to a real regression if an import got missed silently.
5. **Leave SessionLines, LocalCliSessions, ProviderIdentity, ClaudeTurn at
   chatmap.backend top level.** These are shared value types/helpers used
   across multiple subgroups; promoting them would just create
   cross-subpackage imports for no benefit. The goal is no *mixed
   responsibilities* at top level, not zero files there.

Use an IDE "Move" refactor (Eclipse) or equivalent for each file, not
manual git mv + hand-edited imports -- this keeps the package declaration
and every import site in sync atomically.

## Constraints

- One commit per subpackage move (four commits total), not one giant
  commit -- makes it easy to bisect if something breaks.
- Run `./gradlew check` after EACH subpackage move, not just at the end --
  catches circular-dependency issues early rather than after everything is
  tangled together.
- Do not change any class's public behavior -- this is a pure move/rename,
  no logic changes.
- Do not push -- Ray reviews first.
- LF line endings, no CRLF.

## Optional follow-on (separate from this task, don't do it here)

Once split, each subpackage is small enough to bring toward reasonable
test coverage incrementally. That's a separate piece of work, not part of
this restructuring -- flag it in the summary but don't start it.

## Output

Short summary: confirm all four subpackages were created, file counts per
subpackage, confirmation `./gradlew check` and `./gradlew test` (or
Eclipse's runner) pass after all four moves, confirmation that a real live
web fetch was manually verified after step 2 and again after step 4 (not
just that tests passed), and note anything that didn't fit the plan
cleanly (e.g. a file with dependencies pointing the "wrong" direction).
