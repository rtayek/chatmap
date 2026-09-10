# Handoff: Pending Doc Edits and Open Work

From: chatmap (planning session, claude.ai)
To: Claude Code (running directly in the cjatmanager working copy)
Date: 2026-09-10
Repo: github.com/rtayek/chatmap
Local clone: C:\Users\ray\eclipse-workspace\cjatmanager

## Note on delivery

Everything below was worked out against a throwaway sandbox clone, since
claude.ai has no push/write access to Ray's real working copy. You are
running directly in the real working copy with full filesystem access, so
there is no patch file to apply -- just make these edits directly and show
Ray the diff before committing, per AGENTS.md.

Everything in this handoff and the four referenced below was written from
a disconnected sandbox clone and secondhand descriptions (terminal output
Ray pasted, files read in that sandbox). Where you can check the real repo
or real filesystem directly instead of trusting a description here, do
that -- it takes priority over anything written in these handoffs.

## Part 1: Two pending documentation edits

Check first whether each has already been applied (Ray may have applied
one or both by hand already from earlier patch files) -- run git diff /
git log to check before re-editing.

### 1a. .llm/design.md -- Import section scope note

design.md's "Import" section currently reads as if format-based importers
(plain text, Markdown, ChatGPT JSON, ChatGPT archive ZIP) are the whole
import picture. They are not -- the six-source ChatProvider system (CLI-
history readers for Claude Code, Codex, Gemini; live web-CDP readers for
Claude, ChatGPT, Gemini) is real, tested, and documented in
implementation-notes.md, just not cross-referenced from design.md.

Add this paragraph immediately after "All importers produce normalized
chat data." and before "Current import behavior:":

    This section covers format-based importers only. Live/local acquisition via
    the six-source ChatProvider system (CLI-history readers for Claude Code,
    Codex, and Gemini; live web-CDP readers for Claude, ChatGPT, and Gemini) is
    documented in `implementation-notes.md` under "Supported Live Provider &
    Automation Capabilities."

### 1b. .llm/working-context.md -- layer-boundary enforcement, Deferred list

Add this bullet to the end of the "## Deferred" list (after "embeddings,
semantic search, and broad UI redesign"):

    - layer-boundary enforcement (e.g. domain cannot import infrastructure) via
      an ArchUnit test or Checkstyle ImportControl, in preference to a Gradle
      multi-project split, which Ray does not want

## Part 2: Open dev-work handoffs from this session

These are separate, larger tasks already scoped as their own handoff
documents, delivered earlier in this session. Not required reading to do
Part 1 above -- listed here so nothing gets lost. Ray should say which (if
any) to pick up next; do not start these without him choosing.

1. **A2A recorder, production wiring.** A2aTaskRecorder exists and is
   tested on master, but is only exercised via ModelRecordingClient, which
   records into an isolated temporary ChatMap home. No wiring exists into
   the production home/database path, and nothing triggers it
   automatically. Read src/chatmap/a2a/experiment/A2aTaskRecorder.java and
   ModelRecordingClient.java directly rather than relying on the summary in
   handoff-chatmap-a2a-recorder-2026-09-09-1500.md, which was written from
   a sandbox clone. Also read design.md's Coordination Boundary section
   first -- it describes this as intentionally bounded, not an oversight.

2. **Gemini Takeout importer.** New, fourth import path -- Google Takeout
   export of Gemini conversation history, JSON despite a .txt extension,
   flat conversation_turns array. Real source files are at
   ~/tmp/takeout-20260909T004313Z-1-001.tgz (small, has the actual
   conversation) and ~/tmp/takeout-20260909T004313Z-001.tgz (just
   archive_browser.html, not useful). Extract and inspect the real archive
   directly rather than relying only on the one sample described in
   handoff-chatmap-gemini-takeout-importer-2026-09-10-2030.md -- that
   handoff is based on a single head -20 of one file, not the full archive.
   See that handoff for the scoped task, open questions on
   Workspace-vs-personal export format and turn_index contiguity, and the
   existing-code comparison (ChatGptArchiveImporter /
   ChatGptConversationParser pattern).

3. **Paste-box capture tool.** See
   handoff-chatmap-paste-box-capture-2026-09-09-1500.md.

4. **Clipboard-relay / plain-text stripper.** See
   handoff-chatmap-clipboard-relay-2026-09-09-1500.md.

## Naming convention note

No handoff naming convention is currently documented anywhere in the repo
(.llm/handoffs/README.md does not specify one, and at least four different
patterns are in active use in .llm/handoffs/). Ray deferred deciding this
during this session -- do not invent or impose a convention unilaterally.
