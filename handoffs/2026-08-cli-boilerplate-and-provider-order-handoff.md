# Handoff: CLI boilerplate, provider ordering, error-handling consistency

Scope: three small, independent fixes surfaced in a fresh high-level pass on
2026-08-10, after the ChatMapApp extraction (groups A/B/D) landed. No
architecture-level issues found this pass.

**Explicitly out of scope: JShellBackend.** Do not touch
`src/chatmap/backend/ai/JShellBackend.java` or its wiring in
`DefaultAiBackends.java` as part of this handoff. That's a separate, still-open
decision (convert to subprocess vs. delete) and should not be bundled in here.

---

## 1. Duplicated CLI entry-point error-handling boilerplate

Four CLI classes each hand-roll the identical shape in `main()`:

```java
ParsedArguments parsedArguments;
try {
    parsedArguments = ChatMapPaths.parse(args);
} catch (IllegalArgumentException e) {
    System.err.println(e.getMessage());
    System.err.println("Usage: <toolname> ...");
    System.exit(1);
    return;
}
```

Affected files:
- `src/chatmap/cli/ImportChatGptArchiveCli.java`
- `src/chatmap/cli/ConversationInventoryCli.java`
- `src/chatmap/cli/SummarizeChatCli.java`
- `src/chatmap/cli/RunPromptCli.java`

Each copy differs only in the usage string. `CliBootstrap` already
consolidated the *service-wiring* duplication (`CliBootstrap.open(...)`) —
this is the matching fix for the *argument-parsing* duplication that's still
scattered across each `main()`.

**Suggested fix:** add a helper to `CliBootstrap`, e.g.:

```java
public static ParsedArguments parseOrExit(String[] args, String usage) {
    try {
        return ChatMapPaths.parse(args);
    } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        System.err.println(usage);
        System.exit(1);
        throw new AssertionError("unreachable"); // keeps callers exhaustive
    }
}
```

and have each of the four `main()` methods call
`CliBootstrap.parseOrExit(args, "Usage: ...")` instead of repeating the
try/catch. Keep each CLI's own usage string as-is — only the parsing/exit
mechanics move.

Also check `SummarizeChatCli` and `RunPromptCli` for the same "wrong arg
count" pattern (`System.err.println(usage); System.exit(1);`) seen in
`ImportChatGptArchiveCli`/`ConversationInventoryCli` — if it's the same shape,
consider whether it's worth folding into the same helper or a sibling one.
Not required if it doesn't fit cleanly; use judgment.

---

## 2. `preferLocal` system property → plain provider ordering

`src/chatmap/backend/providers/DefaultChatProviders.java` currently gates
local-first provider ordering behind an opt-in system property:

```java
boolean preferLocal = Boolean.getBoolean("chatmap.providers.preferLocal");
if (preferLocal) {
    ...
}
```

Product decision (confirmed with Ray): drop the system-property branch
entirely and just hardcode local CLI-history providers before the web
providers in the default `List.of(...)` ordering. The reason for wanting this
as a plain list rather than a flag is so Ray can directly comment
providers in/out to test different orderings/subsets, rather than needing an
env var to change behavior.

**Suggested fix:** remove the `preferLocal` boolean and the conditional
branch; replace with a single `List.of(...)` (or equivalent) with local
CLI-history providers listed first, web providers after. No opt-in flag
needed — this becomes the one and only default ordering.

Note this changes runtime behavior: `LiveChatFetchService.resolve()` returns
the first provider with any result, not the best across all of them, so this
was previously flagged as a real behavior change (web could become
unreachable for a local-heavy workflow) — already discussed and accepted, not
a new risk to re-litigate.

---

## 3. `ChatConsolidatorCli` inconsistent top-level error handling

`src/chatmap/cli/ChatConsolidatorCli.java`, in the top-level `main()` catch
block (~line 112-115), does both:

```java
} catch (Exception e) {
    System.err.println("❌ Fatal Error: " + e.getMessage());
    e.printStackTrace();
    System.exit(1);
}
```

This is the only CLI class in the codebase that calls `printStackTrace()` in
addition to printing the message — every other CLI just prints
`e.getMessage()` and exits. Minor inconsistency; align it with the rest
(drop `printStackTrace()`, keep the message print + exit), unless there's a
reason this one specifically wants the full stack trace on failure — if so,
leave a short comment explaining why it's different.

---

## Suggested order

1. CLI boilerplate helper (#1) — mechanical, low risk, touches the most files.
2. `ChatConsolidatorCli` consistency (#3) — one-line, do it while in that file.
3. `preferLocal` removal (#2) — isolated to `DefaultChatProviders`, independent
   of the other two.
