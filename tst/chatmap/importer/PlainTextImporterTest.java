package chatmap.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class PlainTextImporterTest {

    private static final String importedAt = "2026-07-06T00:00:00Z";

    private Connection conn;

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void textWithoutRolePrefixesCreatesOneUnknownMessage() {
        String text = "Plain text without transcript role markers.\n\nSecond paragraph.";
        ImportedChat imported = new PlainTextImporter().importText(text, importedAt);

        assertEquals("Plain text without transcript role markers.", imported.chat().title());
        assertEquals(chatmap.domain.Source.plainText, imported.chat().source());
        assertEquals(importedAt, imported.chat().importedAt());
        assertEquals(false, imported.chat().archived());
        assertEquals(null, imported.chat().projectId());

        assertEquals(1, imported.messages().size());
        Message message = imported.messages().getFirst();
        assertEquals(PlainTextImporter.unknownRole, message.role());
        assertEquals(text, message.text());
        assertEquals(0, message.sequence());
        assertEquals(null, message.timestamp());
        assertEquals(null, message.rawJson());
    }

    @Test
    void importsRolePrefixedTranscriptAsSeparateMessages() {
        String text = "  uSeR: First line\n\nSecond paragraph.\n"
                + " Assistant: Answer line\n\nMore detail.";

        ImportedChat imported = new PlainTextImporter().importText("Transcript", text, importedAt);

        assertEquals(List.of("user", "assistant"),
                imported.messages().stream().map(Message::role).toList());
        assertEquals(List.of("First line\n\nSecond paragraph.", "Answer line\n\nMore detail."),
                imported.messages().stream().map(Message::text).toList());
        assertEquals(List.of(0, 1),
                imported.messages().stream().map(Message::sequence).toList());
    }

    @Test
    void importedPlainTextPersistsAndCanBeSearchedWithFts() throws Exception {
        String text = Files.readString(Path.of("samples", "plainTextSample.txt"));
        ImportedChat imported = new PlainTextImporter().importText(text, importedAt);
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);

        Chat storedChat = chats.insert(imported.chat());
        Message importedMessage = imported.messages().getFirst();
        Message storedMessage = messages.insert(new Message(0, storedChat.id(), importedMessage.role(),
                importedMessage.text(), importedMessage.sequence(), importedMessage.timestamp(),
                importedMessage.rawJson()));

        assertEquals(storedChat, chats.findById(storedChat.id()).orElseThrow());
        assertEquals(List.of(storedMessage), messages.findByChat(storedChat.id()));
        assertEquals(List.of(storedMessage.id()), messages.searchText("ChatMap"));
    }
}
