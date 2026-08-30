package chatmap.infrastructure.persistence.sqlite;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import chatmap.app.ApplicationBootstrap;
import chatmap.app.ServiceGraph;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.DecisionRequest;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.application.service.WorkerLifecycleService.WorkerSemanticHandoffInput;
import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleEvent;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSemanticHandoff;
import chatmap.domain.WorkerSession;

public final class WorkerLifecycleSoakHarness {

    private static final int DEFAULT_CYCLES = 1000;
    private static final long DEFAULT_SEED = 42L;
    private static final int DEFAULT_CHECKPOINT = 500;
    private static final int MAX_SAMPLES = 64;

    private final int totalCycles;
    private final long seed;
    private final int checkpointInterval;
    private final Path homeDirectory;
    private final boolean isManagedTempHome;

    private final LatencyTracker assignmentLatency = new LatencyTracker("createAssignment");
    private final LatencyTracker sessionLatency = new LatencyTracker("createSession");
    private final LatencyTracker transitionLatency = new LatencyTracker("transition (Event insert)");
    private final LatencyTracker handoffLatency = new LatencyTracker("storeHandoff");
    private final LatencyTracker chainLatency = new LatencyTracker("chainFrom (Traversal)");

    private final List<SampledRecord> sampledRecords = new ArrayList<>();
    private int totalVariableArtifacts;

    public WorkerLifecycleSoakHarness(int totalCycles, long seed, int checkpointInterval, Path homeDirectory)
            throws IOException {
        this.totalCycles = totalCycles;
        this.seed = seed;
        this.checkpointInterval = checkpointInterval;
        if (homeDirectory != null) {
            this.homeDirectory = homeDirectory;
            this.isManagedTempHome = false;
        } else {
            this.homeDirectory = Files.createTempDirectory("chatmap-soak-");
            this.isManagedTempHome = true;
        }
    }

    public static void main(String[] args) {
        int cycles = DEFAULT_CYCLES;
        long seed = DEFAULT_SEED;
        int checkpoint = DEFAULT_CHECKPOINT;
        Path home = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--cycles".equals(arg) && i + 1 < args.length) {
                cycles = Integer.parseInt(args[++i]);
            } else if ("--seed".equals(arg) && i + 1 < args.length) {
                seed = Long.parseLong(args[++i]);
            } else if ("--checkpoint-interval".equals(arg) && i + 1 < args.length) {
                checkpoint = Integer.parseInt(args[++i]);
            } else if ("--home".equals(arg) && i + 1 < args.length) {
                String val = args[++i];
                if (!val.isBlank()) {
                    home = Path.of(val);
                }
            }
        }

        try {
            WorkerLifecycleSoakHarness harness = new WorkerLifecycleSoakHarness(cycles, seed, checkpoint, home);
            harness.execute();
        } catch (Throwable t) {
            System.err.println("WorkerLifecycle soak harness failed: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public void execute() throws Exception {
        boolean success = false;
        long startTimeNanos = System.nanoTime();
        ResolvedPaths paths = new ResolvedPaths(homeDirectory, homeDirectory.resolve("chatmap.db"));
        ServiceGraph services = ApplicationBootstrap.open(paths, ServiceGraph.Integrations.none());
        SplittableRandom random = new SplittableRandom(seed);

        try {
            WorkerLifecycleService service = services.workerLifecycleService();

            long startAssignNanos = System.nanoTime();
            WorkerAssignment currentAssignment = service.createAssignment(newAssignmentInput("root-task", random));
            assignmentLatency.record(System.nanoTime() - startAssignNanos);

            int rebootsExecuted = 0;

            for (int cycle = 1; cycle <= totalCycles; cycle++) {
                int scenarioSlot = (cycle - 1) % 20;

                CurrentCycleExecution execution = executeCycle(cycle, scenarioSlot, currentAssignment, service, random);
                currentAssignment = execution.nextAssignment;

                if (cycle % 16 == 0 && sampledRecords.size() < MAX_SAMPLES) {
                    sampledRecords.add(execution.snapshot);
                }

                if (cycle % 10 == 0) {
                    long startChainNanos = System.nanoTime();
                    WorkerLifecycleChain chain = service.chainFrom(execution.sessionId);
                    chainLatency.record(System.nanoTime() - startChainNanos);
                    if (chain.records().isEmpty()) {
                        throw new IllegalStateException("chainFrom returned empty records for session "
                                + execution.sessionId);
                    }
                }

                if (cycle % checkpointInterval == 0 && cycle < totalCycles) {
                    services.close();
                    services = ApplicationBootstrap.open(paths, ServiceGraph.Integrations.none());
                    service = services.workerLifecycleService();
                    rebootsExecuted++;

                    Connection conn = serviceConnection(service);
                    verifySqlInvariants(conn, cycle);
                    verifySampledRecords(service, cycle);
                }
            }

            Connection conn = serviceConnection(service);
            verifySqlInvariants(conn, totalCycles);
            verifySampledRecords(service, totalCycles);

            long elapsedNanos = System.nanoTime() - startTimeNanos;
            printReport(elapsedNanos, rebootsExecuted, paths.databasePath());
            success = true;
        } finally {
            services.close();
            stopLogging();
            if (isManagedTempHome) {
                if (success) {
                    deleteDirectory(homeDirectory);
                } else {
                    System.err.println("Soak test failed. Preserved temporary database home at: "
                            + homeDirectory.toAbsolutePath());
                }
            }
        }
    }

    private static void stopLogging() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext context) {
            context.stop();
        }
    }

    private CurrentCycleExecution executeCycle(int cycle, int scenarioSlot, WorkerAssignment assignment,
            WorkerLifecycleService service, SplittableRandom random) throws SQLException {

        String worker = "worker-" + scenarioSlot + ":" + cycle;
        long startSessionNanos = System.nanoTime();
        WorkerSession session = service.createSession(assignment.id(), worker);
        sessionLatency.record(System.nanoTime() - startSessionNanos);

        List<WorkerLifecycleEvent> events = new ArrayList<>();
        List<WorkerArtifact> artifacts = new ArrayList<>();
        WorkerSemanticHandoff handoff = null;

        if (scenarioSlot >= 0 && scenarioSlot <= 11) {
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.WORKING, null));
            WorkerArtifact art = service.addArtifact(session.id(), "art-" + cycle, "chatmap://art/" + cycle,
                    "Payload " + random.nextInt(1000));
            artifacts.add(art);
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.COMPLETED, null));
            handoff = timedHandoff(service, session.id(), newHandoffInput(cycle, random));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.RETIRED, null));
        } else if (scenarioSlot >= 12 && scenarioSlot <= 14) {
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.WORKING, null));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.WAITING_FOR_DECISION,
                    new DecisionRequest("Question " + cycle + "?", "Need decision", "Partial work " + cycle)));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.WORKING, null));
            WorkerArtifact art = service.addArtifact(session.id(), "art-" + cycle, "chatmap://art/" + cycle,
                    "Payload " + random.nextInt(1000));
            artifacts.add(art);
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.COMPLETED, null));
            handoff = timedHandoff(service, session.id(), newHandoffInput(cycle, random));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.RETIRED, null));
        } else if (scenarioSlot >= 15 && scenarioSlot <= 16) {
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.WORKING, null));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.FAILED, null));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.RETIRED, null));
        } else if (scenarioSlot == 17) {
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.CANCELLED, null));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.RETIRED, null));
        } else if (scenarioSlot == 18) {
            try {
                service.transition(session.id(), WorkerLifecycleState.COMPLETED);
                throw new AssertionError("Invalid transition QUEUED -> COMPLETED should have failed");
            } catch (IllegalStateException expected) {
                // Expected validation failure; state and events remain unmutated.
            }
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.WORKING, null));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.COMPLETED, null));
            handoff = timedHandoff(service, session.id(), newHandoffInput(cycle, random));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.RETIRED, null));
        } else {
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.WORKING, null));
            int artifactCount = selectVariableArtifactCount(cycle);
            totalVariableArtifacts += artifactCount;
            for (int k = 0; k < artifactCount; k++) {
                artifacts.add(service.addArtifact(session.id(), "art-" + cycle + "-" + k,
                        "chatmap://art/" + cycle + "/" + k, "Variable artifact " + k));
            }
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.COMPLETED, null));
            handoff = timedHandoff(service, session.id(), newHandoffInput(cycle, random));
            events.add(timedTransition(service, session.id(), WorkerLifecycleState.RETIRED, null));
        }

        long startSuccessorNanos = System.nanoTime();
        WorkerAssignment nextAssignment = service.createSuccessorAssignment(session.id(),
                newAssignmentInput("successor-task-" + cycle, random));
        assignmentLatency.record(System.nanoTime() - startSuccessorNanos);

        SampledRecord snapshot = new SampledRecord(cycle, assignment, session, events, artifacts, handoff,
                nextAssignment.id());
        return new CurrentCycleExecution(session.id(), nextAssignment, snapshot);
    }

    private int selectVariableArtifactCount(int cycle) {
        int mod = (cycle / 20) % 3;
        if (mod == 0) {
            return 0;
        } else if (mod == 1) {
            return 2;
        } else {
            return 3;
        }
    }

    private WorkerLifecycleEvent timedTransition(WorkerLifecycleService service, long sessionId,
            WorkerLifecycleState nextState, DecisionRequest decision) throws SQLException {
        long startNanos = System.nanoTime();
        service.transition(sessionId, nextState, decision);
        transitionLatency.record(System.nanoTime() - startNanos);
        WorkerLifecycleRecord rec = service.record(sessionId);
        List<WorkerLifecycleEvent> events = rec.events();
        return events.get(events.size() - 1);
    }

    private WorkerSemanticHandoff timedHandoff(WorkerLifecycleService service, long sessionId,
            WorkerSemanticHandoffInput input) throws SQLException {
        long startNanos = System.nanoTime();
        WorkerSemanticHandoff handoff = service.storeHandoff(sessionId, input);
        handoffLatency.record(System.nanoTime() - startNanos);
        return handoff;
    }

    private void verifySqlInvariants(Connection conn, int cycle) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                throw new IllegalStateException("PRAGMA integrity_check failed at cycle " + cycle);
            }
            if (rs.next()) {
                throw new IllegalStateException("PRAGMA integrity_check returned extra rows at cycle " + cycle);
            }
        }

        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
            if (rs.next()) {
                throw new IllegalStateException("PRAGMA foreign_key_check found violations at cycle " + cycle);
            }
        }

        assertCountZero(conn, "SELECT COUNT(*) FROM workerSessions s LEFT JOIN workerAssignments a "
                + "ON s.assignmentId = a.id WHERE a.id IS NULL", "Orphan sessions");
        assertCountZero(conn, "SELECT COUNT(*) FROM workerLifecycleEvents e LEFT JOIN workerSessions s "
                + "ON e.sessionId = s.id WHERE s.id IS NULL", "Orphan events");
        assertCountZero(conn, "SELECT COUNT(*) FROM workerArtifacts a LEFT JOIN workerSessions s "
                + "ON a.sessionId = s.id WHERE s.id IS NULL", "Orphan artifacts");
        assertCountZero(conn, "SELECT COUNT(*) FROM workerSemanticHandoffs h LEFT JOIN workerSessions s "
                + "ON h.sessionId = s.id WHERE s.id IS NULL", "Orphan handoffs");
        assertCountZero(conn, "SELECT COUNT(*) FROM (SELECT sessionId FROM workerSemanticHandoffs "
                + "GROUP BY sessionId HAVING COUNT(*) > 1)", "Duplicate session handoffs");
        assertCountZero(conn, "SELECT COUNT(*) FROM workerAssignments a LEFT JOIN workerSessions s "
                + "ON a.predecessorSessionId = s.id WHERE a.predecessorSessionId IS NOT NULL AND s.id IS NULL",
                "Dangling successor links");

        verifyTransitionContinuity(conn);
        verifyExactRowCounts(conn, cycle);
    }

    private void verifyTransitionContinuity(Connection conn) throws SQLException {
        String sql = "SELECT sessionId, fromState, toState FROM workerLifecycleEvents ORDER BY sessionId, id";
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            long currentSessionId = -1;
            String expectedFromState = "QUEUED";

            while (rs.next()) {
                long sessionId = rs.getLong("sessionId");
                String fromState = rs.getString("fromState");
                String toState = rs.getString("toState");

                if (sessionId != currentSessionId) {
                    currentSessionId = sessionId;
                    expectedFromState = "QUEUED";
                }

                if (!expectedFromState.equals(fromState)) {
                    throw new IllegalStateException("Transition continuity broken for session " + sessionId
                            + ": expected fromState " + expectedFromState + " but found " + fromState);
                }
                expectedFromState = toState;
            }
        }
    }

    private void verifyExactRowCounts(Connection conn, int cycle) throws SQLException {
        int expectedAssignments = 1 + cycle;
        int expectedSessions = cycle;
        int blocks = cycle / 20;
        int remainder = cycle % 20;

        int expectedEvents = (blocks * 65) + eventsForRemainder(remainder);
        int expectedHandoffs = (blocks * 17) + handoffsForRemainder(remainder);
        int expectedArtifacts = (blocks * 15) + artifactsForRemainder(remainder) + totalVariableArtifacts;

        assertTableCount(conn, "workerAssignments", expectedAssignments);
        assertTableCount(conn, "workerSessions", expectedSessions);
        assertTableCount(conn, "workerLifecycleEvents", expectedEvents);
        assertTableCount(conn, "workerSemanticHandoffs", expectedHandoffs);
        assertTableCount(conn, "workerArtifacts", expectedArtifacts);
    }

    private int eventsForRemainder(int remainder) {
        int events = 0;
        for (int i = 0; i < remainder; i++) {
            if (i >= 0 && i <= 11) {
                events += 3;
            } else if (i >= 12 && i <= 14) {
                events += 5;
            } else if (i >= 15 && i <= 16) {
                events += 3;
            } else if (i == 17) {
                events += 2;
            } else {
                events += 3;
            }
        }
        return events;
    }

    private int handoffsForRemainder(int remainder) {
        int handoffs = 0;
        for (int i = 0; i < remainder; i++) {
            if (i >= 0 && i <= 14 || i == 18 || i == 19) {
                handoffs += 1;
            }
        }
        return handoffs;
    }

    private int artifactsForRemainder(int remainder) {
        int artifacts = 0;
        for (int i = 0; i < remainder; i++) {
            if (i >= 0 && i <= 14) {
                artifacts += 1;
            }
        }
        return artifacts;
    }

    private void verifySampledRecords(WorkerLifecycleService service, int currentCycle) throws SQLException {
        for (SampledRecord sample : sampledRecords) {
            if (sample.cycle > currentCycle) {
                continue;
            }
            WorkerLifecycleRecord actual = service.record(sample.session.id());
            if (actual.assignment().id() != sample.assignment.id()) {
                throw new IllegalStateException("Sample mismatch: assignment ID expected "
                        + sample.assignment.id() + " found " + actual.assignment().id());
            }
            if (!actual.session().workerIdentity().equals(sample.session.workerIdentity())) {
                throw new IllegalStateException("Sample mismatch: workerIdentity");
            }
            if (actual.events().size() != sample.events.size()) {
                throw new IllegalStateException("Sample mismatch: events size for session " + sample.session.id());
            }
            if (actual.artifacts().size() != sample.artifacts.size()) {
                throw new IllegalStateException("Sample mismatch: artifacts size for session " + sample.session.id());
            }
            if (sample.handoff == null && actual.handoff().isPresent()) {
                throw new IllegalStateException("Sample mismatch: unexpected handoff present");
            }
            if (sample.handoff != null && actual.handoff().isEmpty()) {
                throw new IllegalStateException("Sample mismatch: expected handoff missing");
            }
            if (actual.successorAssignments().isEmpty()
                    || actual.successorAssignments().get(0).id() != sample.successorAssignmentId) {
                throw new IllegalStateException("Sample mismatch: successor assignment ID");
            }
        }
    }

    private void assertCountZero(Connection conn, String sql, String description) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                int count = rs.getInt(1);
                if (count != 0) {
                    throw new IllegalStateException("Invariant violated [" + description + "]: count=" + count);
                }
            }
        }
    }

    private void assertTableCount(Connection conn, String table, int expectedCount) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                int count = rs.getInt(1);
                if (count != expectedCount) {
                    throw new IllegalStateException("Table count mismatch on " + table + ": expected "
                            + expectedCount + " but found " + count);
                }
            }
        }
    }

    private static Connection serviceConnection(WorkerLifecycleService service) throws SQLException {
        java.lang.reflect.Field storeField;
        try {
            storeField = WorkerLifecycleService.class.getDeclaredField("store");
            storeField.setAccessible(true);
            Object storeObj = storeField.get(service);
            java.lang.reflect.Field connField = storeObj.getClass().getDeclaredField("conn");
            connField.setAccessible(true);
            return (Connection) connField.get(storeObj);
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Could not access repository connection for audit", e);
        }
    }

    private void printReport(long elapsedNanos, int reboots, Path dbPath) {
        long elapsedMs = elapsedNanos / 1_000_000L;
        double elapsedSec = elapsedNanos / 1_000_000_000.0;
        double throughput = elapsedSec > 0 ? totalCycles / elapsedSec : 0;
        long dbSizeBytes = new File(dbPath.toString()).length();

        System.out.println("=== WORKER LIFECYCLE STAGE 1 SOAK REPORT ===");
        System.out.println("Cycles Completed:      " + totalCycles + " / " + totalCycles);
        System.out.println("Random Seed:           " + seed);
        System.out.println("Persistence Reboots:   " + reboots + " (at cycle " + checkpointInterval + ")");
        System.out.println("Elapsed Time:          " + elapsedMs + " ms (" + String.format("%.2f", elapsedSec) + " s)");
        System.out.println("Throughput:            " + String.format("%.1f", throughput) + " cycles/sec");
        System.out.println("Database Path:         " + dbPath);
        System.out.println("Final Database Size:   " + (dbSizeBytes / 1024) + " KB");
        System.out.println();
        System.out.println("--- ROW COUNT AUDIT ---");
        System.out.println("workerAssignments:     " + (1 + totalCycles) + " / " + (1 + totalCycles) + " (MATCH)");
        System.out.println("workerSessions:        " + totalCycles + " / " + totalCycles + " (MATCH)");
        System.out.println("workerLifecycleEvents: " + ((totalCycles / 20) * 65) + " / "
                + ((totalCycles / 20) * 65) + " (MATCH)");
        System.out.println("workerSemanticHandoffs: " + ((totalCycles / 20) * 17) + " / "
                + ((totalCycles / 20) * 17) + " (MATCH)");
        System.out.println("workerArtifacts:       " + (((totalCycles / 20) * 15) + totalVariableArtifacts)
                + " / " + (((totalCycles / 20) * 15) + totalVariableArtifacts) + " (MATCH)");
        System.out.println();
        System.out.println("--- INTEGRITY CHECKS (Cycles " + checkpointInterval + " & " + totalCycles + ") ---");
        System.out.println("PRAGMA integrity_check:  OK (1 row, 'ok')");
        System.out.println("PRAGMA foreign_key_check: OK (0 violations)");
        System.out.println("Relational Invariants:   OK (0 orphan records)");
        System.out.println("Transition Continuity:   OK (0 broken chains)");
        System.out.println("Sample ID Integrity:     OK (" + sampledRecords.size() + " records verified)");
        System.out.println();
        System.out.println("--- LATENCY HISTOGRAM (Microseconds) ---");
        System.out.printf("%-26s %-7s %-12s %-8s %-8s %-8s %-8s %-8s%n", "Operation", "Count", "Max Latency",
                "<100us", "<500us", "<1ms", "<5ms", ">=5ms");
        printTracker(assignmentLatency);
        printTracker(sessionLatency);
        printTracker(transitionLatency);
        printTracker(handoffLatency);
        printTracker(chainLatency);
        System.out.println("============================================");
    }

    private void printTracker(LatencyTracker tracker) {
        String maxStr = tracker.maxNanos >= 1_000_000L
                ? String.format("%.2f ms", tracker.maxNanos / 1_000_000.0)
                : (tracker.maxNanos / 1_000L) + " us";
        System.out.printf("%-26s %-7d %-12s %-8d %-8d %-8d %-8d %-8d%n", tracker.name, tracker.count, maxStr,
                tracker.bucketUnder100Us, tracker.bucket100To500Us, tracker.bucket500To1000Us, tracker.bucket1To5Ms,
                tracker.bucket5MsPlus);
    }

    private static WorkerAssignmentInput newAssignmentInput(String task, SplittableRandom random) {
        return new WorkerAssignmentInput(task, "Context and files " + random.nextInt(1000), "Tools "
                + random.nextInt(100), "Constraints " + random.nextInt(100), "DoD " + random.nextInt(100),
                "If blocked, preserve partial work and report missing decision");
    }

    private static WorkerSemanticHandoffInput newHandoffInput(int cycle, SplittableRandom random) {
        return new WorkerSemanticHandoffInput("Work completed " + cycle, "Decisions " + random.nextInt(1000),
                "chatmap://art/" + cycle, "No problems", "No user decisions required", "Continue to next cycle",
                "successor-task-" + cycle, "Context " + cycle, "Tools", "Constraints", "DoD", "If blocked, escalate");
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    file.toFile().deleteOnExit();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                try {
                    Files.delete(dir);
                } catch (IOException e) {
                    dir.toFile().deleteOnExit();
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class LatencyTracker {
        private final String name;
        private int count;
        private long maxNanos;
        private int bucketUnder100Us;
        private int bucket100To500Us;
        private int bucket500To1000Us;
        private int bucket1To5Ms;
        private int bucket5MsPlus;

        LatencyTracker(String name) {
            this.name = name;
        }

        void record(long durationNanos) {
            count++;
            if (durationNanos > maxNanos) {
                maxNanos = durationNanos;
            }
            long micros = durationNanos / 1000L;
            if (micros < 100L) {
                bucketUnder100Us++;
            } else if (micros < 500L) {
                bucket100To500Us++;
            } else if (micros < 1000L) {
                bucket500To1000Us++;
            } else if (micros < 5000L) {
                bucket1To5Ms++;
            } else {
                bucket5MsPlus++;
            }
        }
    }

    private record CurrentCycleExecution(long sessionId, WorkerAssignment nextAssignment, SampledRecord snapshot) {
    }

    private record SampledRecord(int cycle, WorkerAssignment assignment, WorkerSession session,
            List<WorkerLifecycleEvent> events, List<WorkerArtifact> artifacts, WorkerSemanticHandoff handoff,
            long successorAssignmentId) {
    }
}
