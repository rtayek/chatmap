package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.command.CommandResult;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.domain.HandoffTask;
import chatmap.infrastructure.handoff.FileSystemHandoffFileStore;

class HandoffInboxManagerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
    private static final HandoffFileStore FILE_STORE = new FileSystemHandoffFileStore();
    private static final String OUTPUT_WITH_FENCE = "before\n```\nsome code\n```\nafter";

    @TempDir
    Path dir;

    @Test
    void writeResultFileFencesAgentOutputThatContainsABacktickFence() throws IOException {
        Path archivedTaskFile = dir.resolve("task1.md");
        Files.writeString(archivedTaskFile, "body");
        HandoffTask task = new HandoffTask(archivedTaskFile, "chatmap", "claude", "feature-x", "body");
        CommandResult agentResult = new CommandResult(
                0, OUTPUT_WITH_FENCE, "", Duration.ofSeconds(1), false);

        Path resultPath = new HandoffInboxManager(FILE_STORE, CLOCK)
                .writeResultFile(archivedTaskFile, task, agentResult);

        String written = Files.readString(resultPath);
        assertFencedContentIsIntact(written, OUTPUT_WITH_FENCE);
    }

    @Test
    void writeFailureReportFencesAgentOutputThatContainsABacktickFence() throws IOException {
        Path taskFile = dir.resolve("task1.md");
        CommandResult agentResult = new CommandResult(
                1, OUTPUT_WITH_FENCE, "", Duration.ofSeconds(1), false);

        Path reportPath = new HandoffInboxManager(FILE_STORE, CLOCK)
                .writeFailureReport(taskFile, "chatmap", "agent crashed", agentResult, dir);

        String written = Files.readString(reportPath);
        assertFencedContentIsIntact(written, OUTPUT_WITH_FENCE);
    }

    private static void assertFencedContentIsIntact(String document, String content) {
        String fence = MarkdownFences.fenceFor(content);
        int openStart = document.indexOf(fence);
        int closeStart = document.indexOf(fence, openStart + fence.length());

        assertTrue(openStart >= 0, "opening fence must be present: " + document);
        assertTrue(closeStart > openStart, "closing fence must be present after the content: " + document);
        assertTrue(fence.length() > 3, "fence must be longer than the content's own ``` run");
    }
}
