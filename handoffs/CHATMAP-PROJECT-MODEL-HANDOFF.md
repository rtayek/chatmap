# ChatMap Project Model — Claude Handoff

## Task

Introduce a small, explicit project model in ChatMap before completing the JavaFX prompt-routing screen.

The current implementation has project-related behavior, but project identity appears to be represented mostly by strings in a registry and by request-time context objects. The GUI needs a real project object so it can present and select known projects safely.

This handoff is intended for Claude working in the `rtayek/chatmap` repository.

## Current Problem

The prompt router already carries project information through `ProjectContext`, but the application does not appear to have a simple persistent domain-level `Project` entity.

That makes it difficult for a desktop UI to:

- List known projects.
- Display project names and locations.
- Select a project reliably.
- Distinguish persistent project identity from a temporary request context.
- Guarantee that Foo and Bar conversation context remain separate.

Do not solve this by adding another unrelated string field to the JavaFX controller.

## Desired Model

Introduce a minimal project entity with stable identity:

```text
Project
  id
  name
  location or repository path
```

Optional description or active status may be added only if the existing persistence design clearly needs them.

A Java record is acceptable if it fits existing project conventions:

```java
record Project(
    String id,
    String name,
    Path location
) {}
```

Use the repository’s existing package, visibility, validation, and persistence conventions. Do not introduce a large project-management framework.

## Keep These Concepts Separate

```text
Project         = persistent project identity
ProjectContext  = project selected for one operation
ProjectRegistry = lookup/discovery of known projects
```

`ProjectContext` should refer to a project identity rather than replacing the project entity. The registry should return project objects or stable project references, not only bare strings.

## Suggested Layers

Adapt names to the current repository:

```text
domain
  Project

application/service
  ProjectService

application/port or infrastructure/persistence
  ProjectRepository

application/service
  ProjectContext
```

The service should provide the small operations needed by the router and GUI, such as:

- List known projects.
- Find a project by stable id.
- Find or create a project when appropriate.
- Convert a selected project into `ProjectContext`.

Do not silently create duplicate projects because two names differ only in capitalization or because the same repository is discovered twice. Follow existing identity conventions and add tests for the chosen behavior.

## Persistence

Inspect the current SQLite schema and repositories before adding tables or columns.

If ChatMap already has a project table or project-related persistence, complete and use it rather than creating a parallel table. If it does not, add only the minimum schema necessary for:

- Stable project id.
- Display name.
- Optional filesystem/repository location.

Keep project persistence separate from prompt-route persistence. A prompt-route record should refer to the project identity; it should not duplicate the entire project object.

## Router Integration

Update the prompt-routing path so it receives an explicit project reference or `ProjectContext` derived from a real `Project`.

The following invariant must remain true:

```text
Foo prompt → Foo project and Foo conversation
Bar prompt → Bar project and Bar conversation
```

Do not send global ChatMap history to a provider. Do not infer the active project only from the latest prompt text.

Preserve the existing routing behavior:

```text
prompt
  → classify as LIGHTWEIGHT or MONSTER
  → select provider/model target
  → execute
  → persist result with project and conversation identity
```

## JavaFX Preparation

The immediate consumer is a future JavaFX prompt-routing screen. The project service should make it possible for the UI to:

1. Request the list of known projects.
2. Display project names.
3. Keep the selected `Project` object or stable id.
4. Create a `ProjectContext` when the user submits a prompt.

The project service must not depend on JavaFX. Keep UI concerns in the controller/view layer.

## Tests Required

Add tests for:

- Project construction and validation.
- Stable lookup by project id.
- Listing known projects.
- Persistence and reload of a project.
- Project location/path handling, including a missing or unknown path if locations are supported.
- No duplicate project identity under the chosen rules.
- Conversion from `Project` to `ProjectContext`.
- Prompt routing under Foo and Bar creates or uses separate project identities.
- The future UI-facing project list does not expose only unstructured strings.

Use fake repositories or in-memory SQLite where appropriate. Do not require a live LLM for project-model tests.

## Scope Limits

Do not include these in this task:

- JavaFX prompt UI implementation.
- Speech, microphone, or text-to-speech support.
- Model catalog/tag-dimension implementation.
- Broad database redesign.
- Multi-project membership for a chat unless the current schema already requires it.
- Gradle or Eclipse workflow changes.
- Provider adapter redesign.

## Existing Constraints

- Java 25.
- Gradle command-line builds are authoritative.
- Use `./gradlew test` for verification.
- Eclipse is used without Buildship.
- Preserve the existing layered architecture.
- Keep project, conversation, provider, target, and model identities explicit and durable.

## Recommended Implementation Order

1. Inspect the current project registry, `ProjectContext`, project service, repositories, schema, and prompt-router persistence.
2. Identify whether project persistence already exists.
3. Add the minimal `Project` model or complete the existing incomplete model.
4. Make the registry return project objects or stable references.
5. Add or complete `ProjectService` and repository operations.
6. Integrate `Project` with `ProjectContext` and prompt routing.
7. Add project-isolation and persistence tests.
8. Run `./gradlew test`.

## Definition of Done

ChatMap has a small real project model with stable identity, the project registry can list and resolve projects, prompt routing uses the selected project explicitly, and the future JavaFX UI can populate a project selector without maintaining its own string-based project system.
