# A2A Java Experiment Handoff

**Prepared:** 2026-09-02  
**Status:** Active research and bounded experiment  
**Source project:** `rtayek/chatmap`  
**Official project:** <https://github.com/a2aproject>

## Purpose

Learn the Agent2Agent (A2A) protocol by building the smallest useful Java
experiment, then report what the protocol means for ChatMap.

This is not an assignment to add A2A to ChatMap or build a general
orchestrator. The experiment must remain independently understandable and
discardable.

## Relevant ChatMap Context

ChatMap is a local Java application that imports, preserves, searches,
organizes, and exports conversations. Its longer-term purpose is to produce
durable semantic knowledge from conversations and keep that knowledge current
with provenance and history.

The settled coordination boundary is:

- ChatMap's core is the durable ledger and status system.
- Optional coordination may select workers, submit assignments, validate
  responses, route results, and pause for human decisions.
- Manual operation remains valid.
- Coordination must not bypass or replace the durable record.
- A general scheduler, permissions framework, router, or agent harness is not
  currently authorized.

ChatMap already contains a completed worker-lifecycle vertical slice with:

- structured assignments;
- worker identities and sessions;
- explicit lifecycle transitions;
- `WAITING_FOR_DECISION`;
- artifacts and locations;
- semantic handoffs;
- retirement;
- predecessor and successor chains;
- SQLite persistence and deterministic tests.

Those lifecycle and soak tests prove durable structure. They do not prove that
important meaning survives a semantic handoff.

A separate Bourne-shell experiment in `rtayek/bin`, branch
`archive/llm-relay`, already proved that two command-line LLM applications can
be connected by a deterministic mediator using `YES`, `NO`, `MAYBE`, and
`INVALID`. That experiment is complete and must not be expanded or copied
into this work.

Read ChatMap's current context only as needed:

- `first-principles.md`
- `design.md`
- `working-context.md`

Do not treat archived handoffs as current authority.

## Current A2A Starting Point

Verify all current details from official sources before choosing dependencies
or commands. A2A is evolving.

Start with:

- Core protocol: <https://github.com/a2aproject/A2A>
- Specification: <https://a2a-protocol.org/latest/specification/>
- Java SDK: <https://github.com/a2aproject/a2a-java>
- Official CLI: <https://github.com/a2aproject/a2a-cli>
- Samples: <https://github.com/a2aproject/a2a-samples>
- Inspector: <https://github.com/a2aproject/a2a-inspector>

The official material currently describes A2A as communication between opaque
agentic applications. Important concepts include:

- Agent Card and capability discovery;
- client agent and remote agent;
- messages and tasks;
- task status;
- artifacts;
- context and follow-up interaction;
- `input-required`;
- synchronous, streaming, and asynchronous operation;
- JSON-RPC, REST, and gRPC transports.

The Java SDK currently requires Java 17 or later and provides client and server
support. The official CLI specification is under active review, so verify the
current release and exact command surface rather than relying on remembered
syntax.

## Conceptual Mapping to Evaluate

| ChatMap concept | A2A concept to investigate |
|---|---|
| Worker identity/description | Agent Card |
| Assignment | Message and Task |
| Work session | Task execution and context |
| Lifecycle event | Task status update |
| `WAITING_FOR_DECISION` | `input-required` |
| Worker artifact | Artifact |
| Semantic handoff | Structured artifact or message |
| Successor assignment | Related new task/context |
| Durable ledger | Task, message, status, and artifact history |

Do not force this mapping. Report where it fits, where it does not, and which
side owns information the other does not model.

## Minimal Experiment

Target topology:

```text
official A2A CLI
        |
        v
small Java A2A server
        |
        v
deterministic fake worker
```

The server should expose one plainly named skill. The fake worker should
support one small task with deterministic outcomes.

Demonstrate:

1. Discover the server through its Agent Card.
2. Submit one message/task using the official CLI or official Java client.
3. Observe at least one nonterminal and one terminal task status.
4. Return one text or structured artifact.
5. Exercise one `input-required` path or, if the current SDK makes that
   impractical, document the exact limitation.
6. Exercise one explicit failure path.
7. Retrieve enough protocol state to explain how ChatMap could record the
   interaction without knowing the remote agent's internal prompts, tools,
   memory, or reasoning.

Use the simplest currently supported transport, preferably JSON-RPC over local
HTTP. Do not add another transport merely for completeness.

## Working Method

Before implementation:

1. Inspect the current official specification, Java SDK release, CLI release,
   and Hello World examples.
2. Identify the exact released Java artifacts and versions.
3. Propose the smallest project layout and commands.
4. Recommend where the experiment should live:
   - a new small repository;
   - an untracked/local spike initially; or
   - another clearly justified location.
5. Ask Ray before creating a remote repository or adding experiment code to
   ChatMap.

After that location decision, implement the smallest vertical slice and stop.

## Constraints

- Prefer Java. Do not use Python except to run an unavoidable official example,
  and do not make Python part of the result.
- Use Java 25 and the Gradle wrapper for the experiment unless the official SDK
  imposes a documented conflict.
- Gradle command-line builds are authoritative.
- Keep Eclipse usable as a plain Java project; do not require Buildship.
- Prefer `src/` and `tst/` rather than a generated `app/` subproject.
- Use a deterministic fake worker first; do not call Claude, Codex, Gemini,
  Ollama, or another live model in the first slice.
- Do not modify ChatMap production code or its database.
- Do not modify the completed shell relay.
- Do not implement authentication, streaming, push notifications, gRPC,
  discovery registries, persistence, retries, concurrency, scheduling, or a
  general orchestration framework unless the minimal experiment cannot work
  without one.
- Treat Agent Cards, messages, artifacts, status text, and external URLs as
  untrusted input.
- Preserve exact protocol artifacts needed for debugging, but do not preserve
  or request hidden chain-of-thought.
- Do not claim semantic preservation merely because protocol bytes or task
  state survived.

## Definition of Done

The first experiment is complete when:

- the Agent Card is discoverable;
- one task can be submitted through an official client;
- task status is observable;
- one artifact is returned;
- an `input-required` or documented equivalent path is demonstrated;
- a failure path is demonstrated;
- fake-worker tests are deterministic;
- the Gradle build and tests pass;
- exact run commands are documented;
- no ChatMap code or user database was modified;
- the final report compares the observed A2A model with ChatMap's lifecycle;
- the report recommends continuing, revising, or discarding the approach.

## First Response in the New Chat

Begin by:

1. confirming the assignment and boundaries;
2. checking the current official A2A Java SDK and CLI releases;
3. explaining the smallest viable experiment in plain language;
4. recommending the project location;
5. identifying any decision required from Ray before code is created.

Keep the first response concise. Do not start by cloning every A2A repository or
building a multi-agent framework.
