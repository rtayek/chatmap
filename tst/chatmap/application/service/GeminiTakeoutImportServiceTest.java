package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Source;
import chatmap.infrastructure.importer.GeminiTakeoutFixture;
import chatmap.infrastructure.importer.GeminiTakeoutImporter;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;

class GeminiTakeoutImportServiceTest {
    @BeforeEach
    void openDatabase() throws Exception {
        connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(connection);
        messages = new MessageRepository(connection);
        service = new GeminiTakeoutImportService(new GeminiTakeoutImporter(), new ImportService(chats, messages));
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    void repeatedImportFromAnotherDirectoryRetainsOneChatAndItsMessages() throws Exception {
        Path first = tempDir.resolve("first");
        Path second = tempDir.resolve("second");
        GeminiTakeoutFixture.write(first, "1786837272");
        GeminiTakeoutFixture.write(second, "1786837272");

        assertEquals(1, service.importDirectory(first).inserted());
        long id = chats.findAll().getFirst().id();
        assertEquals(1, service.importDirectory(second).unchanged());
        assertEquals(1, chats.findAll().size());
        assertEquals(id, chats.findAll().getFirst().id());
        assertEquals(16, messages.findByChat(id).size());
    }

    @Test
    void changedContentUpdatesExistingChatAndSearchIndex() throws Exception {
        Path file = GeminiTakeoutFixture.write(tempDir, "1786837272");
        service.importDirectory(tempDir);
        var original = chats.findAll().getFirst();
        Files.writeString(file, Files.readString(file).replace("Sample question 0.", "Replacement aardvark."));

        assertEquals(1, service.importDirectory(tempDir).updated());
        assertEquals(1, chats.findAll().size());
        assertEquals(original.id(), chats.findAll().getFirst().id());
        assertEquals(original.importedAt(), chats.findAll().getFirst().importedAt());
        assertEquals(16, messages.findByChat(original.id()).size());
        assertEquals("Replacement aardvark.", messages.findByChat(original.id()).getFirst().text());
        assertEquals(1, messages.searchText("aardvark").size());
    }

    @Test
    void malformedFilePreventsAnyPersistence() throws Exception {
        GeminiTakeoutFixture.write(tempDir, "1786837272");
        Path invalid = GeminiTakeoutFixture.write(tempDir, "1786837273");
        Files.writeString(invalid, "{}");

        assertThrows(IOException.class, () -> service.importDirectory(tempDir));
        assertTrue(chats.findAll().isEmpty());
    }

    @Test
    void emptyConversationDoesNotErasePreviouslyImportedMessages() throws Exception {
        Path file = GeminiTakeoutFixture.write(tempDir, "1786837272");
        service.importDirectory(tempDir);
        Files.writeString(file, "{\"conversation_turns\":[]}");

        assertEquals(1, service.importDirectory(tempDir).skipped());
        assertEquals(16, messages.findByChat(chats.findAll().getFirst().id()).size());
    }

    @Test
    void failedMessageInsertionRollsBackOnlyThatConversationAndReportsFailure() throws Exception {
        GeminiTakeoutFixture.write(tempDir, "1786837272");
        GeminiTakeoutFixture.write(tempDir, "1786837273");
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER failGeminiMessage BEFORE INSERT ON messages
                    WHEN NEW.text = 'Sample answer 3.'
                    BEGIN SELECT RAISE(ABORT, 'test message failure'); END
                    """);
        }

        var result = service.importDirectory(tempDir);

        assertEquals(2, result.discovered());
        assertEquals(1, result.inserted());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().getFirst().contains("1786837272"));
        assertTrue(chats.findByExternalIdentity(Source.geminiTakeoutJson, "1786837272").isEmpty());
        assertEquals(1, chats.findAll().size());
        assertEquals(2, messages.findByChat(chats.findAll().getFirst().id()).size());
        assertTrue(messages.searchText("Sample").isEmpty());
    }

    @TempDir
    Path tempDir;
    private Connection connection;
    private ChatRepository chats;
    private MessageRepository messages;
    private GeminiTakeoutImportService service;
}
