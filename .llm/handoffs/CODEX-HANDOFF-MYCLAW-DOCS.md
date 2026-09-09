# Codex Handoff: MyClaw Root Documentation Revision

## Goal

Revise the five authoritative Markdown files in the repository root so they accurately describe:

1. the current implementation;
2. the target architecture;
3. the project vision without making present-user operation or bounded loops absolute requirements.

The five authoritative files are:

- `README.md`
- `VISION.md`
- `ARCHITECTURE.md`
- `HOWTO.md`
- `ROADMAP.md`

Do not create additional root-level design documents.

Superseded or exploratory material belongs in `old-mds/` or `old/`.

## Important architectural correction

The current documentation overstates the degree to which the socket-separated architecture is already integrated.

### Current implementation

The desktop currently constructs `PromptService` and the available AI backends directly in the same JVM.

```text
Java Desktop
    ↓
PromptService
    ↓
in-process AiBackend implementations
```

Relevant code:

- `src/myclaw/desktop/DesktopMain.java`
- `src/myclaw/application/PromptService.java`
- `src/myclaw/application/ApplicationBackends.java`
- `src/myclaw/backend/*`

### Target architecture

```text
Java Desktop Frontend
        ↓
       Core
        ↓ local socket protocol
     Backend Process
        ↓
models, tools, scheduled work, and agent loops
```

The documentation must clearly label this as the target architecture unless the code path is already integrated and used by the desktop.

Do not describe a proposed subsystem as fully implemented merely because related classes or tests exist.

## Strategic language correction

### Present-user operation

MyClaw should prioritize accessible, inspectable interaction for a user who is present, but it must not require the user to remain present for every task.

Preferred wording:

> MyClaw prioritizes accessible, inspectable interaction. It may also perform supervised or unattended work under explicit user-selected policies.

Avoid wording such as:

- “the user must remain present”;
- “a task is rejected if the user stops watching”;
- “MyClaw is never allowed to work unattended.”

### Agent loops

Agent loops should be controlled by explicit policy.

Limits such as step count, elapsed time, cost, tools, directories, approvals, and stop conditions should be defaults and configurable safeguards, not permanent architectural prohibitions.

Preferred wording:

> Agent loops are policy-governed. Conservative limits are the default, while longer or unattended execution may be explicitly enabled.

## File-by-file changes

### `README.md`

Keep it brief.

It should answer:

- What is MyClaw?
- What exists now?
- What is the intended direction?
- Where are the authoritative documents?

Required changes:

- Keep the accessible, local-first Java desktop description.
- Show current and target architecture separately.
- Remove language that says the user must be present at all times.
- Keep links to the other four root documents.
- Avoid detailed implementation claims.

### `VISION.md`

Keep this about purpose and principles.

Required themes:

- accessibility is foundational;
- desktop-first Java workbench;
- conversations, context, and artifacts belong to the user;
- local-first with local and cloud backends;
- sessions outlive models and providers;
- optional skills, memory, scheduling, and agent loops;
- supervised or unattended work may be enabled by explicit policy;
- one-person maintainability matters.

Required changes:

- Replace “built for someone present the whole time” with flexible wording.
- Replace the feature test that automatically rejects non-present-user work.
- Replace categorical rejection of agent loops with policy-governed execution.
- Move detailed feature lists to `ROADMAP.md` or `ARCHITECTURE.md`.
- Keep the vision concise.

Suggested core statement:

> MyClaw is an accessible, local-first Java desktop cockpit for working with interchangeable AI systems while preserving conversations, context, and artifacts in durable, user-owned records.

### `ARCHITECTURE.md`

This file needs the largest revision.

Use these major sections:

1. Current implementation
2. Target process architecture
3. Socket protocol
4. Sessions and persistence
5. Skills, memory, scheduling, and agent loops

Current implementation:

```text
DesktopMain
    ↓
PromptService
    ↓
AiBackend implementations
```

Target process architecture:

```text
Java Desktop Frontend
        ↓
       Core
        ↓ sockets
     Backend Process
```

Responsibilities:

- Frontend: accessible presentation and input.
- Core: application state, sessions, context assembly, orchestration policy.
- Socket transport: provider-neutral process boundary.
- Backend: model/tool execution, scheduled jobs, agent loops.
- Persistence: source records, transcripts, replay, derived artifacts.

Keep useful socket protocol details, but distinguish implemented, experimental, and proposed operations.

Describe the first agent loop as:

```text
Claude Code or Codex edits files
    ↓
Gradle runs tests
    ↓
agent inspects failures
    ↓
agent revises
    ↓
user reviews the diff
```

Execution policy should support:

- maximum steps;
- elapsed time;
- cost/token budget;
- allowed tools;
- allowed directories;
- approval requirements;
- stop conditions;
- unattended execution setting.

### `HOWTO.md`

Keep it practical.

Tasks:

- Verify every documented Gradle task exists.
- Verify command examples match current backend IDs.
- Verify `runDesktop`.
- Verify socket examples match actual startup and port configuration.
- Keep the paste-safe Git workflow.
- Keep the five-file documentation convention.

Do not add vision or speculative architecture.

### `ROADMAP.md`

Distinguish:

- implemented and integrated;
- implemented experimentally;
- designed;
- planned.

Do not mark the full socket-separated runtime complete unless the desktop actually uses it.

Suggested phases:

#### Phase 1: Daily-use desktop

- accessible Swing UI;
- Claude and Ollama backends;
- reliable transcripts;
- basic session continuity;
- readable errors;
- text-to-speech milestone.

#### Phase 2: Core/backend separation

- make the socket backend the normal desktop path;
- provider-neutral API;
- health, cancellation, and error propagation;
- remove duplicate transcript paths.

#### Phase 3: Memory, skills, and library

- curated memory;
- local `SKILL.md`;
- search;
- context handoffs;
- scheduled consolidation.

#### Phase 4: Policy-governed agent work

- tool adapters;
- coding loop;
- approval controls;
- scheduling;
- optional unattended execution.

Keep only genuine scope exclusions:

- public skill marketplace;
- hosted multi-tenant service;
- prompt-to-hosted-app product;
- cloud sandbox fleet maintained by this project.

## Consistency requirements

Across all five files:

1. Use **MyClaw** consistently.
2. Distinguish current, target, and future.
3. Do not call proposed architecture implemented.
4. Do not require a present user for every task.
5. Do not make bounded loops an eternal prohibition.
6. Keep accessibility, transparency, and user ownership central.
7. Keep the desktop Java frontend as the initial product.
8. Keep the core/backend socket boundary as the intended architecture.
9. Do not create new root Markdown files.
10. Remove duplicate explanations.

## Validation

```sh
git diff -- README.md VISION.md ARCHITECTURE.md HOWTO.md ROADMAP.md
./gradlew test
./gradlew integrationTest
```

Also inspect:

```sh
git grep -n "present the whole time"
git grep -n "unbounded autonomous"
git grep -n "3-tier"
git grep -n "socket"
```

## Acceptance criteria

The task is complete when:

- the five root Markdown files remain the only authoritative project documents;
- each file has a distinct purpose;
- the current in-process desktop path is described honestly;
- the target socket-separated architecture is clear;
- agent work is policy-governed rather than categorically forbidden;
- supervised and unattended execution are both possible under explicit policy;
- accessibility, local ownership, and replaceable backends remain central;
- no new root-level design document is added.
