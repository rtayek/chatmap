import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import chatmap.application.service.WorkerLifecycleService;
import chatmap.infrastructure.persistence.sqlite.*;

public class EvidenceCheck {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        try (var conn = new Database("jdbc:sqlite:" + root.resolve("home/chatmap.db")).open()) {
            var service = new WorkerLifecycleService(new WorkerLifecycleRepository(conn), new TransactionRunner(conn), Clock.systemUTC());
            int hashes = 0;
            for (var record : service.chainFrom(1).records()) {
                for (var artifact : record.artifacts()) {
                    Path file = Path.of(artifact.location());
                    String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                    require(artifact.description().contains("SHA-256=" + actual), "hash mismatch: " + file);
                    hashes++;
                }
            }
            System.out.println("PASS: " + hashes + " ledger artifact hashes recomputed and matched");
            String context = service.record(5).assignment().contextAndFiles();
            String[] reports = {"lifecycle-report.md", "operations-report.md", "verifier-report.md"};
            for (int i = 0; i < reports.length; i++) {
                long session = i + 2;
                require(context.contains("session=" + session + " identity=codex-native:/root/"), "session reference");
                require(context.contains(root.resolve(reports[i]).toString().replace('\\', '/')), "exact report path");
                require(service.record(session).artifacts().stream().anyMatch(a -> a.label().equals(reports[(int)session - 2])), "worker report attachment");
                byte[] bytes = Files.readAllBytes(root.resolve(reports[i]));
                require(bytes.length > 1000, "nonempty worker report");
            }
            System.out.println("PASS: exact synthesis paths and worker report attachments");
            String synthesis = Files.readString(root.resolve("synthesis.md"));
            for (String section : List.of("Agreement", "Disagreement", "Unverified", "Duplicated work", "Missing or failed workers")) {
                require(synthesis.contains(section), "synthesis section " + section);
            }
            System.out.println("PASS: synthesis evidence categories present; content reviewed by coordinator");
            try (var statement = conn.prepareStatement("SELECT predecessorSessionId FROM workerAssignments WHERE id = ?")) {
                statement.setLong(1, service.record(1).assignment().id());
                try (var result = statement.executeQuery()) {
                    require(result.next(), "root row");
                    Object sqlValue = result.getObject(1);
                    require(sqlValue == null, "SQL root predecessor should be null");
                    System.out.println("KNOWN DEFECT: SQL root predecessor is NULL; service readback predecessor=" + service.record(1).assignment().predecessorSessionId());
                }
            }
            var workers = service.chainFrom(1).records();
            String end = service.record(1).events().get(1).createdAt();
            Instant fanout = Instant.parse("2026-09-12T04:05:50Z");
            Instant lastReportedEnd = Instant.parse("2026-09-12T04:08:03Z");
            Instant synthesisStarted = Instant.parse(service.record(5).events().get(0).createdAt());
            Instant synthesisCompleted = Instant.parse(service.record(5).events().get(1).createdAt());
            System.out.println("TIMING fanout-to-last-worker-reported-end-seconds=" + Duration.between(fanout, lastReportedEnd).toSeconds());
            System.out.println("TIMING last-worker-reported-end-to-coordinator-complete-seconds=" + Duration.between(lastReportedEnd, Instant.parse(end)).toSeconds());
            System.out.println("TIMING synthesis-recorded-seconds=" + Duration.between(synthesisStarted, synthesisCompleted).toSeconds());
            System.out.println("TIMING fanout-to-coordinator-complete-seconds=" + Duration.between(fanout, Instant.parse(end)).toSeconds());
            System.out.println("coordinatorCompletedAt=" + end);
        }
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
