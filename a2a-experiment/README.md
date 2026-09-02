# ChatMap A2A Experiment

This standalone project exercises A2A Protocol 1.0 with the official Java SDK
1.3.0.Final and the Quarkus JSON-RPC reference server.

It is experimental code on branch `experiment/a2a`. It does not use ChatMap
production code or the ChatMap database.

## Layout

- `src/chatmap/a2a/experiment/AgentCardProducer.java`: public Agent Card
- `src/chatmap/a2a/experiment/AgentExecutorProducer.java`: A2A server adapter
- `src/chatmap/a2a/experiment/FakeWorker.java`: deterministic worker
- `src/chatmap/a2a/experiment/ExperimentClient.java`: official Java client
- `tst/chatmap/a2a/experiment/FakeWorkerTest.java`: worker tests
- `a2a-findings.md`: observed behavior and ChatMap mapping

## Run From the Outer Worktree

Use:

```text
C:/Users/ray/eclipse-workspace/chatmap-a2a-experiment
```

Test:

```sh
./gradlew -p a2a-experiment test
```

Start the server in one terminal:

```sh
./gradlew -p a2a-experiment quarkusDev
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
./gradlew -p a2a-experiment a2aRequest -Prequest=complete:hello
```

Request more input:

```sh
./gradlew -p a2a-experiment a2aRequest -Prequest=input-required
```

Request failure:

```sh
./gradlew -p a2a-experiment a2aRequest -Prequest=fail
```

Stop the server with `Ctrl+C`.

## Expected States

- `complete:hello`: `TASK_STATE_COMPLETED` with artifact text `hello`
- `input-required`: `TASK_STATE_INPUT_REQUIRED` with a request message
- `fail`: `TASK_STATE_FAILED` with an explicit reason

## Known Dependency Messages

Quarkus reports that Quarkus 4 will require a newer Gradle version. This
experiment uses Quarkus 3.39.1, so no wrapper upgrade is required.

The client may report JBoss LogManager and protobuf Unsafe warnings. They come
from the released dependencies and did not prevent the tested exchanges.
