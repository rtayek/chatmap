# ChatMap Prompt Router — Codex Handoff

## Purpose

Add a small binary prompt router to ChatMap to reduce unnecessary use of expensive frontier LLMs.

For each incoming developer prompt, the router decides:

- `LIGHTWEIGHT`: routine, localized, well-defined work.
- `MONSTER`: broad, ambiguous, architectural, multi-file, or high-impact work.

The initial goal is deliberately binary. Do not build a multi-level complexity system yet.

## User Problem

The user frequently reaches monthly or daily spending limits on major frontier LLMs. ChatMap should use lightweight models for ordinary work and reserve powerful models for tasks that genuinely require broad reasoning or repository-wide understanding.

The classifier itself should be inexpensive. Prefer deterministic Java rules first; do not spend a frontier-model request merely to decide which model should receive the request.

## Important Project Identity

This work belongs to **ChatMap**, not OpenClaw.

OpenClaw may eventually be one provider or source, but it is not the project identity for this feature.

The router must also preserve the active software project context. A prompt being worked on in project `Foo` must never accidentally receive `Bar` conversation history.

## Initial Scope

Implement a CLI-first vertical slice that can:

1. Select or identify an active project.
2. Read an incoming prompt.
3. Classify it as `LIGHTWEIGHT` or `MONSTER`.
4. Select a provider/model route.
5. Send the prompt through the selected provider adapter.
6. Persist the turn with its project, conversation, classification, provider, and model.

The core must be usable without a GUI. A GUI project selector can be added later using the same application services.

## Recommended Architecture

Keep routing separate from presentation and provider-specific code:

```text
CLI or future GUI
        |
Project/session manager
        |
Prompt classifier
        |
Provider/model router
        |
Provider adapter
        |
Conversation persistence
```

Likely responsibilities:

- `ProjectContext`: active project identity and repository/workspace information.
- `ConversationContext`: logical ChatMap conversation identity and provider/session identity.
- `PromptClassification`: `LIGHTWEIGHT` or `MONSTER`, confidence, and reasons.
- `PromptClassifier`: deterministic classification rules.
- `ModelRoute`: selected provider/model and routing decision.
- `PromptRouter`: orchestration of classification, route selection, provider call, and persistence.

Use existing ChatMap domain/application/infrastructure layering. Do not let the CLI or UI contain routing rules.

## First Classification Rules

Classify as `MONSTER` when the prompt indicates any of the following:

- Multiple files, packages, modules, or repository-wide scope.
- Architecture, redesign, migration, refactor, audit, or system-wide analysis.
- Gradle, build configuration, dependencies, schemas, APIs, or persistence changes.
- Cross-component debugging or an unclear root cause.
- A request to review everything, find all problems, or trace behavior across the system.
- A change that requires coordinated edits and repeated build/test verification.

Classify as `LIGHTWEIGHT` for isolated, well-defined work such as:

- Explaining a concept or error.
- A local syntax or compile fix.
- A small, isolated method or class change.
- Generating a simple script or unit-test boilerplate.
- A straightforward conversion or formatting task.

When the rules are uncertain, choose `MONSTER`. A false escalation costs money, but a false lightweight classification can produce an incomplete or unsafe result.

Do not rely only on prompt length. Long prompts may be simple, while short prompts may describe a repository-wide architectural change.

## Classification Result

Return a structured result similar to:

```text
classification = MONSTER
confidence = 0.78
reasons = [MULTI_FILE_SCOPE, BUILD_IMPACT]
```

Although the decision is binary, preserve confidence and reasons so the rules can be evaluated and improved later.

## Project and Conversation Isolation

Every routed turn must carry an explicit project identity:

```text
project = chatmap
workingProject = foo
conversation = foo-current-task
```

At minimum, persist:

- ChatMap project identity.
- Active software-project identity, such as `foo` or `bar`.
- Conversation/thread identity.
- Repository or working-directory association when available.
- Prompt and response.
- Classification and reasons.
- Provider and model used.
- Provider-specific session/conversation identity.
- Timestamps and request status.

Do not send global ChatMap history to a provider. Context sent during a model switch should come only from the active project and logical conversation, preferably as a compact handoff or selected prior turns.

## CLI Direction

Start with a command-line interface, for example:

```bash
chatmap route --project foo
chatmap projects
chatmap classify --project foo "Review the persistence layer"
```

The exact command names may follow existing ChatMap CLI conventions.

The CLI should make the selected project and routing decision visible before execution. A later GUI should provide a project selector and call the same core services; it should not duplicate the classifier.

## Provider Routing

Use provider/model abstractions rather than hard-coding a specific vendor into the classifier.

Example route configuration:

```text
LIGHTWEIGHT -> lightweight provider/model
MONSTER     -> frontier provider/model
```

The initial implementation may use stubs or existing provider adapters if live provider command contracts are not yet stable. Do not build a JavaFX prompt UI before provider-specific command contracts and durable conversation identity are reliable.

## Existing Project Constraints

- Java 25.
- Gradle command-line builds are authoritative.
- Use `./gradlew` on Unix/WSL and `gradlew.bat` on Windows.
- Eclipse is a plain Java/JDT project without Buildship.
- Use `./gradlew eclipse` when Eclipse metadata needs regeneration.
- Follow existing ChatMap layered architecture and persistence conventions.
- Preserve raw chats as evidence; AI-derived routing metadata is additional working state.
- Keep provider provenance explicit and durable.

## Tests Required

Add tests for:

- Lightweight classification of local, unambiguous prompts.
- Monster classification of multi-file and architectural prompts.
- Gradle/build/dependency impact.
- Ambiguous prompts defaulting to monster.
- Case-insensitive matching and harmless punctuation.
- Project isolation: Foo context is never attached to Bar.
- Conversation continuity when the selected provider changes.
- Persistence of classification, reasons, project, provider, and model.
- CLI behavior with an explicitly selected project.

Run the authoritative checks with:

```bash
./gradlew test
```

## Non-Goals for the First Pass

- No GUI chat client yet.
- No multi-level complexity scoring.
- No automatic use of two frontier models for every difficult prompt.
- No embeddings or semantic retrieval required for the classifier.
- No global conversation mixing.
- No assumption that a particular provider is permanently the lightweight or monster provider.

## Completion Criteria

The first slice is complete when a developer can select project `Foo`, submit a prompt, see a binary classification and reason, route it to the configured provider/model, and verify that the resulting turn is stored under Foo’s conversation rather than another project’s.

## Suggested Implementation Order

1. Inspect the current ChatMap provider, conversation, persistence, and CLI abstractions.
2. Add the classification value objects and deterministic classifier.
3. Add project/session context propagation.
4. Add route selection and provider adapter integration or a test double.
5. Add persistence for routing metadata and provider session identity.
6. Add CLI commands and visible decision output.
7. Add tests, run `./gradlew test`, and review for project-context leakage.

## Who Should Receive This Handoff

Give this to **Codex working in the ChatMap repository** as an implementation handoff. It is also suitable for Claude Code or another coding agent, provided that agent is explicitly told that the target repository and project are ChatMap.

