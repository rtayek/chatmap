# ChatMap JavaFX Prompt Screen — Implementation Handoff

## Recipient

Give this handoff to **Claude working in `rtayek/chatmap`**.

Claude is already reviewing the prompt-router correctness fixes and project-model work. Codex is currently working on the separate speech project.

## Goal

Add a minimal JavaFX desktop screen that lets the user type a prompt, see whether ChatMap classifies it as `LIGHTWEIGHT` or `MONSTER`, send it to the selected provider/model, and read the response.

This is a small vertical slice. Do not build a complete chat browser, speech interface, or model-management system yet.

## Desired User Flow

```text
Select project
  → select or enter conversation
  → type prompt
  → click Send
  → classify as LIGHTWEIGHT or MONSTER
  → select provider/model
  → execute prompt
  → display response
  → persist the turn
```

## UI Contents

The first screen should contain:

- Project selector backed by the real `Project` model/registry when available.
- Conversation identifier or current-task field.
- Large prompt text area.
- Send button.
- Visible classification result.
- Visible selected provider, target, and model.
- Response area with copyable text.
- Busy, success, and error status.

Use the existing ChatMap JavaFX application and controller patterns. Do not create a separate application or a parallel routing implementation.

## Existing Services to Reuse

Inspect and reuse the current services, including:

- `PromptRouterService`
- `PromptRoutingResult`
- `ProjectContext`
- `ConversationContext`
- `Project`/`ProjectService`/`ProjectRegistry` work from the project-model handoff
- Existing provider integrations and persistence

The UI must not duplicate:

- Prompt classification.
- Lightweight/monster decision logic.
- Provider/model selection.
- Provider invocation.
- Conversation persistence.

The UI should call the existing router service and render its result.

## Threading

Provider execution must not run on the JavaFX application thread.

Use the project’s existing asynchronous/task conventions, or the simplest appropriate JavaFX background-task mechanism. Keep the UI responsive while classification, provider execution, and persistence occur.

While a request is active:

- Disable Send or prevent duplicate submission.
- Show a clear working state.
- Preserve the submitted prompt.

On completion:

- Update the result on the JavaFX thread.
- Re-enable input.
- Display the response or a readable error.

## Project Isolation

The selected project must be explicit. A prompt for Foo must not use Bar context.

Pass the selected project and conversation into the existing router:

```text
Foo + foo-current-task + prompt
```

Persisted results must retain the correct project and conversation identity, together with classification, provider, target, model, and session information where available.

## Current Routing Scope

The initial router is binary:

```text
LIGHTWEIGHT
MONSTER
```

The current default route targets may still be fixed, for example:

```text
LIGHTWEIGHT → Ollama Qwen 2.5 7B
MONSTER     → Claude
```

Display the actual target returned by `PromptRoutingResult`. Do not hard-code a display label that can disagree with execution.

Do not implement the future model catalog and named tag dimensions in this task.

## Validation and Errors

Reject or clearly report:

- No project selected.
- Empty prompt.
- Invalid conversation identifier.
- Provider unavailable.
- Provider command failure.
- Authentication failure.
- Persistence failure.

Do not silently swallow exceptions. The screen must return to a usable state after an error.

## Accessibility

- Use large fonts.
- Use high contrast.
- Keep the layout simple.
- Support keyboard navigation and activation.
- Make labels explicit.
- Do not rely on color alone for classification or status.
- Keep prompt and response text copyable.

## Tests

Add tests where practical for:

- Sending a lightweight prompt.
- Sending a monster prompt.
- Displaying the classification.
- Displaying the actual selected provider/model.
- Displaying the response from a fake provider.
- Rejecting an empty prompt.
- Preventing duplicate submission while busy.
- Recovering from provider failure.
- Preserving Foo/Bar project isolation.

Do not require live providers for ordinary tests. Use fake or recording providers.

## Constraints

- Java 25.
- Existing ChatMap JavaFX application.
- Gradle command-line builds remain authoritative.
- Use `./gradlew test` for verification.
- Eclipse remains without Buildship.
- No speech or audio dependencies.
- No ChatMap-wide UI redesign.
- No new provider adapter.
- No model catalog implementation.

## Prerequisite Work

Before beginning, check the status of:

1. The four prompt-router correctness fixes:
   - Atomic transaction.
   - Word-bounded `database` classifier matching.
   - CLI option validation.
   - Shared connection-lock helper.
2. The minimal `Project` model and registry work.

If either is incomplete, preserve its design and integrate with it rather than creating temporary duplicate abstractions.

## Definition of Done

From the ChatMap JavaFX application, the user can select a project, enter a conversation, type a prompt, submit it, see `LIGHTWEIGHT` or `MONSTER`, see the selected provider/model, read the response, and verify that the turn was persisted under the correct project and conversation.

Run:

```bash
./gradlew test
```

