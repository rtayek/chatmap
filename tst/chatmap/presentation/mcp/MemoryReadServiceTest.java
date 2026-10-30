package chatmap.presentation.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.app.ReadOnlyMcpRuntime;
import chatmap.application.service.MemoryReadService;
import chatmap.application.service.MemoryReadService.ChatMessagesResult;
import chatmap.application.service.MemoryReadService.WorkingContextResult;

class MemoryReadServiceTest {

    @Test
    void searchIsProjectScopedBoundedAndRejectsBlankQueries(@TempDir Path directory) throws Exception {
        McpSyntheticFixture.Fixture fixture = McpSyntheticFixture.create(directory);
        try (ReadOnlyMcpRuntime runtime = open(fixture)) {
            MemoryReadService reads = runtime.memoryReads();

            var sqlite = reads.searchChats("SQLite", null);
            var forbidden = reads.searchChats("FORBIDDEN-OTHER-PROJECT-CONTENT", null);

            assertEquals(1, sqlite.chats().size());
            assertEquals(McpSyntheticFixture.historyChatId, sqlite.chats().getFirst().chatId());
            assertEquals(0, forbidden.chats().size());
            assertThrows(IllegalArgumentException.class, () -> reads.searchChats(" ", null));
            assertThrows(IllegalArgumentException.class, () -> reads.searchChats("x", 11));
        }
    }

    @Test
    void messageReadsAreOrderedScopedAndReconstructOversizedUnicode(@TempDir Path directory) throws Exception {
        McpSyntheticFixture.Fixture fixture = McpSyntheticFixture.create(directory);
        try (ReadOnlyMcpRuntime runtime = open(fixture)) {
            MemoryReadService reads = runtime.memoryReads();
            ChatMessagesResult ordered = reads.readChatMessages(
                    McpSyntheticFixture.currentChatId, 0, 2, null);
            ChatMessagesResult forbidden = reads.readChatMessages(
                    McpSyntheticFixture.otherChatId, 0, 5, null);

            assertEquals(McpSyntheticFixture.currentTaskMessageId,
                    ordered.messages().get(0).messageId());
            assertEquals(McpSyntheticFixture.deferredMessageId,
                    ordered.messages().get(1).messageId());
            assertEquals("outOfScope", forbidden.outcome());
            assertTrue(forbidden.messages().isEmpty());

            StringBuilder reconstructed = new StringBuilder();
            ChatMessagesResult part = reads.readChatMessages(
                    McpSyntheticFixture.currentChatId, 2, 1, null);
            while (true) {
                reconstructed.append(part.messages().getFirst().text());
                if (part.nextCursor() == null) {
                    break;
                }
                part = reads.readChatMessages(McpSyntheticFixture.currentChatId, null, 1, part.nextCursor());
            }
            assertEquals(fixture.oversizedText(), reconstructed.toString());
        }
    }

    @Test
    void messageCursorDetectsAChangedSource(@TempDir Path directory) throws Exception {
        McpSyntheticFixture.Fixture fixture = McpSyntheticFixture.create(directory);
        try (ReadOnlyMcpRuntime runtime = open(fixture)) {
            MemoryReadService reads = runtime.memoryReads();
            ChatMessagesResult first = reads.readChatMessages(
                    McpSyntheticFixture.currentChatId, 2, 1, null);
            try (Connection writer = DriverManager.getConnection("jdbc:sqlite:" + fixture.database());
                    PreparedStatement statement = writer.prepareStatement(
                            "UPDATE messages SET text = text || ' changed' WHERE id = ?")) {
                statement.setLong(1, McpSyntheticFixture.oversizedMessageId);
                statement.executeUpdate();
            }

            ChatMessagesResult changed = reads.readChatMessages(
                    McpSyntheticFixture.currentChatId, null, 1, first.nextCursor());

            assertEquals("changedSource", changed.outcome());
            assertTrue(changed.messages().isEmpty());
        }
    }

    @Test
    void workingContextChunksByUnicodeAndDetectsChanges(@TempDir Path directory) throws Exception {
        McpSyntheticFixture.Fixture fixture = McpSyntheticFixture.create(directory);
        try (ReadOnlyMcpRuntime runtime = open(fixture)) {
            MemoryReadService reads = runtime.memoryReads();
            WorkingContextResult first = reads.readWorkingContext(0, 20, null);
            WorkingContextResult second = reads.readWorkingContext(null, 20, first.nextCursor());

            assertEquals("ok", first.outcome());
            assertEquals(20, first.text().codePointCount(0, first.text().length()));
            assertFalse(second.text().isEmpty());

            Files.writeString(fixture.workingContext(), McpSyntheticFixture.workingContextText() + "changed\n",
                    StandardCharsets.UTF_8);
            WorkingContextResult changed = reads.readWorkingContext(null, 20, second.nextCursor());
            assertEquals("changedSource", changed.outcome());
        }
    }

    @Test
    void missingProjectFailsAtStartup(@TempDir Path directory) throws Exception {
        McpSyntheticFixture.Fixture fixture = McpSyntheticFixture.create(directory);

        assertThrows(java.sql.SQLException.class,
                () -> ReadOnlyMcpRuntime.open(fixture.home(), 999, fixture.projectRoot()));
    }

    private static ReadOnlyMcpRuntime open(McpSyntheticFixture.Fixture fixture) throws Exception {
        return ReadOnlyMcpRuntime.open(
                fixture.home(), McpSyntheticFixture.selectedProjectId, fixture.projectRoot());
    }
}
