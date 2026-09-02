# Handoff: ChatMap Overview - Continue

Date: 2026-08-31
Project: chatmap
Purpose: retire long-running "ChatMap overview" chat, resume in a fresh one.

## Repo state (verified against origin/master, commit 4775138)

- **Worker-lifecycle vertical slice is merged and canonical.** State machine
  (QUEUED -> WORKING -> WAITING_FOR_DECISION -> COMPLETED|FAILED|CANCELLED ->
  RETIRED), transactional service layer with event logging, semantic handoff
  storage gated on COMPLETED, successor linkage. A 603-line SQLite soak
  harness was added right after the merge. implementation-notes.md now lists
  this as milestone 11 (durable assignments/sessions/lifecycle
  events/artifacts/semantic handoffs/retirement/successor chains) -
  no longer experimental, it's architecture.

- **HandoffWatcher was rewritten and merged (feature/handoff-watcher-intake).**
  Polling + stability-check (file size/mtime settle) instead of WatchService +
  fixed delay. Multi-source, single-inbox. Collision-safe naming (-1, -2
  suffixes). --once mode. Flag-based CLI (--inbox, --source).
  **Project-routing (the old PROJECTS map / filename parsing) was removed,
  not hardened back in.** This was discussed at length and resolved
  deliberately: implementation-notes.md now states explicitly that
  HandoffWatcher "is a transport utility that collects stable handoff files
  into an inbox; it does not interpret or route their contents." Routing, if
  it happens, lives elsewhere (ties into ChatMap's own domain model, not a
  second parallel routing table in the watcher). This decision is now
  written down - do not re-litigate it without a reason.

- **design.md updated:** ChatMap's longer-term purpose is now stated as
  extracting durable semantic knowledge from conversations and keeping it
  current as projects evolve, while preserving provenance/history of every
  accepted knowledge object. References a new first-principles.md file that
  has not yet been reviewed in this chat - worth reading next session.

- Small stuff also landed: null-pointer guard fix in HandoffWatcher.java,
  regression test coverage for the ChatGPT JSON identity fix, Eclipse
  project rename (cjatmanager -> chatmap) fully propagated, project-home.html
  simplified (has some hardcoded chat-specific links that will go stale -
  minor, not urgent).

## Open thread 1: escalation-chain / bubble-up model (NOT written down anywhere else)

Ray's own formulation, arrived at independently before finding it echoed in
the A2A protocol's delegation-chain example:

- When a worker hits WAITING_FOR_DECISION, it doesn't need to know who
  ultimately resolves it. It just reports back to whoever called it.
- The caller has exactly two choices: resolve it if it's within its own
  authority, or pass it up unresolved to whoever called *it*.
- This is structurally like an Optional/Maybe climbing through caller
  identity: empty = "not mine, passing up," present = "resolved, here's the
  outcome, continue."
- No level needs special-case logic for "am I the top" - it just tries, and
  if it can't, it doesn't. It only ever reaches Ray because nothing below him
  could resolve it, not because the system is hardwired to always ask him.
- Explicitly rejected: a doc from another LLM proposed a fixed
  "Heavy Manager Backend" that auto-resolves and reinjects a fix without a
  human in the loop. That inverts what WAITING_FOR_DECISION is for. The
  bubble-up-to-a-human model is what Ray wants, not autonomous resolution by
  default.
- Gap: WorkerLifecycleRecord currently captures predecessor/successor in the
  completed-handoff sense, but it's not confirmed whether it captures
  "who called me" for an in-progress escalation to bubble up through. Worth
  checking the schema before designing further.

Action needed: write this into a proper handoff doc of its own. It currently
only exists in this chat's history.

## Open thread 2: A2A (Agent2Agent) protocol - investigation started, not finished

- github.com/a2aproject - originally Google, now Linux Foundation. Open
  standard for interop between independent/opaque agent systems built on
  different frameworks or vendors. SDKs in Python/JS/Java/Go/C#/Rust.
- It's wire-protocol/plumbing only: Message, Task, Part, Artifact,
  AgentCard (capability discovery). No ledger, no six-questions concept, no
  persistence-for-later-review. That's still ChatMap's job, not A2A's.
- Notable: A2A's task lifecycle (submitted -> working -> input-required ->
  completed|failed|canceled|rejected) closely mirrors ChatMap's own
  WorkerLifecycleState, independently arrived at. input-required ==
  WAITING_FOR_DECISION conceptually.
- A2A's own delegation-chain example (shopping agent -> specialist agent ->
  market-data agent -> exchange agent, caller doesn't need to know the
  topology) is very close to Ray's escalation-chain idea above - good
  external validation.
- Framing to keep: NOT "replace ChatMap's model with A2A." Rather: "should
  ChatMap's worker adapters speak A2A on the wire, while ChatMap's own
  ledger/six-questions model stays the layer above it." Adopt the plumbing,
  keep the architecture.
- Next step, not yet done: clone the A2A spec repo (pin to the 1.0.0 release
  tag, not main/dev, which is a moving draft) and a2a-java, compare
  AgentCard/Task shapes against WorkerLifecycleState/WorkerLifecycleRecord
  directly.
- Repos worth pulling: `A2A` (spec, read-only), `a2a-java` (SDK, if this
  gets built into ChatMap), `a2a-samples` (example multi-agent workflows,
  useful to sanity check A2A's "task" concept against the six questions
  before committing any code).

## Open thread 3: two small tools discussed today, neither built yet

Two genuinely different problems surfaced, need two different tools, not one:

1. **Capture** ("I asked an LLM for a list of trees, I want it saved.")
   Asynchronous, land-it-and-forget. Solution: a small paste-box page/artifact
   with a project dropdown (+ optional second project), auto-filled
   timestamp, and a Download button that names the file correctly for
   HandoffWatcher to pick up. Not yet built.

2. **Transfer** ("I want to copy output from LLM A and paste it into LLM B
   right now, without tabbing/funny-character garbling.")
   Synchronous, mid-conversation, waiting on it. A downloadable file is the
   WRONG tool here - adds a filesystem round-trip to what's already
   clipboard friction. Right shape: a clipboard-relay tool - paste in, it
   strips smart quotes/em-dashes/non-breaking-spaces/fancy Unicode down to
   plain ASCII, one-tap Copy button out. No file, no Downloads folder,
   no HandoffWatcher involvement. Not yet built.

Ray said he had no strong preference on exact implementation mechanism for
either - the paste-box-with-download-button shape was chosen for capture
specifically because it doesn't depend on any LLM's in-chat judgment call
about whether something "deserves" a file - it's a form, not a request to an
LLM.

## Resolved: the "every response is a downloadable .md" rule

Final, agreed-on wording - ready to go into a persona/response-format file
(Ray was deciding between human.md and a new "responses" section as of end
of this chat):

---
**Handoff File Rule**

Every response you give me must also be provided as a downloadable .md
file. No exceptions, no judgment calls about whether it "counts."

Filename, exactly this shape, every time:
handoff-<from-project>[-to-<to-project>]-<YYYY-MM-DD-HHMM>.md

- <from-project> = the project we're working on. If unknown, ask once at
  the start of the conversation. If never told, use "misc".
- -to-<to-project> = only if told this is going to a second project.
  Otherwise omit entirely.
- Timestamp = 24-hour local time, no colons or slashes
  (2026-08-31-1442).

Content rule: plain ASCII only. No smart quotes, em-dashes, curly
apostrophes, non-breaking spaces, or other fancy Unicode.

No exceptions. Even a one-line answer gets a file. Ray decides what to do
with it, not the LLM.
---

Rationale for "no exceptions" over a scoped/triggered rule: a rule with a
judgment-call branch ("only when it seems like a handoff") is exactly what's
been failing - some LLM decides a given response doesn't count. A rule with
zero conditionals can't be misapplied. Cost of over-triggering is
near-zero since Ray doesn't have to click Download, and self-describing
filenames mean stray files are harmless clutter, not noise that gets
routed anywhere by HandoffWatcher.

## Small unrelated note

No font-size slider found in Ray's mobile browser session (three-dot menu,
no appearance/display option located). Native app install and OS-level
accessibility text-size are the fallbacks; pinch-zoom preferred over OS
text-size scaling since OS scaling breaks box/card layouts for Ray. Ray
prefers short, plain-paragraph responses generally, given low vision -
already the operating mode for this chat, carry forward.

## Suggested first move in the new chat

Probably: either (a) write the escalation-chain idea up as its own formal
handoff doc since it doesn't exist anywhere but chat history yet, or
(b) start the A2A investigation for real (clone spec + a2a-java, compare
against WorkerLifecycleState). Ray should pick which; both are live and
neither blocks the other.