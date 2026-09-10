# Handoff: Paste-Box Capture Tool

From: chatmap (planning session)
Date: 2026-09-09 15:00
Repo: github.com/rtayek/chatmap
Local clone: C:\Users\ray\eclipse-workspace\cjatmanager

## Context

Ray's workflow treats each LLM as an autonomous worker; he pastes results
back into ChatMap by hand today. A lightweight capture page was identified
as a gap: a paste-box with a download button, for asynchronous capture of
LLM output that then gets fed into the handoff pipeline.

## Task

Build a paste-box-with-download-button page: user pastes text (an LLM
response), the page produces a downloadable .md file following the standing
naming rule (no exceptions):
handoff-<from-project>[-to-<to-project>]-<YYYY-MM-DD-HHMM>.md, plain ASCII
only.

## Constraints

- Low-vision and voice-dictation-friendly: large touch targets, minimal
  required typing, works on both phone and desktop.
- Plain ASCII output only -- strip or transliterate anything else.
- Should fit into the existing worker-lifecycle / HandoffWatcher pickup path
  (drop into whatever directory HandoffWatcher polls).
- Naming: kebab-case files, lowerCamelCase identifiers, no underscores in
  string identifiers.

## Decision

- This is a capture utility, not an interpreter -- it does not parse or
  route the pasted content, consistent with HandoffWatcher's role.

## Open

- Where the from-project / to-project fields come from: free-text fields the
  user fills in, a dropdown of known projects, or inferred somehow? Not yet
  decided -- propose an approach.
- Standalone page vs. integrated into the JavaFX app vs. simple local web
  page is undecided.

## Output

Provide the result as a downloadable .md file per the standing rule (no
exceptions), same naming convention as above.
