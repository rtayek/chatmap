package chatmap.a2a.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.DecisionRequest;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSession;

final class A2aTaskRecorder {
    private final WorkerLifecycleService lifecycle;
    private final Path artifactDirectory;

    A2aTaskRecorder(WorkerLifecycleService lifecycle, Path artifactDirectory) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.artifactDirectory = Objects.requireNonNull(artifactDirectory, "artifactDirectory");
    }

    Recording begin(WorkerAssignmentInput assignmentInput, String agentIdentity) throws SQLException {
        WorkerAssignment assignment = lifecycle.createAssignment(assignmentInput);
        WorkerSession session = lifecycle.createSession(assignment.id(), agentIdentity);
        return new Recording(assignment.id(), session.id());
    }

    WorkerLifecycleRecord record(long sessionId, String rawTaskJson) throws SQLException, IOException {
        ObservedTask task = ObservedTask.parse(rawTaskJson);
        applyState(sessionId, task);
        preserveSnapshot(sessionId, task, rawTaskJson);
        preserveInlineArtifacts(sessionId, task);
        return lifecycle.record(sessionId);
    }

    private void applyState(long sessionId, ObservedTask task) throws SQLException {
        WorkerLifecycleState current = lifecycle.record(sessionId).session().lifecycleState();
        switch (task.state()) {
            case SUBMITTED -> requireCurrent(current, WorkerLifecycleState.QUEUED, task.state());
            case WORKING -> moveToWorking(sessionId, current);
            case INPUT_REQUIRED -> moveToInputRequired(sessionId, current, task);
            case COMPLETED -> moveToTerminal(sessionId, current, WorkerLifecycleState.COMPLETED);
            case FAILED, REJECTED -> moveToTerminal(sessionId, current, WorkerLifecycleState.FAILED);
            case CANCELLED -> moveToCancelled(sessionId, current);
        }
    }

    private void moveToInputRequired(long sessionId, WorkerLifecycleState current, ObservedTask task)
            throws SQLException {
        if (current == WorkerLifecycleState.WAITING_FOR_DECISION) {
            return;
        }
        moveToWorking(sessionId, current);
        lifecycle.transition(sessionId, WorkerLifecycleState.WAITING_FOR_DECISION, new DecisionRequest(
                task.statusMessage(),
                "Remote A2A task requires additional input",
                "The complete A2A task snapshot is preserved as an artifact"));
    }

    private void moveToTerminal(long sessionId, WorkerLifecycleState current, WorkerLifecycleState terminal)
            throws SQLException {
        if (current == terminal) {
            return;
        }
        moveToWorking(sessionId, current);
        lifecycle.transition(sessionId, terminal);
    }

    private void moveToCancelled(long sessionId, WorkerLifecycleState current) throws SQLException {
        if (current == WorkerLifecycleState.CANCELLED) {
            return;
        }
        if (current != WorkerLifecycleState.QUEUED
                && current != WorkerLifecycleState.WORKING
                && current != WorkerLifecycleState.WAITING_FOR_DECISION) {
            throw unexpectedState(current, ObservedState.CANCELLED);
        }
        lifecycle.transition(sessionId, WorkerLifecycleState.CANCELLED);
    }

    private void moveToWorking(long sessionId, WorkerLifecycleState current) throws SQLException {
        if (current == WorkerLifecycleState.WORKING) {
            return;
        }
        if (current != WorkerLifecycleState.QUEUED
                && current != WorkerLifecycleState.WAITING_FOR_DECISION) {
            throw unexpectedState(current, ObservedState.WORKING);
        }
        lifecycle.transition(sessionId, WorkerLifecycleState.WORKING);
    }

    private void preserveSnapshot(long sessionId, ObservedTask task, String rawTaskJson)
            throws SQLException, IOException {
        int sequence = nextArtifactSequence(sessionId);
        Path snapshot = artifactDirectory.resolve(String.format(
                "a2a-session-%d-event-%03d.json", sessionId, sequence));
        writeNewFile(snapshot, rawTaskJson);
        lifecycle.addArtifact(sessionId, "A2A task snapshot", snapshot.toUri().toString(),
                task.description());
    }

    private void preserveInlineArtifacts(long sessionId, ObservedTask task) throws SQLException, IOException {
        for (InlineArtifact artifact : task.artifacts()) {
            int sequence = nextArtifactSequence(sessionId);
            Path output = artifactDirectory.resolve(String.format(
                    "a2a-session-%d-artifact-%03d.txt", sessionId, sequence));
            writeNewFile(output, artifact.text());
            lifecycle.addArtifact(sessionId, artifact.label(), output.toUri().toString(),
                    "A2A artifact " + artifact.id() + " from task " + task.taskId());
        }
    }

    private int nextArtifactSequence(long sessionId) throws SQLException {
        return lifecycle.record(sessionId).artifacts().size() + 1;
    }

    private void writeNewFile(Path output, String content) throws IOException {
        Files.createDirectories(artifactDirectory);
        Files.writeString(output, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void requireCurrent(WorkerLifecycleState current, WorkerLifecycleState expected,
            ObservedState observed) {
        if (current != expected) {
            throw unexpectedState(current, observed);
        }
    }

    private static IllegalStateException unexpectedState(WorkerLifecycleState current, ObservedState observed) {
        return new IllegalStateException("Cannot record A2A state " + observed
                + " from ChatMap state " + current);
    }

    record Recording(long assignmentId, long sessionId) {
    }

    private record ObservedTask(
            String taskId,
            String contextId,
            ObservedState state,
            String statusMessage,
            List<InlineArtifact> artifacts) {

        private static ObservedTask parse(String rawTaskJson) {
            JsonObject task = requiredObject(JsonParser.parseString(
                    Objects.requireNonNull(rawTaskJson, "rawTaskJson")), "task");
            JsonObject status = requiredObject(task.get("status"), "status");
            ObservedState state = ObservedState.fromProtocolValue(requiredString(status, "state"));
            String message = statusMessage(status);
            if (state == ObservedState.INPUT_REQUIRED && message.isBlank()) {
                message = "Remote A2A task requires additional input";
            }
            return new ObservedTask(
                    requiredString(task, "id"),
                    requiredString(task, "contextId"),
                    state,
                    message,
                    readArtifacts(task));
        }

        private String description() {
            return "taskId=" + taskId + " contextId=" + contextId + " state=" + state;
        }

        private static String statusMessage(JsonObject status) {
            JsonElement message = status.get("message");
            if (message == null || !message.isJsonObject()) {
                return "";
            }
            return textParts(message.getAsJsonObject().get("parts"));
        }

        private static List<InlineArtifact> readArtifacts(JsonObject task) {
            JsonElement element = task.get("artifacts");
            if (element == null || !element.isJsonArray()) {
                return List.of();
            }
            List<InlineArtifact> artifacts = new ArrayList<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject artifact = item.getAsJsonObject();
                String text = textParts(artifact.get("parts"));
                if (!text.isBlank()) {
                    String id = optionalString(artifact, "artifactId", "unnamed");
                    String label = optionalString(artifact, "name", id);
                    artifacts.add(new InlineArtifact(id, label, text));
                }
            }
            return List.copyOf(artifacts);
        }

        private static String textParts(JsonElement element) {
            if (element == null || !element.isJsonArray()) {
                return "";
            }
            List<String> texts = new ArrayList<>();
            for (JsonElement part : element.getAsJsonArray()) {
                if (part.isJsonObject()) {
                    JsonElement text = part.getAsJsonObject().get("text");
                    if (text != null && text.isJsonPrimitive()) {
                        texts.add(text.getAsString());
                    }
                }
            }
            return String.join(System.lineSeparator(), texts);
        }

        private static JsonObject requiredObject(JsonElement element, String label) {
            if (element == null || !element.isJsonObject()) {
                throw new IllegalArgumentException("A2A " + label + " object is required");
            }
            return element.getAsJsonObject();
        }

        private static String requiredString(JsonObject object, String name) {
            String value = optionalString(object, name, "");
            if (value.isBlank()) {
                throw new IllegalArgumentException("A2A " + name + " is required");
            }
            return value;
        }

        private static String optionalString(JsonObject object, String name, String fallback) {
            JsonElement value = object.get(name);
            return value == null || !value.isJsonPrimitive() ? fallback : value.getAsString();
        }
    }

    private record InlineArtifact(String id, String label, String text) {
    }

    private enum ObservedState {
        SUBMITTED,
        WORKING,
        INPUT_REQUIRED,
        COMPLETED,
        FAILED,
        CANCELLED,
        REJECTED;

        private static ObservedState fromProtocolValue(String value) {
            return switch (value) {
                case "TASK_STATE_SUBMITTED" -> SUBMITTED;
                case "TASK_STATE_WORKING" -> WORKING;
                case "TASK_STATE_INPUT_REQUIRED" -> INPUT_REQUIRED;
                case "TASK_STATE_COMPLETED" -> COMPLETED;
                case "TASK_STATE_FAILED" -> FAILED;
                case "TASK_STATE_CANCELED" -> CANCELLED;
                case "TASK_STATE_REJECTED" -> REJECTED;
                default -> throw new IllegalArgumentException("Unsupported A2A task state: " + value);
            };
        }
    }
}
