# Handoff: end-to-end test for provider fetch -> persist -> search -> export

Gap found while reviewing test coverage on 2026-08-11. Concrete, not a general
"add more tests" ask.

---

## What's covered today

- `SampleRoundTripTest` (tst/chatmap/service/) — file-based imports
  (plainText, markdown, chatgptJson) through `ImportService.importFile()`,
  then search, then `ExportService.exportChatMarkdown()`. Good full-pipeline
  coverage for the *file* import path.
- `CliHistoryProvidersTest` (tst/chatmap/backend/providers/) — exercises
  `provider.listChats()` and `provider.fetch(candidate)` for Claude Code,
  Codex, and Gemini CLI history providers, with fixtures grounded in real
  file shapes (the Gemini ones specifically note they match real captured
  logs, added after the parser bug found on 2026-08-10).

## What's missing

Nothing connects those two. `CliHistoryProvidersTest` stops at
`provider.fetch(candidate)` — it never calls `ImportService.persist(...)`,
never searches, never exports. The actual pipeline
`ImportAllChatsCli` runs in production is:

```
provider.listChats() -> provider.fetch(candidate) -> ImportService.persist(imported)
    -> (later) searchable via FTS5, exportable via ExportService
```

No automated test exercises that full chain for any provider-based (as
opposed to file-based) import. This is the exact seam where the Gemini
CLI content bug lived — `fetch()` returning near-empty `ImportedChat`
objects that nonetheless persisted "successfully" (zero messages isn't an
error condition to `ImportService`). A test chaining fetch straight into
persist-and-search wouldn't have prevented that specific bug (it was a
parser issue, would need real-shaped fixtures either way) but it would
catch anything *downstream* of fetch — a persist-layer regression, a
dedup-path issue specific to provider-sourced imports, a search-indexing
gap for provider-imported messages that behaves differently than
file-imported ones.

## Suggested test

New test, e.g. `tst/chatmap/service/ProviderRoundTripTest.java`, following
`SampleRoundTripTest`'s existing shape (in-memory SQLite, real
`ImportService`/`ExportService` instances). For at least one CLI-history
provider (Codex is probably easiest — simplest JSONL shape, already has a
working fixture pattern in `CliHistoryProvidersTest`):

1. Write a realistic session file to a `@TempDir`.
2. `provider.listChats()` to get a candidate.
3. `provider.fetch(candidate)` to get an `ImportedChat`.
4. `importService.persist(imported)` — assert `Outcome.inserted`.
5. Search for known content from the fixture — assert it's found.
6. `exportService.exportChatMarkdown(chat.id())` — assert the exported
   markdown contains the expected turns.
7. Optionally: fetch and persist the *same* candidate again — assert
   `Outcome.unchanged`, proving the provider path's dedup behaves the same
   as the file-import path already tested in `SampleRoundTripTest`.

If time allows, repeat for Claude Code and Gemini too — same shape, just
different fixture content — so all three CLI-history providers have one
real end-to-end proof each, not just parse-level coverage.

## Explicitly out of scope for this handoff

- Web providers (Claude/ChatGPT/Gemini via CDP) — genuinely hard to test
  this way without a live browser; not asking for that here.
- `ImportAllChatsCli` itself (the CLI entry point, multi-provider
  orchestration, skip-already-imported logic) — a good next step after this
  one, but a separate, larger piece of work. This handoff is scoped to
  proving the single-provider pipeline works end-to-end at the service
  layer first.
