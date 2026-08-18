---
project: chatmap
agent: claude
branch: feature-structured-output-no-silent-blank
---

# Task: Stop StructuredCliOutput from turning an unrecognized response schema into a successful blank

## Background / why

`StructuredCliOutput.parse` (in `src/chatmap/infrastructure/llm/`) parses an
agent CLI's stream-json / NDJSON stdout into a `Parsed(text, sessionId)`. It walks
each JSON line, and for recognized assistant events pulls out the response text.

The problem is the final line:

```java
if (!texts.isEmpty()) {
    return new Parsed(String.join("\n", texts), sessionId);
}
return new Parsed(sawJson ? "" : standardOutput, sessionId);
```

When the output DID contain valid JSON (`sawJson == true`) but NONE of it matched
a recognized assistant-text shape, this returns `text == ""` — a blank but
otherwise "successful" result. The three CLI providers (`ClaudeCliProvider`,
`CodexCliProvider`, `AntigravityCliProvider`) all call `parse` and wrap
`parsed.text()` into an `LlmResponse` with no error. So a provider changing its
JSON schema (a field rename, a new event-type name) silently degrades to an empty
successful response instead of a visible failure. That's the worst kind of bug:
no exception, no error text, just a blank answer that looks like the agent had
nothing to say.

The current fallback conflates two very different situations:
- **"saw no JSON at all"** (`sawJson == false`) → the output was plain text; returning
  it raw is correct (codex/agy without stream-json, or a plain-text agent).
- **"saw JSON but recognized nothing"** (`sawJson == true`, `texts.isEmpty()`) → this
  is a schema mismatch / parser-coverage gap, and should NOT masquerade as success.

## What to do

Distinguish those two cases. Keep the raw-text fallback for the no-JSON case, but
make the "JSON present, nothing recognized" case a visible failure that still
preserves the raw output for diagnosis.

Preferred approach — throw a typed exception the providers already handle:

1. When `sawJson == true` and `texts.isEmpty()`, throw
   `LlmBackendExecutionException` (the same type the providers already throw for
   nonzero exit / timeout, so callers need no new handling). The message must be
   diagnostic and MUST include the raw stdout (or a bounded prefix of it) so the
   failure is debuggable from the failure report / logs, e.g.:
   `"Agent produced structured output but no recognized response text
   (possible schema change). Raw output: " + standardOutput`.
   If `LlmBackendExecutionException` requires a `CommandResult`, either add a
   constructor/overload that takes just a message, or have the CALLER catch a
   lighter parse-specific exception and rethrow as `LlmBackendExecutionException`
   with the `CommandResult` it already has (see step 3).

2. When `sawJson == false`, keep the existing behavior exactly: return
   `new Parsed(standardOutput, sessionId)` (plain-text passthrough).

3. Decide where the throw lives, to keep layering clean:
   - Simplest: `parse` itself throws. But `StructuredCliOutput` currently has no
     dependency on the exception type — check whether importing
     `LlmBackendExecutionException` here is acceptable (it's the same package,
     `chatmap.infrastructure.llm`, so it is). If so, throw directly from `parse`.
   - Alternative if you'd rather keep `parse` pure: introduce a small
     package-private `StructuredOutputException`, throw that from `parse`, and have
     each of the three providers translate it to `LlmBackendExecutionException`
     (they already have the `CommandResult` in hand at the call site). Pick one;
     state which in the commit message. Prefer the direct throw unless it creates
     an awkward dependency.

4. Do NOT change the recognized-text path, the session-id extraction, or the
   schema-matching heuristics in `extractAssistantText`. This task is ONLY about
   the empty-result fallback. Broadening what counts as a recognized field is a
   separate concern — if anything, this change makes such gaps VISIBLE so they can
   be fixed deliberately later.

## Tests (add to the StructuredCliOutput test class, or the providers' tests)

- **JSON-but-unrecognized throws:** feed `parse` a couple of valid JSON lines that
  contain NO recognized assistant-text field (e.g. only `{"type":"system",...}`
  and `{"type":"telemetry",...}`). Assert it throws (LlmBackendExecutionException
  or the chosen parse exception), and that the exception message contains the raw
  output (so diagnosis is possible).
- **Plain text still passes through:** feed non-JSON stdout (no lines starting with
  `{`). Assert it returns `Parsed` with `text == standardOutput`, no throw.
- **Recognized text still works:** a normal stream-json sample with a
  `type:"result"`/`result` field still returns the extracted text (guard against
  regressing the happy path).
- **Session id still extracted** in the throwing case is NOT required (we're
  failing the call), but ensure the recognized-text case still surfaces sessionId.

## Constraints

- Confined to `StructuredCliOutput` and, if you choose the translate-at-caller
  approach, the three CLI providers. Don't touch the model-target / channel code.
- The raw output must survive into the failure path — never throw away
  `standardOutput` on the error; it's the only diagnostic when a schema drifts.
- Bound the raw output in the message if it could be huge (stream-json can be
  large) — include up to a few KB, then elide, so a giant NDJSON dump doesn't
  bloat the failure report unreasonably. Use judgment; a simple length cap with a
  "... (truncated)" marker is fine.
- `./gradlew check` passes.

## Validation

`./gradlew check` passes; the new "unrecognized JSON throws with raw output in the
message" test is present and green; plain-text passthrough and recognized-text
paths are unchanged.
