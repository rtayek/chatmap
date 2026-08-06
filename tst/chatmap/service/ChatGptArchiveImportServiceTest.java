package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ChatGptArchiveImporter;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class ChatGptArchiveImportServiceTest {

    @TempDir
    Path tempDir;

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ChatGptArchiveImportService service;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        ImportService importService = new ImportService(chats, messages);
        service = new ChatGptArchiveImportService(importService, chats);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void importsMultipleConversationsFromOneZip() throws Exception {
        Path zip = zip("multi.zip", Map.of("conversations-000.json",
                "[" + conversation("a", "A", "A1") + "," + conversation("b", "B", "B1") + "]"));

        ChatGptArchiveImportService.BulkImportResult result = service.importArchive(zip);

        assertEquals(2, result.conversationsDiscovered());
        assertEquals(2, result.inserted());
        assertEquals(0, result.failed());
        assertEquals(List.of("a", "b"), chats.findAll().stream().map(Chat::externalConversationId).toList());
    }

    @Test
    void importsNumberedShardsInLexicographicOrder() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("conversations-001.json", "[" + conversation("second", "Second", "S") + "]");
        entries.put("conversations-000.json", "[" + conversation("first", "First", "F") + "]");
        Path zip = zip("shards.zip", entries);

        ChatGptArchiveImportService.BulkImportResult result = service.importArchive(zip);

        assertEquals(List.of("conversations-000.json", "conversations-001.json"),
                result.conversationEntries());
        assertEquals(List.of("first", "second"),
                chats.findAll().stream().map(Chat::externalConversationId).toList());
    }

    @Test
    void acceptsLegacySingleConversationsJson() throws Exception {
        Path zip = zip("legacy.zip", Map.of("conversations.json",
                "[" + conversation("legacy", "Legacy", "old") + "]"));

        ChatGptArchiveImportService.BulkImportResult result = service.importArchive(zip);

        assertEquals(List.of("conversations.json"), result.conversationEntries());
        assertEquals(1, result.inserted());
        assertEquals("legacy", chats.findAll().getFirst().externalConversationId());
    }

    @Test
    void reconstructsOnlyActivePathFromCurrentNode() throws Exception {
        Path zip = zip("branch.zip", Map.of("conversations-000.json", "[" + branchingConversation() + "]"));

        service.importArchive(zip);

        Chat chat = chats.findAll().getFirst();
        assertEquals("branch", chat.externalConversationId());
        assertEquals(Source.chatgptJson, chat.source());
        assertEquals(List.of("root", "active answer"),
                messages.findByChat(chat.id()).stream().map(Message::text).toList());
    }

    @Test
    void storesStableExternalIdsAndSourceUri() throws Exception {
        Path zip = zip("identity.zip", Map.of("conversations-000.json",
                "[" + conversation("stable-id", "Title", "text") + "]"));

        service.importArchive(zip);

        Chat chat = chats.findAll().getFirst();
        assertEquals("stable-id", chat.externalConversationId());
        assertTrue(chat.sourceUri().contains("identity.zip!conversations-000.json#conversationId=stable-id"));
        assertTrue(chat.contentHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void unchangedReimportCreatesNoDuplicateChatOrMessages() throws Exception {
        Path zip = zip("same.zip", Map.of("conversations-000.json",
                "[" + conversation("same", "Same", "body") + "]"));

        ChatGptArchiveImportService.BulkImportResult first = service.importArchive(zip);
        ChatGptArchiveImportService.BulkImportResult second = service.importArchive(zip);

        assertEquals(1, first.inserted());
        assertEquals(1, second.unchanged());
        assertEquals(1, chats.findAll().size());
        assertEquals(1, messages.findByChat(chats.findAll().getFirst().id()).size());
    }

    @Test
    void changedContentUpdatesOneChatAndReplacesMessages() throws Exception {
        Path zip = zip("refresh.zip", Map.of("conversations-000.json",
                "[" + conversation("refresh", "Refresh", "old text") + "]"));
        service.importArchive(zip);
        Chat chat = chats.findAll().getFirst();
        String originalImportedAt = chat.importedAt();

        Path changed = zip("refresh-changed.zip", Map.of("conversations-000.json",
                "[" + conversation("refresh", "Refresh", "new text") + "]"));
        ChatGptArchiveImportService.BulkImportResult result = service.importArchive(changed);

        Chat refreshed = chats.findAll().getFirst();
        assertEquals(1, result.updated());
        assertEquals(chat.id(), refreshed.id());
        assertEquals(originalImportedAt, refreshed.importedAt());
        assertEquals(List.of("new text"), messages.findByChat(refreshed.id()).stream().map(Message::text).toList());
        assertTrue(messages.searchText("old").isEmpty());
        assertEquals(1, messages.searchText("new").size());
    }

    @Test
    void malformedConversationDoesNotPreventValidImport() throws Exception {
        Path zip = zip("malformed.zip", Map.of("conversations-000.json",
                "[" + conversation("valid", "Valid", "body") + ",42]"));

        ChatGptArchiveImportService.BulkImportResult result = service.importArchive(zip);

        assertEquals(2, result.conversationsDiscovered());
        assertEquals(1, result.inserted());
        assertEquals(1, result.failed());
        assertEquals("valid", chats.findAll().getFirst().externalConversationId());
    }

    @Test
    void missingConversationEntriesIsClearError() throws Exception {
        Path zip = zip("missing.zip", Map.of("user.json", "{}"));

        IOException e = assertThrows(IOException.class, () -> service.importArchive(zip));

        assertTrue(e.getMessage().contains("No conversations.json or conversations-*.json entries"));
    }

    @Test
    void unsupportedOrEmptyContentIsCountedAndSkipped() throws Exception {
        Path zip = zip("unsupported.zip", Map.of("conversations-000.json",
                "[" + unsupportedConversation("unsupported") + "]"));

        ChatGptArchiveImportService.BulkImportResult result = service.importArchive(zip);

        assertEquals(1, result.conversationsDiscovered());
        assertEquals(1, result.skipped());
        assertEquals(1, result.unsupportedContentParts());
        assertEquals(1, result.unsupportedContentCategories().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(result.unsupportedContentCategories().keySet().stream()
                .anyMatch(key -> key.contains("object_part:multimodal_text")));
        assertTrue(chats.findAll().isEmpty());
    }

    @Test
    void fallbackUsesLatestLeafWhenCurrentNodeIsMissing() throws Exception {
        Path zip = zip("fallback.zip", Map.of("conversations-000.json", "[" + noCurrentNodeConversation() + "]"));

        service.importArchive(zip);

        Chat chat = chats.findAll().getFirst();
        assertEquals(List.of("later"), messages.findByChat(chat.id()).stream().map(Message::text).toList());
    }

    @Test
    void codexJsonIsInspectedButNotImportedAsNormalConversationShard() throws Exception {
        Path zip = zip("codex.zip", Map.of(
                "conversations-000.json", "[" + conversation("normal", "Normal", "body") + "]",
                "codex.json", "[{\"id\":\"codex-1\",\"title\":\"Codex\",\"turns\":[]}]"));

        ChatGptArchiveImportService.BulkImportResult result = service.importArchive(zip);

        assertEquals(1, result.inserted());
        assertTrue(result.codexInspection().present());
        assertTrue(result.codexInspection().codexTurnSchema());
        assertEquals(1, chats.findAll().size());
    }

    @Test
    void importerReportsCodexShapeWithoutReadingMessages() throws Exception {
        Path zip = zip("codex-only-shape.zip", Map.of(
                "conversations-000.json", "[]",
                "codex.json", "[{\"id\":\"codex-1\",\"title\":\"Codex\",\"turns\":[]}]"));

        ChatGptArchiveImporter.CodexInspection inspection =
                new ChatGptArchiveImporter().inspectCodex(zip);

        assertEquals("array", inspection.topLevelType());
        assertEquals(1, inspection.recordCount());
        assertTrue(inspection.codexTurnSchema());
        assertEquals(false, inspection.normalConversationSchema());
    }

    private Path zip(String name, Map<String, String> entries) throws Exception {
        Path zip = tempDir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(java.nio.file.Files.newOutputStream(zip))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private static String conversation(String id, String title, String text) {
        return """
                {
                  "conversation_id":"%s",
                  "title":"%s",
                  "create_time":1000.0,
                  "update_time":1001.0,
                  "current_node":"m1",
                  "mapping":{
                    "root":{"id":"root","parent":null,"message":null},
                    "m1":{"id":"m1","parent":"root","message":%s}
                  }
                }
                """.formatted(id, title, message("m1", "user", text, 1000.0));
    }

    private static String branchingConversation() {
        return """
                {
                  "conversation_id":"branch",
                  "title":"Branch",
                  "create_time":1000.0,
                  "update_time":1003.0,
                  "current_node":"active",
                  "mapping":{
                    "root":{"id":"root","parent":null,"message":null},
                    "q":{"id":"q","parent":"root","message":%s},
                    "active":{"id":"active","parent":"q","message":%s},
                    "alternate":{"id":"alternate","parent":"q","message":%s}
                  }
                }
                """.formatted(
                message("q", "user", "root", 1000.0),
                message("active", "assistant", "active answer", 1001.0),
                message("alternate", "assistant", "alternate answer", 1002.0));
    }

    private static String noCurrentNodeConversation() {
        return """
                {
                  "conversation_id":"fallback",
                  "title":"Fallback",
                  "create_time":1000.0,
                  "update_time":1003.0,
                  "mapping":{
                    "root":{"id":"root","parent":null,"message":null},
                    "early":{"id":"early","parent":"root","message":%s},
                    "later":{"id":"later","parent":"root","message":%s}
                  }
                }
                """.formatted(
                message("early", "user", "early", 1000.0),
                message("later", "user", "later", 1002.0));
    }

    private static String unsupportedConversation(String id) {
        return """
                {
                  "conversation_id":"%s",
                  "title":"Unsupported",
                  "create_time":1000.0,
                  "update_time":1001.0,
                  "current_node":"m1",
                  "mapping":{
                    "root":{"id":"root","parent":null,"message":null},
                    "m1":{"id":"m1","parent":"root","message":{
                      "id":"m1",
                      "author":{"role":"user"},
                      "create_time":1000.0,
                      "content":{"content_type":"multimodal_text","parts":[{"asset_pointer":"file-1"}]}
                    }}
                  }
                }
                """.formatted(id);
    }

    private static String message(String id, String role, String text, double createTime) {
        return """
                {
                  "id":"%s",
                  "author":{"role":"%s"},
                  "create_time":%s,
                  "content":{"content_type":"text","parts":["%s"]}
                }
                """.formatted(id, role, createTime, text);
    }
}
