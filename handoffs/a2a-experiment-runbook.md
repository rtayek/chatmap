# ChatMap A2A Experiment Runbook

This package exercises A2A Protocol 1.0 with the official Java SDK 1.3.0.Final
and the Quarkus JSON-RPC reference server.

It is experimental code in the regular ChatMap package
`chatmap.a2a.experiment` on `master`. The continuation demonstration creates an isolated temporary ChatMap database; it does not touch the normal ChatMap home.

## Layout

- `src/chatmap/a2a/experiment/AgentCardProducer.java`: public Agent Card
- `src/chatmap/a2a/experiment/AgentExecutorProducer.java`: A2A server adapter
- `src/chatmap/a2a/experiment/FakeWorker.java`: deterministic worker
- `src/chatmap/a2a/experiment/ModelWorker.java`: bounded model-backed worker
- `src/chatmap/a2a/experiment/ExperimentClient.java`: one-request Java client
- `src/chatmap/a2a/experiment/ContinuationClient.java`: same-task continuation client and ledger demonstration
- `src/chatmap/a2a/experiment/A2aTaskRecorder.java`: A2A-to-worker-lifecycle adapter
- `tst/chatmap/a2a/experiment/FakeWorkerTest.java`: worker tests
- `tst/chatmap/a2a/experiment/ModelWorkerTest.java`: model-worker adapter tests
- `tst/chatmap/a2a/experiment/A2aTaskRecorderTest.java`: recorder projection tests
- `src/chatmap/presentation/cli/WorkerLifecycleRecordCli.java`: read-only persisted-record display
- `handoffs/a2a-experiment-findings.md`: observed behavior and ChatMap mapping

## Run From ChatMap

Use:

```text
C:/Users/ray/eclipse-workspace/chatmap
```

Test:

```sh
./gradlew test
```

Start the deterministic server in one terminal:

```sh
./gradlew quarkusDev
```

Wait for:

```text
Listening on: http://localhost:9999
```

Retrieve the Agent Card from another terminal:

```sh
curl --fail --show-error http://localhost:9999/.well-known/agent-card.json
```

Submit a successful request:

```sh
./gradlew a2aRequest -Prequest=complete:hello
```

Request more input:

```sh
./gradlew a2aRequest -Prequest=input-required
```

Request failure:

```sh
./gradlew a2aRequest -Prequest=fail
```

Run the same-task continuation experiment:

```sh
./gradlew a2aContinue
```

This sends `input-required`, captures the returned task and context IDs, then
sends `complete:continued hello` with those same IDs. It validates the completed
state and unchanged identity before printing `CONTINUATION PROVEN`. Every returned task snapshot is also projected into one ChatMap worker session. The command prints the temporary database and artifact paths plus the final ChatMap state and counts. The verified deterministic run reports `state=COMPLETED`, `events=4`, and `artifacts=3`.

Inspect the resulting record after the continuation process exits:

```sh
./gradlew workerLifecycleRecord -Phome='<printed-home>' -Psession=1
```

The command reopens the printed temporary database and displays the assignment,
session, lifecycle transitions, decision details, artifact locations, handoff
presence, and successor count.

## Run the Ollama-backed Worker

Confirm that Ollama is running and that the selected model is installed:

```sh
ollama list
```

Start the A2A server with the model worker:

```sh
CHATMAP_A2A_WORKER=ollama \
CHATMAP_A2A_OLLAMA_TARGET=ollama-glm4 \
./gradlew quarkusDev
```

The target may be changed to another curated Ollama target listed by ChatMap.
In a second terminal, retrieve the Agent Card and submit one prompt:

```sh
curl --fail --show-error http://localhost:9999/.well-known/agent-card.json
./gradlew a2aRequest -Prequest='Explain durable task history in two sentences.'
```

A successful response is `TASK_STATE_COMPLETED` with a `worker-result` text
artifact containing the local model response. An unavailable Ollama server or
model becomes `TASK_STATE_FAILED` with an explicit provider message.

Stop the server with `Ctrl+C`.

## Expected Deterministic States

- `complete:hello`: `TASK_STATE_COMPLETED` with artifact text `hello`
- `input-required`: `TASK_STATE_INPUT_REQUIRED` with a request message
- `fail`: `TASK_STATE_FAILED` with an explicit reason
- `a2aContinue`: the same task moves from `TASK_STATE_INPUT_REQUIRED` to
  `TASK_STATE_COMPLETED`, with the continuation preserved in task history

## Known Dependency Messages

Quarkus reports that Quarkus 4 will require a newer Gradle version. This
experiment uses Quarkus 3.39.1, so no wrapper upgrade is required.

The client may report JBoss LogManager and protobuf Unsafe warnings. They come
from the released dependencies and did not prevent the tested exchanges.
