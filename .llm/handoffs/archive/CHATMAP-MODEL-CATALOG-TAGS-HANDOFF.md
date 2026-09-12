# ChatMap Model Catalog and Tag Dimensions — Codex Handoff

## Context

ChatMap is gaining a binary prompt router:

- `LIGHTWEIGHT`: routine, localized, well-defined work.
- `MONSTER`: broad, ambiguous, architectural, multi-file, or high-impact work.

The router may eventually choose among a very large number of models. Most models will come from providers we already access, especially ChatGPT, Claude, and Gemini, with additional local or external providers such as Ollama.

The model catalog must scale without adding a Java class or routing branch for every model.

## Main Design Decision

Represent models as catalog data, not hard-coded provider logic.

Provider adapters know how to communicate with a provider. Model records describe available targets. Routing policies select among enabled model records.

```text
Provider adapter
    → model/target catalog
        → named tag dimensions
            → routing policy
                → selected model
```

## Provider and Model Identity

Keep these identities separate and durable:

```text
provider = Claude
target   = claude-code
model    = sonnet
```

Persist the exact provider, target, and model used for every routed turn. Do not silently replace them with a generic `default` or with the current provider default.

Provider adapters remain provider-specific. Each adapter owns command construction, parsing, resumption, and session discovery. A shared process runner is acceptable, but do not assume that different CLI clients use the same command grammar.

Multiple models from one provider should normally share one adapter. For example, multiple Ollama models should use one configurable Ollama adapter with different model targets.

## Named Tag Dimensions

Use a set of named tag dimensions rather than one unstructured tag list. A model may have multiple values in each dimension:

```text
provider:     Claude
family:       Sonnet
cost:         low
speed:        fast
capability:   coding, general
context:      large
availability: cli
status:       enabled
```

Possible dimensions include:

- `provider`
- `family`
- `cost`
- `speed`
- `capability`
- `context`
- `availability`
- `status`
- `modality`
- `trust` or operational maturity

Do not implement every possible dimension merely because it is listed here. Start with the dimensions needed for routing and configuration.

## Routing Rules

Routing should select from catalog records using predicates, not model-name conditionals.

Example lightweight policy:

```text
classification = LIGHTWEIGHT
status = enabled
cost = low
```

Example monster policy:

```text
classification = MONSTER
status = enabled
capability includes architecture
context = large
```

The initial router remains binary, but the catalog should be capable of supporting later refinements such as coding versus general work.

If several models match, use an explicit deterministic ordering or priority field. Never rely on database order or map iteration order.

## User-Facing Selection

Do not present a massive flat model list. Present useful groups or filters:

- Lightweight
- Monster
- Local
- Coding
- General
- Experimental
- Currently available

Allow an explicit override for testing or expert use:

```bash
chatmap route --project foo --model claude-opus
```

The override must still validate that the selected model is configured and enabled.

## Project Isolation

Every model-routing operation must retain the active software project and logical conversation:

```text
ChatMap project = chatmap
working project = foo
conversation = foo-current-task
provider = Claude
target = claude-code
model = sonnet
```

Model selection must never cause Foo and Bar conversation context to mix. Persist the project identity, provider/model identity, and provider-specific session identity together with each turn.

## Suggested Data Model

Names are illustrative; adapt them to existing ChatMap conventions:

```text
ModelTarget
  provider
  target
  model
  displayName
  tagsByDimension
  priority
  enabled
  configuration
```

The tag structure may initially be represented with normalized tables or a carefully controlled serialized configuration. Prefer the approach consistent with the existing ChatMap persistence model. Avoid premature general-purpose taxonomy machinery.

## Configuration

The catalog should be configurable so adding a model is primarily a data change:

```text
provider: ollama
target: local
model: qwen2.5:7b
tags:
  cost: [local]
  speed: [fast]
  capability: [coding, general]
  status: [enabled]
```

Secrets and credentials must remain outside the model catalog. The catalog may refer to a configured credential or provider profile, but must not contain secret values.

## Tests Required

Add tests for:

- Multiple models sharing one provider adapter.
- Tag membership across several dimensions.
- Matching lightweight and monster routing policies.
- Disabled models never being selected.
- Deterministic selection when several models match.
- Explicit model override and validation.
- Exact provider/target/model persistence.
- Project and conversation isolation.
- Configuration with model names containing punctuation, versions, or colons, such as Ollama names.

## Non-Goals

- Do not implement a GUI model-management screen yet.
- Do not create a separate adapter for every model.
- Do not make the classifier itself depend on model tags beyond route selection.
- Do not assume one permanent model for `LIGHTWEIGHT` or `MONSTER`.
- Do not add embeddings or semantic model comparison yet.
- Do not mix provider provenance between web sources, CLI sources, and local prompts.

## Existing ChatMap Constraints

- Java 25.
- Gradle command-line builds are authoritative.
- Use `./gradlew` on Unix/WSL and `gradlew.bat` on Windows.
- Eclipse remains a plain Java/JDT project without Buildship.
- Preserve the existing layered architecture.
- Keep provider/session identity durable and explicit.
- Run the authoritative test suite with:

```bash
./gradlew test
```

## Recommended Implementation Order

1. Inspect existing provider, model, conversation, and persistence abstractions.
2. Introduce or refine explicit provider/target/model identity.
3. Add a minimal catalog record and named tag dimensions.
4. Add configuration loading for several existing models.
5. Implement policy-based selection for `LIGHTWEIGHT` and `MONSTER`.
6. Add explicit model override support.
7. Add persistence and project-isolation tests.
8. Run the full Gradle test suite and review the resulting design before adding more dimensions.

## Completion Criteria

The feature is complete for its first increment when ChatMap can configure several models from existing providers, classify a prompt, select an enabled model using named tag dimensions, allow an explicit override, and persist the exact provider/target/model together with the correct project conversation.

