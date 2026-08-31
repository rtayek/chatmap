package chatmap.infrastructure.importer;

import chatmap.application.model.ImportedChat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;

class ChatGptJsonImporterTest {

    private static final String importedAt = "2026-07-06T00:00:00Z";

    private Connection conn;

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void importsChatMetadataAndMapsRoles() {
        ImportedChat imported = new ChatGptJsonImporter().importJson(ChatGptJsonFixture.json, importedAt);

        assertEquals("ChatGPT Import Sample", imported.chat().title());
        assertEquals(Source.chatgptJson, imported.chat().source());
        assertEquals("2024-07-03T09:46:40Z", imported.chat().createdAt());
        assertEquals("2024-07-03T09:51:40Z", imported.chat().updatedAt());
        assertEquals(importedAt, imported.chat().importedAt());

        assertEquals(List.of(MessageRole.system, MessageRole.user, MessageRole.assistant, MessageRole.tool),
                imported.messages().stream().map(Message::role).toList());
    }

    @Test
    void preservesToolAuthorRole() {
        String toolConversation = """
                {
                  "title": "Tool call",
                  "mapping": {
                    "tool": {"message": {
                      "author": {"role": "tool"},
                      "content": {"parts": ["tool result"]}
                    }}
                  }
                }
                """;

        ImportedChat imported = new ChatGptJsonImporter().importJson(toolConversation, importedAt);

        assertEquals(MessageRole.tool, imported.messages().getFirst().role());
    }

    @Test
    void followsActiveBranchAndSkipsEditedAwayReplies() {
        // A regenerated reply: user1 has two assistant children; current_node points
        // at the second, so the first (assistantV1) is an edited-away branch.
        String branched = """
                {
                  "title": "Branched",
                  "current_node": "assistantV2",
                  "mapping": {
                    "root": {"parent": null},
                    "user1": {"parent": "root", "message":
                      {"author": {"role": "user"}, "create_time": 1,
                       "content": {"parts": ["question"]}}},
                    "assistantV1": {"parent": "user1", "message":
                      {"author": {"role": "assistant"}, "create_time": 2,
                       "content": {"parts": ["OLD answer"]}}},
                    "assistantV2": {"parent": "user1", "message":
                      {"author": {"role": "assistant"}, "create_time": 3,
                       "content": {"parts": ["NEW answer"]}}}
                  }
                }
                """;

        ImportedChat imported = new ChatGptJsonImporter().importJson(branched, importedAt);

        assertEquals(List.of("question", "NEW answer"),
                imported.messages().stream().map(Message::text).toList());
    }

    @Test
    void importAllReadsEveryConversationInAnArrayExport() {
        String arrayExport = """
                [
                  {"conversation_id":"first-id","title":"First","mapping":{"a":{"message":
                    {"author":{"role":"user"},"content":{"parts":["one"]}}}}},
                  {"id":"second-id","title":"Second","mapping":{"b":{"message":
                    {"author":{"role":"user"},"content":{"parts":["two"]}}}}}
                ]
                """;

        List<ImportedChat> all = new ChatGptJsonImporter().importAll(arrayExport, importedAt);

        assertEquals(List.of("First", "Second"),
                all.stream().map(imported -> imported.chat().title()).toList());
        assertEquals(List.of("first-id", "second-id"),
                all.stream().map(imported -> imported.chat().externalConversationId()).toList());
        assertEquals("one", all.get(0).messages().get(0).text());
        assertEquals("two", all.get(1).messages().get(0).text());

        // importJson stays single-conversation: it returns the first element.
        ImportedChat first = new ChatGptJsonImporter().importJson(arrayExport, importedAt);
        assertEquals("First", first.chat().title());
    }

    @Test
    void flattensMessagePartsAndPreservesRawJson() {
        ImportedChat imported = new ChatGptJsonImporter().importJson(ChatGptJsonFixture.json, importedAt);

        Message user = imported.messages().get(1);
        assertEquals("Please explain SQLite FTS5.\n\nKeep it practical.", user.text());
        // rawJson is now the Gson-canonical (compact) serialization of the message node.
        assertTrue(user.rawJson().contains("\"id\":\"userMessage\""));
        assertTrue(user.rawJson().contains("\"parts\":[\"Please explain SQLite FTS5.\",\"Keep it practical.\"]"));
    }

    @Test
    void preservesTimestampsAndLeavesMissingTimestampNull() {
        ImportedChat imported = new ChatGptJsonImporter().importJson(ChatGptJsonFixture.json, importedAt);

        assertEquals("2024-07-03T09:46:50Z", imported.messages().get(1).timestamp());
        assertNull(imported.messages().get(3).timestamp());
    }

    @Test
    void importedChatGptMessagesPersistAndCanBeSearchedWithFts() throws Exception {
        ImportedChat imported = new ChatGptJsonImporter().importJson(ChatGptJsonFixture.json, importedAt);
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);

        Chat storedChat = chats.insert(imported.chat());
        for (Message message : imported.messages()) {
            messages.insert(new Message(0, storedChat.id(), message.role(), message.text(),
                    message.sequence(), message.timestamp(), message.rawJson()));
        }

        List<Long> hits = messages.searchText("practical");

        assertEquals(1, hits.size());
        Message storedHit = messages.findByChat(storedChat.id()).stream()
                .filter(message -> message.id() == hits.get(0))
                .findFirst()
                .orElseThrow();
        assertEquals(MessageRole.user, storedHit.role());
        assertEquals("Please explain SQLite FTS5.\n\nKeep it practical.", storedHit.text());
        assertEquals(Source.chatgptJson, chats.findById(storedChat.id()).orElseThrow().source());
    }
}
