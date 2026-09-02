# A2A Experiment Findings

**Observed:** 2026-09-02
**Protocol:** A2A 1.0 over JSON-RPC
**Java SDK:** 1.3.0.Final
**Server:** Quarkus reference JSON-RPC server 3.39.1
**Client:** Official A2A Java client

## Result

Continue studying A2A, but do not integrate this experimental server into
ChatMap yet.

The protocol successfully provides discovery, task identity, context identity,
status, messages, and artifacts across an opaque agent boundary. It does not
provide ChatMap's durable ledger, semantic-preservation guarantees, worker
sessions, or predecessor and successor assignment model.

## Demonstrated Behavior

| Request | Observed state | Artifact or message |
|---|---|---|
| `complete:hello` | `TASK_STATE_COMPLETED` | Artifact `fake-worker-result` containing `hello` |
| `input-required` | `TASK_STATE_INPUT_REQUIRED` | Agent message requesting additional text |
| `fail` | `TASK_STATE_FAILED` | Agent message explaining the requested failure |

The Agent Card was retrieved from
`/.well-known/agent-card.json`. It advertised one text skill and one JSON-RPC
interface using protocol version 1.0.

The official CLI had no published release during this experiment, so the
released official Java client was used.

## Mapping to ChatMap

| ChatMap | Observed A2A fit | Important difference |
|---|---|---|
| Worker identity | Agent Card | An Agent Card describes a service and its skills, not a particular worker session. |
| Assignment | Message plus Task | A2A creates a task from a message; ChatMap records the assignment separately from execution. |
| Work session | Task plus context ID | A2A context groups interaction, but does not model ChatMap's session and retirement semantics. |
| Lifecycle event | Task status | States map well, including failed and input-required. A blocking client receives the resulting task rather than every intermediate transition. |
| Waiting for decision | `TASK_STATE_INPUT_REQUIRED` | The mapping is direct, but continuation of the same task was not exercised. |
| Worker artifact | Artifact | The structural mapping is direct. A2A does not make the artifact durable by itself. |
| Semantic handoff | Message or artifact content | A2A transports content but does not guarantee that important meaning was preserved. |
| Successor assignment | No direct equivalent observed | Context IDs, task references, or metadata may relate work, but they are not ChatMap successor chains. |
| Durable ledger | No protocol guarantee | Durability depends on the server implementation. ChatMap can record the externally visible A2A exchange. |

## What ChatMap Can Record Without Agent Internals

ChatMap can record:

- the Agent Card used for discovery;
- the submitted message;
- task ID and context ID;
- returned task state;
- agent status messages;
- artifact identity, name, content, and location;
- timestamps and transport metadata.

ChatMap does not need, and A2A does not expose:

- hidden prompts;
- private reasoning;
- internal memory;
- internal tools;
- the remote implementation plan.

## Limits of This Slice

This experiment did not test:

- continuation of an input-required task;
- streaming status updates;
- task persistence across server restart;
- authentication or authorization;
- push notifications;
- REST or gRPC;
- concurrency, retries, or scheduling;
- semantic preservation.

The worker tests prove deterministic branching. They do not prove A2A transport
conformance or semantic preservation.

## Recommendation

Keep the experiment on its branch and do not merge it wholesale into ChatMap.

If another bounded experiment is approved, test continuation after
`TASK_STATE_INPUT_REQUIRED` using the same task and context. Then evaluate a
small recorder that converts externally visible A2A messages, statuses, and
artifacts into ChatMap ledger entries without making ChatMap the orchestrator.
