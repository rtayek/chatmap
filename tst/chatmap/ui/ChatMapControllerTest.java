package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.SearchService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.SearchRepository;

class ChatMapControllerTest {

    @TempDir
    Path tempDir;

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ChatMapController controller;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        controller = new ChatMapController(
                new ImportService(chats, messages),
                new ExportService(chats, messages),
                new SearchService(new SearchRepository(conn)));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void loadsAllChats() throws Exception {
        Chat first = insertChat("First", "first body");
        Chat second = insertChat("Second", "second body");

        ChatListState.Snapshot snapshot = controller.loadAllChats();

        assertEquals(List.of(first.id(), second.id()), snapshot.currentItems().stream()
                .map(result -> result.chatId())
                .toList());
        assertEquals(ChatListMode.allChats, snapshot.currentMode());
        assertEquals("Ready", snapshot.statusText());
    }

    @Test
    void searchesChats() throws Exception {
        Chat match = insertChat("Match", "ChatMap controller target");
        insertChat("Miss", "unrelated body");

        ChatListState.Snapshot snapshot = controller.searchChats("target");

        assertEquals(List.of(match.id()), snapshot.currentItems().stream()
                .map(result -> result.chatId())
                .toList());
        assertEquals(match.id(), snapshot.selectedChatId());
        assertEquals("1 match", snapshot.statusText());
    }

    @Test
    void importsFileAndRefreshesAllChats() throws Exception {
        Path input = tempDir.resolve("controller.txt");
        Files.writeString(input, "Imported through ChatMap controller");

        ChatListState.Snapshot snapshot = controller.importFile(input);

        assertEquals(1, snapshot.currentItems().size());
        assertEquals("controller.txt", snapshot.currentItems().getFirst().chat().title());
        assertEquals(snapshot.currentItems().getFirst().chatId(), snapshot.selectedChatId());
        assertEquals("Imported controller.txt", snapshot.statusText());
    }

    @Test
    void loadsHydratedChatDetails() throws Exception {
        Chat chat = insertChat("Details", "Detail message");

        ChatExportModel details = controller.loadChatDetails(chat.id()).orElseThrow();

        assertEquals(chat, details.chat());
        assertEquals(List.of("Detail message"), details.messages().stream().map(Message::text).toList());
    }

    @Test
    void exportsChatMarkdown() throws Exception {
        Chat chat = insertChat("Controller Export", "Exported message");
        Path output = tempDir.resolve("controller-export.md");

        assertTrue(controller.exportChatMarkdown(chat.id(), output));
        String markdown = Files.readString(output);
        assertTrue(markdown.contains("# Controller Export"));
        assertTrue(markdown.contains("Exported message"));
    }

    private Chat insertChat(String title, String text) throws Exception {
        Chat chat = chats.insert(new Chat(
                0, null, Source.plainText, title, null, null, "2026-07-08T00:00:00Z", false));
        messages.insert(new Message(0, chat.id(), "unknown", text, 0, null, null));
        return chat;
    }
}
