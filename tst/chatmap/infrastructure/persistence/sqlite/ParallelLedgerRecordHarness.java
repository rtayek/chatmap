package chatmap.infrastructure.persistence.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import chatmap.app.ApplicationBootstrap;
import chatmap.app.ServiceGraph;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.application.service.WorkerLifecycleService;
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
 * Usage: ParallelLedgerRecordHarness --home &lt;dir&gt; --reports &lt;dir&gt; [--started &lt;iso8601&gt;]
 *
 * The reports directory must contain worker-1.md, worker-2.md, worker-3.md, and
 * synthesis.md. Each worker artifact points at its report file by absolute path.
 */
public final class ParallelLedgerRecordHarness {

    private ParallelLedgerRecordHarness() {
    }

    public static void main(String[] args) {
        Path home = null;
        Path reports = null;
        String started = "unspecified";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--home" -> home = requireValue(args, ++i, "--home");
                case "--reports" -> reports = requireValue(args, ++i, "--reports");
                case "--started" -> started = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (home == null || reports == null) {
            System.err.println("Usage: ParallelLedgerRecordHarness --home <dir> --reports <dir> [--started <iso8601>]");
            System.exit(2);
            return;
        }
        try {
            new ParallelLedgerRecordHarness().run(home, reports, started);
        } catch (Exception failure) {
            System.err.println("Parallel ledger record harness failed: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run(Path home, Path reports, String started) throws Exception {
        Path worker1 = requireReport(reports, "worker-1.md");
        Path worker2 = requireReport(reports, "worker-2.md");
        Path worker3 = requireReport(reports, "worker-3.md");
        Path synthesis = requireReport(reports, "synthesis.md");

        ResolvedPaths paths = new ResolvedPaths(home, home.resolve("chatmap.db"));
        ServiceGraph services = ApplicationBootstrap.open(paths, ServiceGraph.Integrations.none());

        long coordinatorSessionId;
        try {
            WorkerLifecycleService service = services.workerLifecycleService();

            WorkerAssignment coordinator = service.createAssignment(new WorkerAssignmentInput(
                    "Coordinate one bounded parallel read-only subagent run and record its structure",
                    "Pinned commit 1bb642d390059e955a548148a4bb3b546f1c3d2f; run started " + started
                            + "; reports directory " + reports.toAbsolutePath(),
                    "Native subagents for execution; ChatMap worker-lifecycle service for recording",
                    "Read-only workers; no schema change; no general scheduler; do not read .chatmap-local",
                    "Coordinator, three sibling workers, and synthesis are all recorded and reopenable",
                    "If a worker fails or is missing, record it and continue; do not fabricate evidence"));
            WorkerSession coordinatorSession = service.createSession(coordinator.id(), "coordinator:claude-opus");
            service.transition(coordinatorSession.id(), WorkerLifecycleState.WORKING);
            coordinatorSessionId = coordinatorSession.id();

            long w1 = recordWorker(service, coordinatorSession.id(), 1, "lifecycle-evidence", worker1,
                    "Determine whether the existing lifecycle model can record coordinator, three siblings, and synthesis without a schema change",
                    "Report the fan-in limitation explicitly; back every fact with file evidence");
            long w2 = recordWorker(service, coordinatorSession.id(), 2, "operations-evidence", worker2,
                    "Examine isolation, timing, failure handling, and artifact paths for a recorded parallel run",
                    "Separate procedural safeguards from enforced safeguards; back every fact with file evidence");
            long w3 = recordWorker(service, coordinatorSession.id(), 3, "independent-verifier", worker3,
                    "Independently confirm or refute the plan's central claims about the lifecycle model",
                    "Give each claim a verdict with file evidence; list contradictions and omissions");

            WorkerAssignment synthesisAssignment = service.createSuccessorAssignment(coordinatorSession.id(),
                    new WorkerAssignmentInput(
                            "Synthesize the three worker reports, preserving disagreement",
                            "Worker sessions " + w1 + ", " + w2 + ", " + w3
                                    + "; artifacts " + worker1.toAbsolutePath() + ", " + worker2.toAbsolutePath()
                                    + ", " + worker3.toAbsolutePath()
                                    + " (single-predecessor model: three parents named here, not linked structurally)",
                            "ChatMap worker-lifecycle service; read-only review of worker reports",
                            "Do not force consensus; label agreement, disagreement, and unverified claims separately",
                            "One synthesis artifact distinguishing agreement, disagreement, and unverified claims",
                            "If workers disagree, preserve the disagreement rather than dropping it"));
            WorkerSession synthesisSession = service.createSession(synthesisAssignment.id(),
                    "coordinator-synthesis:claude-opus");
            service.transition(synthesisSession.id(), WorkerLifecycleState.WORKING);
            service.addArtifact(synthesisSession.id(), "synthesis-report", synthesis.toAbsolutePath().toString(),
                    firstLine(synthesis));
            service.transition(synthesisSession.id(), WorkerLifecycleState.COMPLETED);
            service.storeHandoff(synthesisSession.id(), new WorkerSemanticHandoffInput(
                    "Synthesized three concurrent read-only worker reports into one record",
                    "Preserved disagreement instead of forcing consensus per the handoff boundary",
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
            System.out.println("home=" + paths.homeDirectory());
            System.out.println("database=" + paths.databasePath());
            System.out.println("coordinatorSessionId=" + coordinatorSessionId);
            System.out.println("workerSessionIds=" + w1 + "," + w2 + "," + w3);
            System.out.println("synthesisSessionId=" + synthesisSession.id());
        } finally {
            services.close();
        }

        // Prove durability: reopen the same database in a fresh service graph and traverse.
        ServiceGraph reopened = ApplicationBootstrap.open(paths, ServiceGraph.Integrations.none());
        try {
            WorkerLifecycleChain chain = reopened.workerLifecycleService().chainFrom(coordinatorSessionId);
            printChain(chain);
            verify(chain);
        } finally {
            reopened.close();
        }
    }

    private long recordWorker(WorkerLifecycleService service, long coordinatorSessionId, int index, String role,
            Path report, String task, String definitionOfDone) throws Exception {
        WorkerAssignment assignment = service.createSuccessorAssignment(coordinatorSessionId,
                new WorkerAssignmentInput(
                        task,
                        "Pinned commit 1bb642d; report file " + report.toAbsolutePath(),
                        "Read-only search and read tools (Explore subagent; no write, edit, build, or git)",
                        "Read-only; no builds, tests, formatting, writes, git, or .chatmap-local access",
                        definitionOfDone,
                        "If blocked, emit a FAILURE section and return partial evidence"));
        WorkerSession session = service.createSession(assignment.id(), "worker-" + index + ":" + role);
        service.transition(session.id(), WorkerLifecycleState.WORKING);
        service.addArtifact(session.id(), "worker-" + index + "-report", report.toAbsolutePath().toString(),
                firstLine(report));
        service.transition(session.id(), WorkerLifecycleState.COMPLETED);
        service.storeHandoff(session.id(), new WorkerSemanticHandoffInput(
                "Completed read-only " + role + " review at pinned commit 1bb642d",
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

    private static void verify(WorkerLifecycleChain chain) {
        if (chain.records().size() != 5) {
            throw new IllegalStateException("Expected 5 sessions (coordinator + 3 workers + synthesis), found "
                    + chain.records().size());
        }
        WorkerLifecycleRecord coordinator = chain.records().get(0);
        if (coordinator.successorAssignments().size() != 4) {
            throw new IllegalStateException("Expected coordinator to have 4 successors (3 workers + synthesis), found "
                    + coordinator.successorAssignments().size());
        }
        System.out.println();
        System.out.println("VERIFIED: coordinator + 3 sibling workers + synthesis, reopened successfully.");
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
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return Path.of(args[index]);
    }
}
