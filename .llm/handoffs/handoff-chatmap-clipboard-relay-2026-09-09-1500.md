# Handoff: Clipboard Relay / Plain-Text Stripper

From: chatmap (planning session)
Date: 2026-09-09 15:00
Repo: github.com/rtayek/chatmap
Local clone: C:\Users\ray\eclipse-workspace\cjatmanager

## Context

Second identified gap: synchronous LLM-to-LLM transfer of output currently
carries formatting artifacts (rich text, markdown rendering quirks) when
moved between tools. A clipboard-relay / plain-text stripper was identified
to clean this up in the moment, as opposed to the paste-box tool which is
for asynchronous capture.

## Task

Build a clipboard-relay utility: takes clipboard content, strips formatting
down to plain ASCII text, and makes the cleaned text available again
(re-copy to clipboard, or hand off directly) for pasting into the next LLM
worker.

## Constraints

- Synchronous, low-friction -- this sits in the middle of a live
  back-and-forth between LLMs, so it needs to be fast, not a multi-step
  form.
- Plain ASCII output only.
- Should work across phone and desktop, given Ray's cross-device workflow.
- Distinct from the paste-box tool: no download-file step required here
  unless Ray decides the relay should also emit a handoff .md on demand.

## Decision

- None yet -- this is a smaller, more open-ended item than the recorder or
  paste-box tool.

## Open

- Platform: browser extension, OS-level clipboard watcher, or a page like
  the paste-box tool but without the file-download step? Not yet decided.
- Whether this tool should also optionally emit a handoff .md (tying it into
  the "no exceptions" rule) or stay purely ephemeral/clipboard-only.

## Output

Provide the result as a downloadable .md file per the standing rule (no
exceptions):
handoff-<from-project>[-to-<to-project>]-<YYYY-MM-DD-HHMM>.md, plain ASCII
only.
