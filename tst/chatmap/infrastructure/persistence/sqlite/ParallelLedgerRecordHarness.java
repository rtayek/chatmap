package chatmap.infrastructure.persistence.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;

import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.FailureReport;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.application.service.WorkerLifecycleService.WorkerSemanticHandoffInput;
import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSession;

/**
 * Records one real, bounded parallel-subagent run into an isolated ChatMap home
 * using only the existing worker-lifecycle service. It does not launch workers;
 * an external runtime did that. This harness records their structure.
 *
 * Usage: ParallelLedgerRecordHarness --home &lt;dir&gt; --reports &lt;dir&gt;
 * [--started &lt;iso8601&gt;] [--revision &lt;commit&gt;] [--failed-worker &lt;0..3&gt;]
 *
 * The reports directory must contain worker-1.md, worker-2.md, worker-3.md, and
 * synthesis.md. Each successful worker artifact points at its report file by
 * absolute path; a failed worker records only its reason and partial work.
 */
public final class ParallelLedgerRecordHarness {

    private ParallelLedgerRecordHarness() {
    }

    public static void main(String[] args) {
        Path home = null;
        Path reports = null;
        String started = "unspecified";
        String revision = "unspecified";
        int failedWorker = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--home" -> home = requireValue(args, ++i, "--home");
                case "--reports" -> reports = requireValue(args, ++i, "--reports");
                case "--started" -> started = requireStringValue(args, ++i, "--started");
                case "--revision" -> revision = requireStringValue(args, ++i, "--revision");
                case "--failed-worker" -> failedWorker = Integer.parseInt(
                        requireStringValue(args, ++i, "--failed-worker"));
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (home == null || reports == null) {
            System.err.println("Usage: ParallelLedgerRecordHarness --home <dir> --reports <dir>"
                    + " [--started <iso8601>] [--revision <commit>] [--failed-worker <0..3>]");
            System.exit(2);
            return;
        }
        if (failedWorker < 0 || failedWorker > 3) {
            throw new IllegalArgumentException("--failed-worker must be 0, 1, 2, or 3");
        }
        try {
            new ParallelLedgerRecordHarness().run(home, reports, started, revision, failedWorker);
        } catch (Exception failure) {
            System.err.println("Parallel ledger record harness failed: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run(Path home, Path reports, String started, String revision, int failedWorker) throws Exception {
        Path worker1 = requireReport(reports, "worker-1.md");
        Path worker2 = requireReport(reports, "worker-2.md");
        Path worker3 = requireReport(reports, "worker-3.md");
        Path synthesis = requireReport(reports, "synthesis.md");

        Files.createDirectories(home);
        Path database = home.resolve("chatmap.db");

        long coordinatorSessionId;
        try (Connection connection = new Database("jdbc:sqlite:" + database).openAndInitialize()) {
            WorkerLifecycleService service = new WorkerLifecycleService(new WorkerLifecycleRepository(connection),
                    new TransactionRunner(connection), Clock.systemUTC());

            WorkerAssignment coordinator = service.createAssignment(new WorkerAssignmentInput(
                    "Coordinate one bounded parallel read-only subagent run and record its structure",
                    "Pinned commit " + revision + "; run started " + started
                            + "; reports directory " + reports.toAbsolutePath(),
                    "Native subagents for execution; ChatMap worker-lifecycle service for recording",
                    "Read-only workers; no schema change; no general scheduler; do not read .chatmap-local",
                    "Coordinator, three sibling workers, and synthesis are all recorded and reopenable",
                    "If a worker fails or is missing, record it and continue; do not fabricate evidence"));
            WorkerSession coordinatorSession = service.createSession(coordinator.id(), "coordinator:claude-opus");
            service.transition(coordinatorSession.id(), WorkerLifecycleState.WORKING);
            coordinatorSessionId = coordinatorSession.id();

            long w1 = recordWorker(service, coordinatorSession.id(), 1, "lifecycle-evidence", worker1, revision,
                    "Determine whether the existing lifecycle model can record coordinator, three siblings,"
                            + " and synthesis without a schema change",
                    "Report the fan-in limitation explicitly; back every fact with file evidence", failedWorker == 1);
            long w2 = recordWorker(service, coordinatorSession.id(), 2, "operations-evidence", worker2, revision,
                    "Examine isolation, timing, failure handling, and artifact paths for a recorded parallel run",
                    "Separate procedural safeguards from enforced safeguards; back every fact with file evidence",
                    failedWorker == 2);
            String worker3Role = failedWorker == 3 ? "deliberate-failure" : "independent-verifier";
            String worker3Task = failedWorker == 3
                    ? "Check the exact required input named in the worker report and stop without fabrication"
                            + " if it is missing"
                    : "Independently confirm or refute the plan's central claims about the lifecycle model";
            String worker3Done = failedWorker == 3
                    ? "Record the exact failure reason, preserved partial work, and absence of an artifact claim"
                    : "Give each claim a verdict with file evidence; list contradictions and omissions";
            long w3 = recordWorker(service, coordinatorSession.id(), 3, worker3Role, worker3, revision,
                    worker3Task, worker3Done, failedWorker == 3);

            String workerEvidence = evidenceDescription(worker1, 1, failedWorker)
                    + ", " + evidenceDescription(worker2, 2, failedWorker)
                    + ", " + evidenceDescription(worker3, 3, failedWorker);

            WorkerAssignment synthesisAssignment = service.createSuccessorAssignment(coordinatorSession.id(),
                    new WorkerAssignmentInput(
                            "Synthesize the three worker reports, preserving disagreement",
                            "Worker sessions " + w1 + ", " + w2 + ", " + w3
                                    + "; evidence " + workerEvidence
                                    + " (single-predecessor model: three parents named here, not linked structurally)",
                            "ChatMap worker-lifecycle service; read-only review of worker reports",
                            "Do not force consensus; label agreement, disagreement, and unverified claims separately",
                            "One synthesis artifact distinguishing completed evidence, failed-worker evidence,"
                                    + " disagreement, and unverified claims",
                            "If workers disagree, preserve the disagreement rather than dropping it"));
            WorkerSession synthesisSession = service.createSession(synthesisAssignment.id(),
                    "coordinator-synthesis:claude-opus");
            service.transition(synthesisSession.id(), WorkerLifecycleState.WORKING);
            service.addArtifact(synthesisSession.id(), "synthesis-report", synthesis.toAbsolutePath().toString(),
                    firstLine(synthesis));
            service.transition(synthesisSession.id(), WorkerLifecycleState.COMPLETED);
            String completedWork = failedWorker == 0
                    ? "Synthesized three successful workers into one record"
                    : "Synthesized successful workers and one deliberately failed worker into one record";
            String decision = failedWorker == 0
                    ? "Preserved disagreement instead of forcing consensus per the handoff boundary"
                    : "Preserved the failed worker's reason and partial work instead of treating it as successful";
            service.storeHandoff(synthesisSession.id(), new WorkerSemanticHandoffInput(
                    completedWork,
                    decision,
                    "synthesis-report at " + synthesis.toAbsolutePath(),
                    "Single-predecessor model cannot structurally link three parents; named in context instead",
                    "Decide whether a fan-in link or run identifier is worth a schema change",
                    "Reopen the database in a separate process and verify chainFrom",
                    "Review the recorded parallel run",
                    "Worker sessions " + w1 + ", " + w2 + ", " + w3,
                    "workerLifecycleRecord CLI and chainFrom",
                    "No schema change without evidence",
                    "Confirm coordinator, three siblings, and synthesis are reopenable",
                    "If reopen fails, preserve the database and report the failure"));

            service.transition(coordinatorSession.id(), WorkerLifecycleState.COMPLETED);

            System.out.println("RECORDED RUN");
            System.out.println("home=" + home);
            System.out.println("database=" + database);
            System.out.println("coordinatorSessionId=" + coordinatorSessionId);
            System.out.println("workerSessionIds=" + w1 + "," + w2 + "," + w3);
            System.out.println("synthesisSessionId=" + synthesisSession.id());
        }

        // Prove durability: reopen the same database through a fresh service instance and traverse.
        try (Connection connection = new Database("jdbc:sqlite:" + database).openAndInitialize()) {
            WorkerLifecycleService service = new WorkerLifecycleService(new WorkerLifecycleRepository(connection),
                    new TransactionRunner(connection), Clock.systemUTC());
            WorkerLifecycleChain chain = service.chainFrom(coordinatorSessionId);
            printChain(chain);
            verify(chain, worker1, worker2, worker3, synthesis, failedWorker);
        }
    }

    private long recordWorker(WorkerLifecycleService service, long coordinatorSessionId, int index, String role,
            Path report, String revision, String task, String definitionOfDone, boolean failed) throws Exception {
        WorkerAssignment assignment = service.createSuccessorAssignment(coordinatorSessionId,
                new WorkerAssignmentInput(
                        task,
                        "Pinned commit " + revision + "; report file " + report.toAbsolutePath(),
                        "Read-only search and read tools (Explore subagent; no write, edit, build, or git)",
                        "Read-only; no builds, tests, formatting, writes, git, or .chatmap-local access",
                        definitionOfDone,
                        "If blocked, emit a FAILURE section and return partial evidence"));
        WorkerSession session = service.createSession(assignment.id(), "worker-" + index + ":" + role);
        service.transition(session.id(), WorkerLifecycleState.WORKING);
        if (failed) {
            service.transitionWithFailure(session.id(), WorkerLifecycleState.FAILED,
                    new FailureReport(failureReason(report), Files.readString(report).strip()));
            return session.id();
        }
        service.addArtifact(session.id(), "worker-" + index + "-report", report.toAbsolutePath().toString(),
                firstLine(report));
        service.transition(session.id(), WorkerLifecycleState.COMPLETED);
        service.storeHandoff(session.id(), new WorkerSemanticHandoffInput(
                "Completed read-only " + role + " review at pinned commit " + revision,
                "Findings recorded verbatim in the report artifact",
                "worker-" + index + "-report at " + report.toAbsolutePath(),
                "None reported beyond the report body",
                "See report body for any required decisions",
                "Return report to coordinator for synthesis",
                "Synthesize with sibling worker reports",
                "worker-" + index + "-report",
                "Read-only review",
                "No schema change",
                "Report incorporated into synthesis",
                "If a claim is unverified, the verifier flags it"));
        service.transition(session.id(), WorkerLifecycleState.RETIRED);
        return session.id();
    }

    private static void printChain(WorkerLifecycleChain chain) {
        System.out.println();
        System.out.println("REOPENED CHAIN: " + chain.records().size() + " sessions");
        for (WorkerLifecycleRecord record : chain.records()) {
            System.out.println("  session=" + record.session().id()
                    + " worker=" + record.session().workerIdentity()
                    + " state=" + record.session().lifecycleState()
                    + " assignment=" + record.assignment().id()
                    + " events=" + record.events().size()
                    + " artifacts=" + record.artifacts().size()
                    + " successors=" + record.successorAssignments().size()
                    + " handoff=" + record.handoff().isPresent());
            for (WorkerArtifact artifact : record.artifacts()) {
                System.out.println("      artifact " + artifact.label() + " -> " + artifact.location());
            }
        }
    }

    private static void verify(WorkerLifecycleChain chain, Path worker1, Path worker2, Path worker3,
            Path synthesis, int failedWorker) {
        if (chain.records().size() != 5) {
            throw new IllegalStateException("Expected 5 sessions (coordinator + 3 workers + synthesis), found "
                    + chain.records().size());
        }
        WorkerLifecycleRecord coordinator = chain.records().get(0);
        if (coordinator.successorAssignments().size() != 4) {
            throw new IllegalStateException("Expected coordinator to have 4 successors (3 workers + synthesis), found "
                    + coordinator.successorAssignments().size());
        }
        Path[] reports = {worker1, worker2, worker3};
        for (int index = 1; index <= 3; index++) {
            int workerIndex = index;
            WorkerLifecycleRecord worker = chain.records().stream()
                    .filter(record -> record.session().workerIdentity().startsWith("worker-" + workerIndex + ":"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing worker " + workerIndex));
            if (index == failedWorker) {
                if (worker.session().lifecycleState() != WorkerLifecycleState.FAILED
                        || worker.events().stream().noneMatch(event -> event.toState() == WorkerLifecycleState.FAILED
                                && event.reason() != null && !event.reason().isBlank()
                                && event.partialWork() != null && !event.partialWork().isBlank())
                        || worker.handoff().isPresent() || !worker.artifacts().isEmpty()) {
                    throw new IllegalStateException("Failed worker evidence was not preserved correctly");
                }
            } else if (worker.session().lifecycleState() != WorkerLifecycleState.RETIRED
                    || worker.artifacts().size() != 1 || worker.handoff().isEmpty()
                    || !Files.isRegularFile(reports[index - 1])) {
                throw new IllegalStateException("Successful worker " + index + " evidence is incomplete");
            }
        }
        WorkerLifecycleRecord synthesisRecord = chain.records().stream()
                .filter(record -> record.session().workerIdentity().startsWith("coordinator-synthesis:"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing synthesis session"));
        if (synthesisRecord.session().lifecycleState() != WorkerLifecycleState.COMPLETED
                || synthesisRecord.artifacts().size() != 1 || synthesisRecord.handoff().isEmpty()
                || !Files.isRegularFile(synthesis)) {
            throw new IllegalStateException("Synthesis evidence is incomplete");
        }
        System.out.println();
        System.out.println("VERIFIED: coordinator + 3 sibling workers + synthesis reopened; failedWorker="
                + failedWorker + ".");
    }

    private static String evidenceDescription(Path report, int index, int failedWorker) {
        String type = index == failedWorker ? "failed-worker partial evidence " : "artifact ";
        return type + report.toAbsolutePath();
    }

    private static String failureReason(Path report) throws IOException {
        for (String line : Files.readAllLines(report)) {
            if (line.startsWith("Reason: ")) {
                return line.substring("Reason: ".length()).strip();
            }
        }
        throw new IllegalArgumentException("Failed-worker report has no 'Reason: ' line: "
                + report.toAbsolutePath());
    }

    private static Path requireReport(Path reports, String name) {
        Path report = reports.resolve(name);
        if (!Files.isRegularFile(report)) {
            throw new IllegalArgumentException("Missing required report: " + report.toAbsolutePath());
        }
        return report;
    }

    private static String firstLine(Path report) {
        try {
            for (String line : Files.readAllLines(report)) {
                String trimmed = line.strip().replaceFirst("^#+\\s*", "");
                if (!trimmed.isEmpty()) {
                    return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
                }
            }
        } catch (IOException ignored) {
            // Fall through to a stable default when the report cannot be read.
        }
        return "See report file";
    }

    private static Path requireValue(String[] args, int index, String flag) {
        return Path.of(requireStringValue(args, index, flag));
    }

    private static String requireStringValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }
}
