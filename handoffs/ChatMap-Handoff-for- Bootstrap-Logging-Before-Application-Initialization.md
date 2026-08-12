# ChatMap Handoff: Bootstrap Logging Before Application Initialization

Work in the current `rtayek/chatmap` repository. Preserve unrelated changes. Do not commit or push.

## Goal

Ensure that:

* Normal logs live in `<resolved ChatMap home>/logs`.
* Errors occurring before the ChatMap home can be resolved go to a safe temporary directory.
* Logging never creates `~/.chatmap` and accidentally causes ChatMap to select a second database.
* SLF4J/Logback is not initialized before the logging directory property is established.

## Required design

Add a lightweight logging/bootstrap class with no static logger or static initialization that reaches SLF4J.

Use this system property:

```text
chatmap.log.dir
```

Startup order must be:

```text
plain main class
→ establish temporary fallback property
→ parse --home / CHATMAP_HOME / project-local home
→ replace property with <resolved-home>/logs
→ initialize logging
→ open database and services
```

Suggested fallback:

```text
${java.io.tmpdir}/chatmap-bootstrap/logs
```

## Entry points

Apply the bootstrap consistently to:

* `ChatMapLauncher.main`
* `ChatMapApp.main`
* All CLI entry points through `CliBootstrap`

Entry-point classes must not contain static loggers.

Remove the static logger from `ChatMapRuntime`. Create its logger only after paths and logging have been configured, using an instance or local logger.

Avoid duplicating path-selection rules. Continue using `ChatMapPaths` as the authority.

## Logback configuration

Update `logback.xml` to use:

```xml
${chatmap.log.dir}
```

with the temporary directory as its default when the property is absent.

Do not use `${user.home}/.chatmap/logs`.

## Legacy-home safety

Change legacy-home discovery so this:

```text
~/.chatmap/
```

is not sufficient to select the legacy home.

Require the legacy database file itself:

```text
~/.chatmap/chatmap.db
```

A logs-only directory must never influence database selection.

## Early failures

If argument or home resolution fails:

* Preserve the existing terminal error behavior.
* Initialize logging with the temporary fallback and record the failure there.
* Do not create a database or legacy ChatMap directory.

Do not add complex support for unusual `-javaagent` or custom-launcher behavior. Normal Gradle, Eclipse, JavaFX, and CLI launches are the required scope.

## Tests

Add focused tests proving:

1. Explicit `--home` produces `<home>/logs`.
2. The selected project-local or configured home produces its own `logs` directory.
3. An early parsing failure retains the temporary fallback.
4. An existing `~/.chatmap` directory without `chatmap.db` is ignored.
5. An existing legacy `~/.chatmap/chatmap.db` remains selectable.
6. Bootstrap/path resolution does not initialize or depend on an application static logger.
7. Existing CLI and runtime path tests still pass.

Restore changed system properties after every test so tests do not contaminate one another.

## Validation

Run:

```bash
git status --short --branch
git diff --check
./gradlew test
./gradlew eclipse
./gradlew eclipse
git diff --check
```

Confirm that two consecutive Eclipse-generation runs are idempotent and that all required Logback/SLF4J jars remain present in `lib/` and `.classpath`.

Report:

* Files changed
* Final logging directory behavior
* Early-failure behavior
* Test counts and failures
* Any remaining limitation

Also correct the existing `PromptProfile.jShellHarnesss` typo to `jShellHarness` if it is still present, with all references updated.
