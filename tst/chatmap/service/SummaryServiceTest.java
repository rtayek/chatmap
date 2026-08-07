package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.service.SummaryService.Parsed;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.SummaryRepository;
import chatmap.storage.TagRepository;

class SummaryServiceTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private SummaryRepository summaries;
    private TagRepository tags;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        summaries = new SummaryRepository(conn);
        tags = new TagRepository(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void parsesSummaryAndTagsFromWellFormedResponse() {
        String response = "SUMMARY: We decided to use SQLite with FTS5 for search.\n"
                + "TAGS: architecture, storage, search";

        Parsed parsed = SummaryService.parse(response);

        assertEquals("We decided to use SQLite with FTS5 for search.", parsed.summaryText());
        assertEquals(List.of("architecture", "storage", "search"), parsed.tagNames());
    }

    @Test
    void isCaseInsensitiveAndTrimsTagWhitespace() {
        String response = "summary:   Short summary here.  \n"
                + "tags:  One , TWO ,three  ";

        Parsed parsed = SummaryService.parse(response);

        assertEquals("Short summary here.", parsed.summaryText());
        assertEquals(List.of("one", "two", "three"), parsed.tagNames());
    }

    @Test
    void fallsBackToWholeResponseWhenFormatNotFollowed() {
        String response = "The model just wrote free-form text instead of following the format.";

        Parsed parsed = SummaryService.parse(response);

        assertEquals(response, parsed.summaryText());
        assertTrue(parsed.tagNames().isEmpty());
    }

    @Test
    void ignoresTagsLineWithNoTags() {
        String response = "SUMMARY: Fine.\nTAGS:   ";

        Parsed parsed = SummaryService.parse(response);

        assertTrue(parsed.tagNames().isEmpty());
    }

    @Test
    void buildPromptIncludesTitleAndRoleLabeledMessages() {
        Chat chat = new Chat(1, null, Source.plainText, "Storage Decision", null, null, "2026-01-01T00:00:00Z", false);
        List<Message> messages = List.of(
                new Message(1, 1, "user", "Should we use SQLite?", 0, null, null),
                new Message(2, 1, "assistant", "Yes, with FTS5 for search.", 1, null, null));

        String prompt = SummaryService.buildPrompt(chat, messages);

        assertTrue(prompt.contains("Storage Decision"));
        assertTrue(prompt.contains("USER: Should we use SQLite?"));
        assertTrue(prompt.contains("ASSISTANT: Yes, with FTS5 for search."));
        assertTrue(prompt.contains("SUMMARY: "));
        assertTrue(prompt.contains("TAGS: "));
    }

    @Test
    void successfulSummaryInsideOuterTransactionCanBeRolledBackByCaller() throws Exception {
        Chat chat = insertChatWithMessage();
        SummaryService service = new SummaryService(chats, messages, summaries, tags,
                prompt -> "SUMMARY: Stored summary.\nTAGS: storage");

        conn.setAutoCommit(false);
        try {
            service.summarize(chat.id());
            assertTrue(summaries.findLatestStoredForChat(chat.id()).isPresent());
            assertEquals(1, tags.findAll().size());

            conn.rollback();
        } finally {
            conn.setAutoCommit(true);
        }

        assertTrue(summaries.findLatestStoredForChat(chat.id()).isEmpty());
        assertTrue(tags.findAll().isEmpty());
    }

    @Test
    void failedSummaryTagAssignmentRollsBackSummaryAndTags() throws Exception {
        Chat chat = insertChatWithMessage();
        SummaryService service = new SummaryService(chats, messages, summaries, tags,
                prompt -> "SUMMARY: Stored summary.\nTAGS: failtag");
        try (var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TRIGGER fail_chat_tag BEFORE INSERT ON chatTags
                    BEGIN
                        SELECT RAISE(ABORT, 'tag assignment failed');
                    END
                    """);
        }

        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> service.summarize(chat.id()));

        assertTrue(summaries.findLatestStoredForChat(chat.id()).isEmpty());
        assertTrue(tags.findAll().isEmpty());
    }

    private Chat insertChatWithMessage() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.plainText, "Storage Decision",
                null, null, "2026-01-01T00:00:00Z", false));
        messages.insert(new Message(0, chat.id(), "user", "Should we use SQLite?", 0, null, null));
        return chat;
    }
}
