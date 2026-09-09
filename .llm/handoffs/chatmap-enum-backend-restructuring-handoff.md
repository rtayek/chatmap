# ChatMap Handoff: Restructure AI Backends for Many Model Targets

## Objective

Restructure ChatMap's prompt backend design so it can grow from the current small set of choices to many deliberately supported model targets without creating one backend class per model.

Keep this implementation intentionally simple and Java-defined.

## Decisions already made

These are requirements, not open design questions:

1. Do not introduce configuration files.
2. Do not introduce a general plugin system or `ServiceLoader` framework.
3. Use a curated `ModelTarget` enum for the supported model choices.
4. Use a `ProviderId` enum for the genuinely different provider or transport families.
5. Use an `EnumMap<ProviderId, AiProvider>` to connect provider identities to provider implementations.
6. Subclass only at the provider/protocol level, where command construction and response behavior genuinely differ.
7. Do not create one subclass per model.
8. Keep the inheritance hierarchy shallow: at most one abstract CLI base class plus final provider subclasses.
9. Preserve existing behavior and existing public target identifiers where practical.

## Starting procedure

Inspect the latest repository state after all other current code reviews and fixes have been pushed.

Run:

```bash
git status --short --branch
git log -8 --oneline
git diff --check
./gradlew test
```

Preserve all existing work. Stop if overlapping uncommitted changes cannot be preserved safely.

Do not commit or push. Ray will review the result first.

## Problem being corrected

The current backend design mixes several different concepts:

- Provider or tool: Claude CLI, Codex CLI, Antigravity CLI, Ollama.
- Transport: command-line process, HTTP service, or another protocol.
- Model: Qwen, GLM, Claude, GPT, and future models.
- Target configuration: executable, model name, endpoint, timeout, and display name.
- Capabilities: sessions, system prompts, structured output, streaming, tools, and file editing.

`StandardCliBackend` currently suggests that the CLI providers differ primarily by binary name. They do not. Their invocation, resume behavior, session discovery, permission controls, and output formats are provider-specific.

At the same time, individual models served through one provider generally do not need separate Java subclasses. For example, multiple Ollama models should share one Ollama provider implementation.

## Required domain types

### `ProviderId`

Introduce or adapt an enum representing the provider/protocol families, for example:

```java
public enum ProviderId {
    claudeCli,
    codexCli,
    antigravityCli,
    ollama
}
```

Follow the repository's existing enum naming convention if it differs.

### `ModelTarget`

Introduce a curated enum representing user-selectable targets. It should contain enough immutable metadata to select the provider and provider model, for example:

```java
public enum ModelTarget {
    claude(
            "claude",
            "Claude",
            ProviderId.claudeCli,
            "default"),

    codex(
            "codex",
            "Codex",
            ProviderId.codexCli,
            "default"),

    qwen7b(
            "qwen-7b",
            "Qwen 7B",
            ProviderId.ollama,
            "qwen2.5:7b"),

    glm9b(
            "glm-9b",
            "GLM 9B",
            ProviderId.ollama,
            "glm4:9b");
}
```

Use the actual currently supported targets and model names found in the latest source. Do not add speculative models merely to make the enum appear comprehensive.

The enum should provide:

- Stable external ID used by the CLI, UI, and persistence boundary.
- Human-readable display name.
- `ProviderId`.
- Provider-specific model name or target name.
- Explicit capabilities if capabilities vary by target rather than solely by provider.
- A checked lookup such as `ModelTarget.require(String id)` with a useful unknown-target error.

Validate that stable IDs are unique and nonblank.

## Provider abstraction

Define or adapt a provider-facing interface along these lines:

```java
public interface AiProvider {
    AiResponse execute(ModelTarget target, AiRequest request);

    Set<AiCapability> capabilities(ModelTarget target);
}
```

Use repository terminology where it is already clearer. Avoid needless renaming that creates a large mechanical diff.

The important contract is:

- `ModelTarget` identifies what the user selected.
- `AiProvider` implements how that provider is invoked.
- `PromptService` coordinates selection, capability validation, execution, and persistence.

## Controlled use of subclassing

Subclassing is appropriate for genuinely shared CLI process mechanics.

A possible shape is:

```java
abstract class CliAiProvider implements AiProvider {
    private final CommandExecutor executor;
    private final Duration timeout;

    @Override
    public final AiResponse execute(ModelTarget target, AiRequest request) {
        CommandRequest command = createCommand(target, request);
        CommandResult result = executor.run(command);
        validateResult(result);
        return parseResponse(target, request, result);
    }

    protected abstract CommandRequest createCommand(
            ModelTarget target,
            AiRequest request);

    protected abstract AiResponse parseResponse(
            ModelTarget target,
            AiRequest request,
            CommandResult result);
}
```

Then use final provider-specific implementations where appropriate:

```text
ClaudeCliProvider
CodexCliProvider
AntigravityCliProvider
```

The abstract base may own only truly identical behavior:

- Process execution.
- Timeout handling.
- Common nonzero-exit handling.
- Working-directory propagation.
- Common construction of execution exceptions.

Provider subclasses must own:

- Exact command syntax.
- Session resume syntax.
- Session discovery, if supported.
- Permission flags.
- Output-format flags.
- Provider-specific parsing.
- Extraction of newly created provider session identity.

Do not force a hook into the abstract class when only one provider uses it. Do not silently ignore a requested capability.

If Ollama continues to use its CLI, it may share the process-execution base only when that produces a truthful abstraction. If Ollama uses HTTP, implement it directly as `AiProvider` instead.

## Provider wiring

Create the providers in the application composition/bootstrap code:

```java
EnumMap<ProviderId, AiProvider> providers = new EnumMap<>(ProviderId.class);
providers.put(ProviderId.claudeCli, new ClaudeCliProvider(...));
providers.put(ProviderId.codexCli, new CodexCliProvider(...));
providers.put(ProviderId.antigravityCli, new AntigravityCliProvider(...));
providers.put(ProviderId.ollama, new OllamaProvider(...));
```

Pass a read-only view into `PromptService`. Do not expose a mutable global map.

At startup, verify that every `ProviderId` referenced by a `ModelTarget` has exactly one configured provider implementation. Fail early with a precise diagnostic if the wiring is incomplete.

Do not add a formal registry abstraction merely to wrap this map. If map-related behavior becomes complicated in the future, it can be extracted then.

## Prompt execution

Change `PromptService` so its public selection boundary accepts a stable target ID or a `ModelTarget`, resolves it once, obtains the provider from the `EnumMap`, validates capabilities, and executes it.

Conceptually:

```java
ModelTarget target = ModelTarget.require(targetId);
AiProvider provider = providers.get(target.providerId());
validateCapabilities(target, provider, request);
AiResponse response = provider.execute(target, request);
```

Keep lookups localized. Arbitrary presentation and infrastructure classes should not independently resolve targets or provider maps.

The UI should enumerate `ModelTarget.values()` in deliberate enum order rather than maintain a separate duplicated list or switch statement.

The CLI should accept the enum's stable external ID, not depend on the Java enum constant spelling.

## Capabilities

Represent relevant capabilities explicitly, using a small enum such as:

```java
public enum AiCapability {
    sessions,
    systemPrompt,
    structuredOutput,
    streaming,
    tools,
    fileEditing
}
```

Use only capabilities the application currently needs. Do not add a large speculative capability taxonomy.

When a request requires an unsupported capability, fail before launching the external process with a clear error naming the target and missing capability. Do not silently drop permission, output-format, session, or system-prompt requests.

## Provider commands

Do not assume the CLI providers share command syntax.

For each locally installed CLI:

1. Inspect its current local `--help` output or authoritative documentation.
2. Implement its command independently.
3. Add focused command-construction tests.
4. When feasible, run a harmless live smoke test.

In particular, do not preserve a generic `<binary> -p`, `--resume`, or `--list-sessions` convention unless it has been verified separately for that provider.

Never log or persist credentials, access tokens, or environment secrets.

## Session identity

Preserve the provider's durable conversation identity through the entire result path.

`AiResponse` or its replacement should be able to return:

- Provider ID.
- Model target ID.
- Provider model name.
- Response text.
- Newly created or resumed provider session ID, when available.
- Duration and other already-supported execution metadata.

`PromptService` should use the returned provider session ID when the caller did not already supply one. Repeated turns in one provider session must append to one ChatMap conversation.

Do not leave `contentHash` stale after appending messages. Recompute it or explicitly redefine the persistence invariant and cover it with tests.

## Compatibility

Preserve current user-facing backend IDs as aliases when practical so existing CLI commands, saved UI state, and tests continue to work.

If an old ID is removed or changed, report it explicitly in the final report and provide the replacement.

Do not change historical imported-chat `Source` values unnecessarily. Provider selection and import provenance are related but distinct concepts.

## Tests

Add focused tests covering at least:

### Enum and wiring

- Every target has a nonblank unique external ID.
- Every target has a display name, provider ID, and provider model name.
- Every target's provider is present in the `EnumMap`.
- Stable ID lookup succeeds.
- Unknown ID produces a useful error.
- UI/display ordering is deterministic.

### Provider implementations

- Claude command construction, permissions, output format, and resume behavior.
- Codex noninteractive command construction and resume behavior.
- Antigravity command construction and continuation behavior.
- Ollama passes the selected target's model name rather than a hard-coded model.
- Unsupported capabilities fail before process execution.
- Timeout, startup failure, and nonzero exit handling remain correct.
- Provider-created session IDs are returned when available.

### Prompt service

- Target ID selects the correct provider.
- Two different models using the same provider are dispatched with different model names.
- Unknown target fails without invoking a provider.
- A resumed session appends to one ChatMap chat.
- A newly created provider session is persisted and subsequently reusable.
- Appending messages leaves content metadata consistent with the transcript.

Use fake command executors and provider doubles. Automated tests must not require installed CLIs, live accounts, or network access.

## Non-goals

Do not implement any of the following in this pass:

- Configuration files.
- Runtime downloading or discovery of every available model.
- External plugin loading.
- Automatic model routing such as `fast`, `cheap`, or `best`.
- A subclass for every individual model.
- A deep inheritance hierarchy.
- Broad UI redesign beyond replacing the existing backend list with the enum-defined targets.

## Validation

Run:

```bash
./gradlew clean test
./gradlew check
./gradlew eclipse
git diff --check
git status --short --branch
```

Run `./gradlew eclipse` a second time and confirm it creates no additional tracked changes.

When the local CLIs are available, run one harmless smoke prompt through each provider-specific implementation. Do not expose credentials or transcript contents in the report.

## Final report

Report:

- Exact architecture implemented.
- Enum constants and stable external IDs added.
- Provider implementations added or changed.
- How `StandardCliBackend` was removed or narrowed.
- Capability behavior.
- Session-identity behavior.
- Compatibility aliases retained or removed.
- Tests added or changed.
- Automated validation results.
- Live smoke-test results, if run.
- Files changed.
- Current Git status.
- Confirmation that no commit or push was made.
- Any remaining limitation.
