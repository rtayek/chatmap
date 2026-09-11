---
id: CM-HANDOFF-PARALLEL-REVIEW-2026-09-11
lifecycle: working
status: active
provenance: parallel-read-only-review-c115a9a
---

# Parallel Review Recommendations Handoff

## Purpose

Preserve the recommendations from three independent, read-only reviews of
ChatMap so a later worker can act on them without relying on conversation
scrollback.

This handoff is not canonical project state. Confirm decisions against the
root project documents, current code, and current tests before making changes.

## Review Baseline

- Repository: `rtayek/chatmap`
- Branch: `master`
- Reviewed commit: `c115a9a`
- Review roles:
  - architecture and dependency consistency
  - test coverage and evidence gaps
  - documentation and working-state drift
- No repository changes were made during the reviews.
- No new tests were run as part of the reviews.

## Prioritized Recommendations

### 1. Repair the context index first

Update `.llm/index.md` so a cold-start worker is directed to the complete
authoritative context set.

At minimum, reconcile the index with `.llm/manifest.json` and include:

- `.llm/implementation-notes.md`
- `.llm/manifest.json`
- `.llm/handoffs/README.md` as the handoff usage and naming guide

Describe the handoff area generically rather than naming only selected
handoffs. After the edit, perform a cold-read check: a fresh worker following
only the root discovery file and index should be able to identify the complete
authority order.

### 2. Reconcile the text-character policy

`.llm/human.md` currently requires ASCII-only text while
`.llm/persona.md` requires typographic Unicode punctuation. Choose one
consistent policy and state it once.

The working hypothesis from the discussion is:

- UTF-8 without BOM
- Unix LF line endings
- avoid decorative or fragile Unicode where it creates copy-and-paste trouble
- do not impose ASCII-only unless a demonstrated tool constraint requires it

Treat this as a documentation correction, not as evidence that every existing
file must immediately be rewritten.

### 3. Add a deterministic recorder-failure integrity test

Add a focused test around `A2aTaskRecorder` that forces snapshot or artifact
creation to fail after task execution, then reopens a file-backed database and
checks the durable result.

The key invariant to verify is:

> A session must not remain terminal if its required durable evidence was not
> written successfully.

A deterministic setup could pre-create the expected snapshot path so a
`CREATE_NEW` write fails. The test should then reopen the database rather
than trusting only in-memory state.

If the test exposes a production defect, decide the transaction and recovery
semantics before changing the recorder implementation.

### 4. Audit package dependency edges later

Create an explicit allowed-layer matrix and compare it with imports or package
references. Determine whether `ChatMapControllerFactory` is the only
presentation-layer path that reaches JDBC or composition details.

This is lower priority than repairing context discovery and testing durable
recorder behavior.

## Additional Findings to Reconcile

- `.llm/Artifact-handoff-routing-draft.md` describes an inbox watcher that
  interprets filenames and routes artifacts. That conflicts with the settled
  transport-only watcher and direct-delivery policy. The file is explicitly
  draft and non-authoritative, so reconcile, replace, or archive it before
  promoting any of its rules.
- Completed or retired handoffs remain in the active handoff directory. Apply
  the archive policy in `.llm/handoffs/README.md` when their outcomes have
  been absorbed into canonical documents.
- The MVP summary in `.llm/design.md` should mention the completed Gemini
  importer if that remains part of the current baseline.
- Caller-chain provenance remains an evidence question and deferred design
  topic. Do not silently turn it into a required invariant.

## Strengths Confirmed by the Reviews

- The watcher is consistently described as transport-only in the authoritative
  design and implementation material.
- A2A execution is bounded and isolated by the current design.
- The documentation distinguishes transport success from semantic correctness.
- `.chatmap-local/` is consistently excluded from routine agent reading.
- The manifest filename is consistently `.llm/manifest.json`.
- The existing test suite is broad; the review observed 618 tests in the
  reported baseline, though it did not rerun them.

## Suggested Next Work Unit

Make the documentation-only index and character-policy corrections as one
small, reviewable change. Then add the recorder-failure integrity test as a
separate work unit. Keep production behavior changes separate until the new
test establishes the actual failure semantics.
