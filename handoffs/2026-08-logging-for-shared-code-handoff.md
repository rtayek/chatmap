# Handoff: route GUI/shared-code diagnostics through logging instead of println

From a fresh sweep on 2026-08-10. Scope is intentionally narrow — most
`System.out`/`System.err` calls in this codebase are correct as-is and
should NOT be touched.

---

## Explicitly out of scope: every CLI entry point

`System.out.println`/`System.err.println` in the following files is the
correct, intended interface for a command-line tool — a person running it
from a terminal is exactly who's meant to see that output. Do not convert
these:

- `src/chatmap/cli/CliBootstrap.java`
- `src/chatmap/cli/ImportAllChatsCli.java`
- `src/chatmap/cli/ImportChatGptArchiveCli.java`
- `src/chatmap/cli/SummarizeChatCli.java`
- `src/chatmap/cli/ConversationInventoryCli.java`
- `src/chatmap/cli/RunPromptCli.java`
- `src/chatmap/cli/ChatConsolidatorCli.java`
- `src/chatmap/cli/LiveSourceExchanges.java`

---

## In scope: three spots where output can silently vanish

These are either GUI-only code (no terminal guaranteed to be attached) or
shared between the GUI and CLI (where a `println` only reaches whichever one
happens to have a visible console).

1. **`src/chatmap/app/ChatMapRuntime.java`**
   - Line 52: `System.out.println(ChatMapPaths.diagnostics(paths));` — startup
     diagnostics. `ChatMapRuntime` is the GUI's own bootstrap (used only by
     `ChatMapApp`/`BackgroundActionRunner`, never by any CLI class). The
     README documents `./gradlew run` as the normal launch command, so this
     is visible in practice today — but launched any other way (double-click
     shortcut, packaged app), it's gone.
   - Line 106: `System.err.println("[ChatMap] Background work did not stop
     in time; ...")` — a real operational warning (background work still
     running at shutdown) that has the same visibility problem.

2. **`src/chatmap/service/LiveChatFetchService.java`**, `resolve()` method
   (lines 65 and 67) — for each provider that returns no live chat or throws,
   the reason is only printed to stderr. This method is called from both the
   GUI and `SummarizeChatCli`. If every provider fails, the exception that
   actually propagates (`NoChatAvailableException`, "No live provider chat
   and no stored chats; nothing to act on.") carries none of that per-provider
   detail — a GUI user has no way to find out *why* it failed, only that it
   did. This is the most valuable one to fix in this handoff.

3. **`src/chatmap/service/PromptService.java`**, `writeLocalTranscript()`
   (line 181) — lower priority. Already explicitly a best-effort side write
   (the comment above it says as much; the real DB persist isn't affected),
   but same shape as the others.

---

## Suggested approach

No logging framework is actually wired into the build right now —
`build.gradle.kts` only declares `sqlite-jdbc` and `gson`. (The
`slf4j-nop`/`slf4j-api` jars still sitting in `lib/` are leftover drift from
a dependency that's since been removed — see the separate eclipse-classpath
handoff. Don't treat their presence as "logging is already set up.")

Simplest path that avoids reopening that dependency question: use
`java.util.logging`, built into the JDK, no new dependency needed. A small
wrapper is enough — doesn't need to be elaborate:

```java
package chatmap.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Log {
    private Log() {}

    public static Logger of(Class<?> owner) {
        return Logger.getLogger(owner.getName());
    }
}
```

Then at each of the three call sites, replace the `System.err.println(...)`
with something like `Log.of(LiveChatFetchService.class).warning(...)`,
and the `System.out.println` startup diagnostic in `ChatMapRuntime` with
`.info(...)`.

By default `java.util.logging` writes to stderr via the console handler —
so this alone doesn't guarantee a GUI user launched without a terminal will
see anything either. If persistence matters (a log file the GUI user could
be pointed to after the fact), that's a follow-up decision, not required for
this handoff: a `FileHandler` writing into ChatMap home
(e.g. `<home>/chatmap.log`) alongside the existing `chatmap.db` would be the
natural place, following the same "everything lives under home" pattern
already used for the database. Flagging this as a design choice rather than
deciding it here — whether a log file is wanted at all is worth a quick
confirmation before adding one.