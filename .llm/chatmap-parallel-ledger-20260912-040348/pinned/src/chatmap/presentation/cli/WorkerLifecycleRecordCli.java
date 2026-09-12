package chatmap.presentation.cli;

import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerLifecycleEvent;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.presentation.cli.CliBootstrap.CliContext;

public final class WorkerLifecycleRecordCli {
    private static final String USAGE =
            "Usage: workerLifecycleRecord [--home <directory>] <session-id>";

    private WorkerLifecycleRecordCli() {
    }

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, USAGE);
        try {
            System.out.print(format(execute(parsedArguments)));
        } catch (Exception exception) {
            System.err.println("Could not read worker lifecycle record: " + exception.getMessage());
            System.exit(1);
        }
    }

    static WorkerLifecycleRecord execute(ParsedArguments parsedArguments) throws Exception {
        long sessionId = sessionId(parsedArguments);
        try (CliContext context = CliBootstrap.open(parsedArguments)) {
            return context.services().workerLifecycleService().record(sessionId);
        }
    }

    static String format(WorkerLifecycleRecord record) {
        StringBuilder output = new StringBuilder();
        output.append("WORKER LIFECYCLE RECORD").append(System.lineSeparator());
        output.append("assignmentId=").append(record.assignment().id()).append(System.lineSeparator());
        output.append("task=").append(record.assignment().task()).append(System.lineSeparator());
        output.append("sessionId=").append(record.session().id()).append(System.lineSeparator());
        output.append("worker=").append(record.session().workerIdentity()).append(System.lineSeparator());
        output.append("state=").append(record.session().lifecycleState()).append(System.lineSeparator());

        output.append("EVENTS ").append(record.events().size()).append(System.lineSeparator());
        for (WorkerLifecycleEvent event : record.events()) {
            output.append(event.id()).append(' ')
                    .append(event.fromState()).append(" -> ").append(event.toState())
                    .append(" at ").append(event.createdAt()).append(System.lineSeparator());
            event.decisionQuestion().ifPresent(value ->
                    output.append("  question=").append(value).append(System.lineSeparator()));
            event.decisionReason().ifPresent(value ->
                    output.append("  reason=").append(value).append(System.lineSeparator()));
            event.preservedPartialWork().ifPresent(value ->
                    output.append("  partialWork=").append(value).append(System.lineSeparator()));
        }

        output.append("ARTIFACTS ").append(record.artifacts().size()).append(System.lineSeparator());
        for (WorkerArtifact artifact : record.artifacts()) {
            output.append(artifact.id()).append(' ').append(artifact.label()).append(System.lineSeparator());
            output.append("  location=").append(artifact.location()).append(System.lineSeparator());
            artifact.detail().ifPresent(value ->
                    output.append("  description=").append(value).append(System.lineSeparator()));
        }

        output.append("handoff=").append(record.handoff().isPresent()).append(System.lineSeparator());
        output.append("successors=").append(record.successorAssignments().size()).append(System.lineSeparator());
        return output.toString();
    }

    private static long sessionId(ParsedArguments parsedArguments) {
        if (parsedArguments.remainingArgs().size() != 1) {
            throw new IllegalArgumentException(USAGE);
        }
        String value = parsedArguments.remainingArgs().get(0);
        try {
            long sessionId = Long.parseLong(value);
            if (sessionId <= 0) {
                throw new IllegalArgumentException("Session ID must be positive: " + value);
            }
            return sessionId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid session ID: " + value, exception);
        }
    }
}
