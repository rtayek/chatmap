package chatmap.presentation.cli;

import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.DecisionRequest;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.application.service.WorkerLifecycleService.WorkerSemanticHandoffInput;
import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSession;

/** Deterministic CLI demonstration for the worker-lifecycle vertical slice. */
public final class WorkerLifecycleDemoCli {
    private static final String USAGE = "Usage: workerLifecycleDemo [--home <directory>]";

    private WorkerLifecycleDemoCli() {
    }

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, USAGE);
        if (!parsedArguments.remainingArgs().isEmpty()) {
            CliBootstrap.exitWithUsage(USAGE);
            return;
        }
        try {
            WorkerLifecycleChain chain = execute(parsedArguments);
            print(chain);
        } catch (Exception e) {
            System.err.println("Could not run worker lifecycle demo: " + e.getMessage());
            System.exit(1);
        }
    }

    public static WorkerLifecycleChain execute(ParsedArguments parsedArguments) throws Exception {
        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            WorkerLifecycleService service = context.services().workerLifecycleService();
            WorkerAssignment assignment = service.createAssignment(new WorkerAssignmentInput(
                    "Implement a small worker lifecycle experiment",
                    "handoffs/chatmap-worker-lifecycle-handoff.md and src/tst persistence code",
                    "Gradle wrapper, SQLite, JUnit, fake/manual worker boundary",
                    "Do not launch agents, automate browsers, or redesign the UI",
                    "Persist assignment, session, transitions, artifacts, handoff, retirement, and successor",
                    "If blocked, stop safely, preserve partial work, and report the required decision"));
            WorkerSession session = service.createSession(assignment.id(), "manual-worker:codex-demo");
            service.transition(session.id(), WorkerLifecycleState.WORKING);
            service.transition(session.id(), WorkerLifecycleState.WAITING_FOR_DECISION, new DecisionRequest(
                    "Should the successor continue with CLI only?",
                    "The experiment intentionally avoids GUI and agent automation",
                    "Assignment and initial session have been persisted"));
            service.transition(session.id(), WorkerLifecycleState.WORKING);
            WorkerArtifact artifact = service.addArtifact(session.id(), "demo-output",
                    "chatmap://worker-lifecycle/demo", "Synthetic artifact proving location tracking");
            service.transition(session.id(), WorkerLifecycleState.COMPLETED);
            service.storeHandoff(session.id(), new WorkerSemanticHandoffInput(
                    "Created a persisted worker lifecycle record",
                    "Kept worker execution outside ChatMap; ChatMap records continuity only",
                    artifact.label() + " at " + artifact.location(),
                    "No real worker adapter is launched in this experiment",
                    "Decide whether to keep building this lifecycle model",
                    "Create a successor assignment for another worker",
                    "Review the worker lifecycle experiment",
                    "Use the demo chain and focused tests",
                    "Gradle wrapper and repository inspection",
                    "Stay on feature/worker-lifecycle; no browser/provider automation",
                    "Recommend continue, revise, or discard",
                    "If blocked, preserve partial review notes"));
            service.transition(session.id(), WorkerLifecycleState.RETIRED);

            WorkerAssignment successor = service.createSuccessorAssignment(session.id(), new WorkerAssignmentInput(
                    "Review the worker lifecycle experiment",
                    "Use the predecessor semantic handoff and demo artifact",
                    "Gradle wrapper and source inspection",
                    "No branch merge or push without explicit instruction",
                    "Report whether the experiment should continue",
                    "If blocked, report the missing decision"));
            WorkerSession successorSession = service.createSession(successor.id(), "manual-worker:reviewer-demo");
            service.transition(successorSession.id(), WorkerLifecycleState.WORKING);
            return service.chainFrom(session.id());
        }
    }

    private static void print(WorkerLifecycleChain chain) {
        System.out.println("worker lifecycle chain: " + chain.records().size() + " sessions");
        for (WorkerLifecycleRecord record : chain.records()) {
            System.out.println(record.session().id() + " " + record.session().workerIdentity()
                    + " " + record.session().lifecycleState()
                    + " assignment=" + record.assignment().id()
                    + " task=\"" + record.assignment().task() + "\"");
            System.out.println("  events=" + record.events().size()
                    + " artifacts=" + record.artifacts().size()
                    + " successors=" + record.successorAssignments().size()
                    + " handoff=" + record.handoff().isPresent());
        }
    }
}
