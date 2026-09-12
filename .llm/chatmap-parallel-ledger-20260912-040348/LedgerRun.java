import java.nio.file.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import chatmap.app.bootstrap.ChatMapPaths;
import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.domain.*;
import chatmap.infrastructure.persistence.sqlite.*;

public class LedgerRun {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path home = root.resolve("home");
        var paths = ChatMapPaths.parse(new String[]{"--home", home.toString()}).paths();
        if (!paths.databasePath().equals(home.resolve("chatmap.db"))) throw new IllegalStateException("Home mismatch");
        String command = args[1];
        boolean init = command.equals("init");
        if (init) {
            Files.createDirectory(home);
        } else if (!Files.isRegularFile(paths.databasePath())) {
            throw new IllegalStateException("Existing isolated database required");
        }
        Database database = new Database("jdbc:sqlite:" + paths.databasePath());
        try (Connection connection = init ? database.openAndInitialize() : database.open()) {
            var service = new WorkerLifecycleService(new WorkerLifecycleRepository(connection),
                    new TransactionRunner(connection), Clock.systemUTC());
            switch (command) {
                case "init" -> {
                    var assignment = service.createAssignment(input(root, "coordinator", "run-contract.md"));
                    var session = service.createSession(assignment.id(), "codex-native:/root");
                    service.transition(session.id(), WorkerLifecycleState.WORKING);
                    attach(service, session.id(), root.resolve("run-contract.md"));
                    System.out.println("coordinatorSession=" + session.id());
                }
                case "worker" -> {
                    var assignment = service.createSuccessorAssignment(1, input(root, args[2], args[3]));
                    var session = service.createSession(assignment.id(), args[4]);
                    service.transition(session.id(), WorkerLifecycleState.WORKING);
                    attach(service, session.id(), root.resolve(args[3]));
                    System.out.println("workerSession=" + session.id() + " identity=" + session.workerIdentity());
                }
                case "finish" -> {
                    long sessionId = Long.parseLong(args[2]);
                    attach(service, sessionId, root.resolve(args[3]));
                    service.transition(sessionId, WorkerLifecycleState.valueOf(args[4]));
                    System.out.println("finishedSession=" + sessionId + " state=" + args[4]);
                }
                case "attach" -> attach(service, Long.parseLong(args[2]), root.resolve(args[3]));
                case "synthesis" -> {
                    for (long id = 2; id <= 4; id++) {
                        var record = service.record(id);
                        if (record.session().lifecycleState() != WorkerLifecycleState.COMPLETED
                                && record.session().lifecycleState() != WorkerLifecycleState.FAILED
                                && record.session().lifecycleState() != WorkerLifecycleState.CANCELLED)
                            throw new IllegalStateException("Worker not terminal: " + id);
                    }
                    var assignment = service.createSuccessorAssignment(1, input(root, "synthesis", "synthesis-contract.md"));
                    var session = service.createSession(assignment.id(), "codex-native:/root:synthesis");
                    service.transition(session.id(), WorkerLifecycleState.WORKING);
                    attach(service, session.id(), root.resolve("synthesis-contract.md"));
                    System.out.println("synthesisSession=" + session.id());
                }
                case "complete" -> {
                    if (service.record(5).session().lifecycleState() != WorkerLifecycleState.COMPLETED)
                        throw new IllegalStateException("Synthesis must be stored and complete first");
                    attach(service, 1, root.resolve("synthesis.md"));
                    service.transition(1, WorkerLifecycleState.COMPLETED);
                    System.out.println("coordinatorCompleted");
                }
                case "dump", "verify" -> {
                    var chain = service.chainFrom(1);
                    System.out.println("database=" + paths.databasePath());
                    System.out.println("chainFrom(1)=" + chain.records().size() + " sessions");
                    for (var record : chain.records()) {
                        System.out.println("SESSION " + record.session());
                        System.out.println("ASSIGNMENT " + record.assignment());
                        for (var event : record.events()) System.out.println("EVENT " + event);
                        for (var artifact : record.artifacts()) System.out.println("ARTIFACT " + artifact);
                    }
                    if (command.equals("verify")) {
                        require(chain.records().size() == 5, "five sessions");
                        for (long id = 1; id <= 5; id++) {
                            var record = service.record(id);
                            require(record.session().lifecycleState() == WorkerLifecycleState.COMPLETED, "completed " + id);
                            require(record.events().size() == 2, "two transitions " + id);
                            require(record.events().get(0).fromState() == WorkerLifecycleState.QUEUED
                                && record.events().get(0).toState() == WorkerLifecycleState.WORKING
                                && record.events().get(1).fromState() == WorkerLifecycleState.WORKING
                                && record.events().get(1).toState() == WorkerLifecycleState.COMPLETED, "event sequence " + id);
                            require(record.artifacts().size() >= 2, "artifacts " + id);
                            for (var artifact : record.artifacts()) require(Files.isRegularFile(Path.of(artifact.location())), "artifact exists");
                            if (id > 1) require(Long.valueOf(1).equals(record.assignment().predecessorSessionId()), "predecessor " + id);
                        }
                        require(java.util.stream.LongStream.rangeClosed(2, 4).mapToObj(id -> {
                            try { return service.record(id).session().workerIdentity(); }
                            catch (Exception e) { throw new RuntimeException(e); }
                        }).distinct().count() == 3, "distinct worker identities");
                        String synthesis = service.record(5).assignment().contextAndFiles();
                        for (long id = 2; id <= 4; id++) require(synthesis.contains("session=" + id), "synthesis reference " + id);
                        require(service.record(5).events().get(1).createdAt().compareTo(service.record(1).events().get(1).createdAt()) <= 0,
                                "synthesis completed before coordinator");
                        try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA integrity_check")) {
                            require(result.next() && result.getString(1).equals("ok"), "integrity_check");
                        }
                        System.out.println("PASS: reopened database, five completed sessions, siblings, unique workers, transitions, files, synthesis references/order, integrity");
                    }
                }
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            }
        }
    }

    static WorkerAssignmentInput input(Path root, String role, String file) throws Exception {
        return new WorkerAssignmentInput("CM-PARALLEL-LEDGER-01: " + role, Files.readString(root.resolve(file)),
            "Workers: read-only Git/shell and native agent messages. Coordinator: native agents and this manual lifecycle recorder.",
            "Pinned commit 1bb642d390059e955a548148a4bb3b546f1c3d2f. No worker writes/builds/formatting. No .chatmap-local reads. No schema/dependency/scheduler changes.",
            "Return scope, verified facts with pinned file:line evidence, recommendations, uncertainties/disagreements, next action, start/end UTC. Coordinator preserves each report and verifies reopen.",
            "Report blockers or failure with partial evidence to /root; do not repair or extend scope.");
    }

    static void attach(WorkerLifecycleService service, long session, Path file) throws Exception {
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Missing artifact " + file);
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        service.addArtifact(session, file.getFileName().toString(), file.toString(), "SHA-256=" + hash + "; coordinator persisted external evidence");
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Verification failed: " + message);
    }
}
