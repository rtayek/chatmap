# ChatMap Resume Existing Conversation — Implementation Handoff

## Recipient

Give this handoff to **Claude working in `rtayek/chatmap`** — same agent
already carrying context on the prompt-router, classifier fixes, and
project-model work this builds directly on top of. Not a fit for
Codex (currently dedicated to the `speech` project) or Antigravity
(no multi-file architectural risk here that needs orchestration).

## Purpose

Right now, every prompt sent through the JavaFX prompt-routing screen
creates a brand-new `Chat` row, even when the user is continuing what
should be the same conversation. The infrastructure to properly resume
an existing conversation already exists in the codebase — this handoff
wires it up rather than building it from scratch.

## What Already Exists (do not rebuild)

- `Chat.providerSessionId()` / `channelId()` / `modelTargetId()` —
  fields already present on the domain model specifically for this.
- `ChatRepository.findByPromptSession(providerId, modelTargetId, providerSessionId)`
  — direct lookup of an existing chat by its provider session.
- `ChatRepository` session listing (backing `ImportService.listPromptSessions(target)`
  / `PromptService.listSessions(backendName)`) — lists known session ids
  for a given provider/target.
- `PromptService.submit(backendName, prompt, profile, sessionId)` —
  already accepts a session id and, per its own documented behavior in
  `recordInDatabase`, appends to the existing chat via
  `importService.appendToConversation(...)` instead of creating a new
  one when a session id is present.
- `MessageRepository.findByChat(chatId)` — returns the full ordered
  transcript (`ORDER BY sequence`) for any saved chat. Full
  reconstruction of a saved conversation's history already works
  today; this is not new work.
- Existing fake/stub `LlmProvider` test doubles, already used in
  `PromptServiceTest` and `PromptRouterServiceTest`. This handoff's
  tests should reuse this exact pattern rather than inventing a new
  test-double mechanism.

## The Actual Gap

`PromptRouterService.route()` calls `promptService.submitForProject(...)`,
which hardcodes `sessionId` to `null` on every call:

```java
public PromptResult submitForProject(String backendName, String prompt, long projectId) throws SQLException {
    return submit(backendName, prompt, PromptProfile.general, null, projectId);
}
```

The `ConversationContext.id()` typed into the JavaFX "Conversation"
field is captured into `PromptRouteRecord` as metadata but never used
to look up a stored session and pass it back in. The continuation
machinery is real and working; the new routing path just doesn't call
into it yet.

## Design: One Shared Resolution Layer, Two Independent Consumers

Do not build "local resume" and "live resume" as two separate
features. There is one shared piece both need — resolving which
`providerSessionId` (if any) corresponds to what the user picked — and
two consumers of it that can be built and verified independently:

1. **Read-only consumer**: given a resolved chat, load and display its
   full history via `findByChat`. Pure DB read, no provider call, no
   network dependency. Build and test this first — it's the fastest
   feedback loop and has zero risk.
2. **Live consumer**: given a resolved `providerSessionId`, pass it
   into `PromptService.submit(...)` so a new turn appends to the
   existing chat instead of creating a new one. **Test this against
   the existing fake-provider pattern before wiring in any real
   provider.** The fake-provider path proves the entire pipeline —
   resolution, `submit()`, `recordInDatabase`'s append-vs-create
   branching, persistence — with no live API calls and no risk to a
   real session. Swapping the fake for a real provider at the end
   should be a small, low-risk step once this is proven, not a
   separate phase of work.

## Required Work

### 1. Session-id resolution

Given a `ConversationContext.id()` (or a directly-picked `Chat`),
resolve the `providerSessionId` to continue, if one exists. Decide the
lookup key:

- By the app-level `conversationContext.id()` string the user types —
  requires mapping that string to a stored session, likely via the
  most recent `PromptRouteRecord` for that conversation id (check
  `PromptRouteStore`/`PromptRouteRecord` for whether this lookup
  already exists or needs adding).
- By directly selecting a `Chat` from a picker (see below) and reading
  its `providerSessionId()` straight off the record — simpler, and
  avoids needing the conversation-id-to-session mapping at all if the
  UI lets the user pick the chat directly rather than retyping an
  identifier string.

Recommend the second approach unless there's a reason the free-text
conversation id field needs to keep working as the primary resume
mechanism — picking from a list is less error-prone than requiring an
exact string match on a hand-typed field.

### 2. Picker / lookup UI

A way to browse and select an existing conversation to resume,
backed by `ChatRepository`'s existing session-listing capability
and/or a chat list filtered by project. Reuse existing list/selection
UI patterns already in `ChatMapViewBuilder` and `ChatListState` rather
than building a new list-selection widget from scratch.

### 3. Wire resolved session id into the routing path

Either extend `submitForProject` to accept an optional session id, or
have `PromptRouterService.route()` resolve it and call the more
general `submit(...)` overload directly instead of
`submitForProject`. Whichever path is chosen, `PromptRouteRecord`
should continue to persist the effective session id used, same as it
does today.

### 4. History display

Once a chat is resolved (whether newly created or resumed), display
its full prior history via `findByChat` before/alongside the new
prompt-response turn, so the user can see what they're continuing
rather than only seeing the latest exchange.

## Tests Required

- Session resolution: given a chat with a known `providerSessionId`,
  resolves correctly; given a chat/conversation with none, resolves to
  null (falls back to creating a new chat, current behavior).
- **Fake-provider end-to-end test**: submit a prompt with a resolved
  session id against the fake provider pattern already used in
  `PromptServiceTest`/`PromptRouterServiceTest`, confirm it appends to
  the existing chat (message count increases, chat id unchanged)
  rather than creating a new chat row.
- Picker/lookup returns the expected set of resumable
  conversations for a given project (project isolation still holds —
  a picker for project Foo must not surface Bar's conversations).
- History display: `findByChat` output renders in the expected order
  for a chat with multiple prior turns.
- Regression: prompts with no resumed session still create a new chat
  exactly as they do today — confirm this handoff doesn't change
  default (non-resume) behavior.

## Non-Goals For This Pass

- No live external provider testing as part of this handoff's
  completion criteria — the fake-provider path is sufficient
  proof. Live-provider verification can happen afterward, separately,
  once this lands.
- No change to how a *new* (non-resumed) conversation is created.
- No cross-project conversation resume — resuming stays scoped to
  conversations within the currently selected project, consistent
  with existing project-isolation guarantees elsewhere in the router.
- No change to the classifier double-counting issue or the
  `ChatMapController` telescoping-constructor issue — both tracked
  separately, not part of this work.

## Completion Criteria

From the JavaFX prompt screen, a user can pick an existing
conversation (not just type a fresh conversation id), see its prior
history, submit a new prompt, and confirm — via a fake-provider-backed
test — that the new turn appended to the existing chat rather than
creating a new one. Live-provider continuation is a documented
follow-up, not required for this handoff to be considered done.

Run:

```bash
./gradlew test
```
