package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.importer.ImportedChat;
import chatmap.service.ImportService.Outcome;
import chatmap.service.ImportService.PersistResult;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.TagRepository;

class ImportServiceTest {

    @TempDir
    Path tempDir;

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ProjectRepository projects;
    private TagRepository tags;
    private ImportService importService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        projects = new ProjectRepository(conn);
        tags = new TagRepository(conn);
        importService = new ImportService(chats, messages);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void importsPlainTextFile() throws Exception {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Plain text import body");

        Chat chat = importService.importFile(file);

        assertEquals("notes.txt", chat.title());
        assertEquals(Source.plainText, chat.source());
        assertEquals(List.of("Plain text import body"), messages.findByChat(chat.id()).stream().map(Message::text).toList());
    }

    @Test
    void importsMarkdownFile() throws Exception {
        Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "# Markdown Title\n\nMarkdown body");

        Chat chat = importService.importFile(file);

        assertEquals("Markdown Title", chat.title());
        assertEquals(Source.markdown, chat.source());
        assertEquals(List.of(chat), chats.findAll());
    }

    @Test
    void reimportingIdenticalPlainTextFileDoesNotDuplicate() throws Exception {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Plain text import body");

        Chat first = importService.importFile(file);
        Chat second = importService.importFile(file);

        assertEquals(first.id(), second.id());
        assertEquals(1, chats.findAll().size());
    }

    @Test
    void reimportingIdenticalMarkdownFileDoesNotDuplicate() throws Exception {
        Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "# Markdown Title\n\nMarkdown body");

        Chat first = importService.importFile(file);
        Chat second = importService.importFile(file);

        assertEquals(first.id(), second.id());
        assertEquals(1, chats.findAll().size());
    }

    @Test
    void providerImportStoresSourceAndExternalIdentity() throws Exception {
        PersistResult result = importService.persist(providerChat(
                Source.chatGptWeb, "abc123", "https://chatgpt.com/c/abc123",
                "Provider title", List.of("hello")));

        assertEquals(Outcome.inserted, result.outcome());
        Chat stored = chats.findById(result.chat().id()).orElseThrow();
        assertEquals(Source.chatGptWeb, stored.source());
        assertEquals("abc123", stored.externalConversationId());
        assertEquals("https://chatgpt.com/c/abc123", stored.sourceUri());
        assertTrue(stored.contentHash().matches("[0-9a-f]{64}"));
        assertEquals(1, messages.findByChat(stored.id()).size());
    }

    @Test
    void unchangedProviderConversationDoesNotInsertDuplicate() throws Exception {
        ImportedChat imported = providerChat(Source.claudeWeb, "claude-1",
                "https://claude.ai/chat/claude-1", "Same title", List.of("same text"));

        PersistResult first = importService.persist(imported);
        PersistResult second = importService.persist(imported);

        assertEquals(Outcome.inserted, first.outcome());
        assertEquals(Outcome.unchanged, second.outcome());
        assertEquals(first.chat().id(), second.chat().id());
        assertEquals(1, chats.findAll().size());
        assertEquals(List.of("same text"), messageTexts(first.chat().id()));
    }

    @Test
    void titleOnlyProviderRefreshKeepsMessagesAndReportsUnchanged() throws Exception {
        PersistResult first = importService.persist(providerChat(Source.claudeWeb, "claude-title",
                "https://claude.ai/chat/claude-title", "Old source title", List.of("same text")));
        List<Message> beforeMessages = messages.findByChat(first.chat().id());

        PersistResult second = importService.persist(providerChat(Source.claudeWeb, "claude-title",
                "https://claude.ai/chat/claude-title", "New source title", List.of("same text")));

        Chat stored = chats.findById(first.chat().id()).orElseThrow();
        assertEquals(Outcome.unchanged, second.outcome());
        assertEquals(first.chat().id(), second.chat().id());
        assertEquals("New source title", stored.title());
        assertEquals(beforeMessages, messages.findByChat(first.chat().id()));
    }

    @Test
    void changedProviderConversationRefreshesTitleMessagesAndPreservesOrganizingState() throws Exception {
        Chat first = importService.persist(providerChat(Source.claudeWeb, "claude-2",
                "https://claude.ai/chat/claude-2", "Source title", List.of("old alpha"))).chat();
        Project project = projects.insert(new Project(0, "Project", null, "2026-01-01T00:00:00Z",
                "2026-01-01T00:00:00Z"));
        Tag tag = tags.insert(new Tag(0, "important"));
        chats.updateTitle(first.id(), "Local title");
        chats.assignProject(first.id(), project.id());
        chats.setArchived(first.id(), true);
        tags.assignToChat(first.id(), tag.id());
        String originalImportedAt = chats.findById(first.id()).orElseThrow().importedAt();

        PersistResult refreshed = importService.persist(providerChat(Source.claudeWeb, "claude-2",
                "https://claude.ai/chat/claude-2", "Changed source title", List.of("new beta")));

        assertEquals(Outcome.updated, refreshed.outcome());
        assertEquals(first.id(), refreshed.chat().id());
        Chat stored = chats.findById(first.id()).orElseThrow();
        assertEquals("Changed source title", stored.title());
        assertEquals(project.id(), stored.projectId());
        assertTrue(stored.archived());
        assertEquals(originalImportedAt, stored.importedAt());
        assertEquals(List.of(tag), tags.findByChat(first.id()));
        assertEquals(List.of("new beta"), messageTexts(first.id()));
        assertTrue(messages.searchText("alpha").isEmpty());
        assertEquals(1, messages.searchText("beta").size());
    }

    @Test
    void identicalTitlesRemainDistinctAcrossProvidersAndProviderIds() throws Exception {
        importService.persist(providerChat(Source.claudeWeb, "same-title-1",
                "https://claude.ai/chat/same-title-1", "Repeated", List.of("a")));
        importService.persist(providerChat(Source.chatGptWeb, "same-title-1",
                "https://chatgpt.com/c/same-title-1", "Repeated", List.of("a")));
        importService.persist(providerChat(Source.claudeWeb, "same-title-2",
                "https://claude.ai/chat/same-title-2", "Repeated", List.of("a")));

        assertEquals(3, chats.findAll().size());
    }

    @Test
    void geminiWebWithoutDurableIdUsesHashOnlyForExactDuplicates() throws Exception {
        ImportedChat first = providerChat(Source.geminiWeb, null, "https://gemini.google.com/app",
                "Gemini", List.of("same"));
        Chat stored = importService.persist(first).chat();
        Chat duplicate = importService.persist(first).chat();
        Chat changed = importService.persist(providerChat(Source.geminiWeb, null,
                "https://gemini.google.com/app", "Gemini", List.of("changed"))).chat();

        assertEquals(stored.id(), duplicate.id());
        assertNotEquals(stored.id(), changed.id());
        assertEquals(2, chats.findAll().size());
    }

    @Test
    void failedNewImportRollsBackChatMessagesAndFts() throws Exception {
        ImportedChat invalid = providerChatWithMessages(Source.chatGptWeb, "bad-new",
                "https://chatgpt.com/c/bad-new", "Bad",
                List.of(new Message(0, 0, "user", "visible before failure", 0, null, null),
                        new Message(0, 0, null, "invalid role", 1, null, null)));

        assertThrowsSql(() -> importService.persist(invalid));

        assertTrue(chats.findAll().isEmpty());
        assertTrue(messages.searchText("visible").isEmpty());
    }

    @Test
    void failedRefreshRestoresPriorChatMessagesAndFts() throws Exception {
        Chat first = importService.persist(providerChat(Source.chatGptWeb, "rollback",
                "https://chatgpt.com/c/rollback", "Rollback", List.of("stable original"))).chat();
        String originalHash = chats.findById(first.id()).orElseThrow().contentHash();
        ImportedChat invalidRefresh = providerChatWithMessages(Source.chatGptWeb, "rollback",
                "https://chatgpt.com/c/rollback", "Rollback",
                List.of(new Message(0, 0, "user", "replacement partial", 0, null, null),
                        new Message(0, 0, null, "invalid role", 1, null, null)));

        assertThrowsSql(() -> importService.persist(invalidRefresh));

        Chat stored = chats.findById(first.id()).orElseThrow();
        assertEquals(originalHash, stored.contentHash());
        assertEquals(List.of("stable original"), messageTexts(first.id()));
        assertEquals(1, messages.searchText("stable").size());
        assertTrue(messages.searchText("replacement").isEmpty());
    }

    @Test
    void successfulImportInsideOuterTransactionCanBeRolledBackByCaller() throws Exception {
        conn.setAutoCommit(false);
        try {
            PersistResult result = importService.persist(providerChat(Source.chatGptWeb, "outer",
                    "https://chatgpt.com/c/outer", "Outer", List.of("outer text")));

            assertEquals(Outcome.inserted, result.outcome());
            assertEquals(1, chats.findAll().size());

            conn.rollback();
        } finally {
            conn.setAutoCommit(true);
        }

        assertTrue(chats.findAll().isEmpty());
        assertTrue(messages.searchText("outer").isEmpty());
    }

    @Test
    void failedImportInsideOuterTransactionKeepsCallerTransactionUsable() throws Exception {
        conn.setAutoCommit(false);
        try {
            Chat survivor = chats.insert(new Chat(0, null, Source.plainText, "Survivor", null, null,
                    "2026-08-05T00:00:00Z", false));
            ImportedChat invalid = providerChatWithMessages(Source.chatGptWeb, "bad-outer",
                    "https://chatgpt.com/c/bad-outer", "Bad",
                    List.of(new Message(0, 0, "user", "partial text", 0, null, null),
                            new Message(0, 0, null, "invalid role", 1, null, null)));

            assertThrowsSql(() -> importService.persist(invalid));
            assertEquals(List.of(survivor), chats.findAll());

            conn.rollback();
        } finally {
            conn.setAutoCommit(true);
        }

        assertTrue(chats.findAll().isEmpty());
        assertTrue(messages.searchText("partial").isEmpty());
    }

    private List<String> messageTexts(long chatId) throws SQLException {
        return messages.findByChat(chatId).stream().map(Message::text).toList();
    }

    private static ImportedChat providerChat(Source source, String externalId, String sourceUri,
            String title, List<String> texts) {
        List<Message> messages = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            messages.add(new Message(0, 0, i % 2 == 0 ? "user" : "assistant", texts.get(i), i, null, null));
        }
        return providerChatWithMessages(source, externalId, sourceUri, title, messages);
    }

    private static ImportedChat providerChatWithMessages(Source source, String externalId, String sourceUri,
            String title, List<Message> messages) {
        Chat chat = new Chat(0, null, source, title, null, null, "2026-08-05T00:00:00Z", false,
                new ImportMetadata(externalId, sourceUri, null, null, "2026-08-05T00:00:00Z"));
        return new ImportedChat(chat, messages);
    }

    private static void assertThrowsSql(SqlRunnable runnable) {
        org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, runnable::run);
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws Exception;
    }
}
