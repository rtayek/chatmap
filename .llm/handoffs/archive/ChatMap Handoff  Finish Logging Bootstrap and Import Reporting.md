# ChatMap Handoff: Finish Logging Bootstrap and Import Reporting

Work in the current `rtayek/chatmap` repository. Complete this corrective pass autonomously. Preserve unrelated work. Do not commit or push.

## Current state

Commit `2069d4f` implements most of the logging bootstrap correctly:

* Early failures log under the temporary directory.
* Normal logs use `<resolved ChatMap home>/logs`.
* A logs-only `~/.chatmap` no longer selects the legacy database.
* `ChatMapRuntime` no longer has a static logger.
* The `jShellHarnesss` typo is fixed.

However, GitHub Actions run `31567934865` is red:

```text
83 tests completed, 3 failed, 1 skipped
```

The three reported failures are in `ChatMapRuntimeTest`, and a later CLI test apparently terminates the Gradle test worker through `System.exit(1)`.

## 1. Propagate resolved paths through ServiceGraph

This is the highest-priority fix.

`ChatMapRuntime` and `CliBootstrap` resolve the correct `ChatMapPaths.ResolvedPaths`, but `ServiceGraph.create(...)` discards them.

It constructs `PromptService` through the short constructor, which calls:

```java
ChatMapPaths.transcriptsDirectory()
```

That performs a second global path resolution without the original `--home`. It fails on a clean CI machine.

Change production wiring so the selected paths flow explicitly:

```text
ChatMapRuntime or CliBootstrap
    → ServiceGraph.create(connection, integrations, resolvedPaths)
    → PromptService(..., resolvedPaths.transcriptsDirectory())
```

The exact parameter ordering may follow existing conventions.

Requirements:

* Production `ServiceGraph` wiring must not call global `ChatMapPaths` accessors after paths have been resolved.
* `--home` must govern the database, logs, transcripts, reports, and other runtime data consistently.
* Preserve convenient test construction without reintroducing hidden global resolution.
* Avoid adding path fields to unrelated domain or repository classes.

Search for remaining hidden path resolution:

```bash
rg 'ChatMapPaths\.(homeDirectory|databasePath|transcriptsDirectory)' src
```

Every remaining use should be intentional and occur before or inside the central bootstrap boundary.

## 2. Distinguish empty web chats from web-provider failures

Keep `NoImportableContentException` for candidates successfully read but containing no real turns, especially empty CLI session files.

Do not classify every empty CDP result as “no content.”

`CdpTranscriptAdapter.transcript()` currently catches browser, connection, selector, and parsing failures and returns `Optional.empty()` while storing `lastUnavailableReason`.

In `CdpWebChatProvider.fetch(...)`:

```text
empty transcript + lastUnavailableReason present
    → ChatProviderException

empty transcript + no unavailable reason
    → NoImportableContentException
```

Add focused tests for both cases:

* A genuinely empty conversation counts as no content.
* A CDP/selector/extraction failure remains a failed provider operation.

Do not print thousands of harmless empty-session lines, but do not hide genuine web-adapter failures.

## 3. Bootstrap before constructing default prompt backends

`RunPromptCli.main()` currently evaluates:

```java
DefaultAiBackends.defaults()
```

before `execute()` performs logging bootstrap.

Rearrange the entry point so bootstrap and argument parsing happen before constructing default backends or any other application services.

Avoid parsing the arguments through different rules twice. A small parsed-arguments overload is acceptable.

## Tests and acceptance criteria

At minimum, verify:

* All `ChatMapRuntimeTest` tests pass with explicit temporary homes.
* `ChatConsolidatorCliTest` cannot terminate the Gradle test worker because of hidden path resolution.
* Existing `LoggingBootstrapTest` tests pass.
* Explicit `--home` places database, logs, and transcripts below the same home.
* A logs-only legacy directory is ignored.
* CLI empty-session results remain separate from genuine failures.
* The complete test suite runs; do not accept another partial result such as 83 tests.
* No existing database-format strings change.

Run:

```bash
git status --short --branch
git diff --check

./gradlew test
./gradlew check

./gradlew eclipse
./gradlew eclipse

git diff --check
git status --short --branch
```

The two Eclipse runs must be idempotent, and all SLF4J/Logback jars must remain available through `lib/` and `.classpath`.

## Final report

Report:

* Root cause of the CI failure
* Files changed
* How resolved paths now reach `PromptService`
* How no-content and provider failures are distinguished
* Total tests executed and results
* Static-analysis results
* Eclipse idempotence
* Any remaining limitation

Stop without committing or pushing.
