# Handoff: test coverage gaps in backend/command and cli packages

Surveyed on 2026-08-11. Not a blanket "add more tests" ask — the gaps below
are specific, and a fair amount of `backend` is already solidly covered
(see "already covered" section at the bottom, so effort isn't duplicated).

---

## 1. `backend/command` — zero tests, and it's the highest-value gap

`CommandExecutor`, `CommandRequest`, `CommandResult`, `CommandRunner`,
`CommandExecutionException` — five files, no test file at all.

This matters more than most gaps because `CommandRunner` is the actual
process-execution layer every `AiBackend` shells out through, and it carries
real safety logic that's never been exercised by a test:

```java
if (process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS)) { ... }
...
process.descendants().forEach(ProcessHandle::destroy);
process.destroy();
...
process.descendants().forEach(ProcessHandle::destroyForcibly);
process.destroyForcibly();
```

Timeout handling and descendant-process cleanup on a hung/misbehaving
subprocess is exactly the kind of thing that's easy to get subtly wrong
(kill order, whether descendants are actually caught before the parent is
destroyed, whether `destroyForcibly` is reached at all if `destroy` doesn't
finish fast enough) and easy to never notice being wrong, since it only
matters when something actually hangs. Worth remembering this is also the
exact category of risk that was the deciding factor against JShellBackend's
in-process execution earlier this project (see the JShellBackend
conversion/deletion decision, still separately open) — this is the
mechanism that's supposed to be the safe alternative, and it's untested.

**Suggested tests:**
- A command that exits normally within its timeout — result captured
  correctly (exit code, stdout, stderr).
- A command that runs past its timeout — process actually gets killed, and
  descendants (a shell spawning a child process is an easy way to construct
  this) get killed too, not orphaned.
- A command that doesn't exist / fails to start — `CommandExecutionException`
  or equivalent, not a hang or a silent wrong result.

Note: `OtherCliBackendsTest` already tests `AiBackend` implementations
against a fake `CommandExecutor` (`CapturingExecutor`) to verify they
construct the right command — that's real, useful coverage, but it's
coverage of the callers, not of `CommandRunner` itself. This handoff is
about the real implementation those fakes stand in for.

---

## 2. `CliBootstrap` — shared by all 8 CLI tools, no dedicated test

Every CLI class routes through `CliBootstrap.parseOrExit()` / `.open()` /
`.parse()` now (that consolidation work landed a few handoffs back), but
nothing tests `CliBootstrap` itself in isolation — only indirectly, through
whichever individual CLI tests happen to exercise a given path.

**Suggested tests:** the arg-parsing/usage/exit-code contract directly —
valid args, invalid args producing the right usage message, `--home`
handling, and whatever else `parseOrExit`/`open` are actually responsible
for. Since this is now the single shared entry point for every CLI tool, a
bug here silently affects all eight at once; worth having it pinned down on
its own rather than only ever seen through other classes' tests.

---

## 3. Five of eight CLI entry points have no test at all

Tested: `ChatConsolidatorCli`, `RunPromptCli`.

Untested: `ConversationInventoryCli`, `ImportChatGptArchiveCli`,
`LiveSourceExchanges`, `SummarizeChatCli`, `ImportAllChatsCli`.

This was already true before today and isn't a new regression — worth
closing regardless, but lower priority than #1 and #2 above, since these are
thinner wrappers around already-tested service-layer code
(`ImportService`, `ConversationInventoryService`, etc.) rather than
containing much logic of their own. Pick whichever feels most valuable to
close first; no strong ordering preference here.

---

## Already covered — don't duplicate this

- `AiBackend` implementations (Claude, Codex, Agy, Ollama, JShell): command
  construction, resume-session behavior, stdin piping, system-prompt
  rejection — all tested against a fake executor.
- CLI-history providers (Claude Code, Codex, Gemini): parsing, full
  provider-round-trip (fetch -> persist -> search -> export), and the
  discovery/dedup reverse-direction test, all landed recently.
- Web (CDP) providers: `CdpBrowserConnection`, `CdpPage`,
  `CdpTranscriptAdapter`, `ChromeCdpLauncher`, `ClaudeWebChatProvider`, plus
  a combined `WebProvidersTest`.
