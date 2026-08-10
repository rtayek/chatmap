package chatmap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;

class RepositoryTest {

    private Connection conn;
    private ProjectRepository projects;
    private ChatRepository chats;
    private MessageRepository messages;
    private TagRepository tags;
    private SummaryRepository summaries;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        projects = new ProjectRepository(conn);
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        tags = new TagRepository(conn);
        summaries = new SummaryRepository(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void createsUpdatesAndDeletesProject() throws Exception {
        Project created = projects.insert(new Project(0, "Work", "Initial",
                "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));

        assertEquals("Work", projects.findById(created.id()).orElseThrow().name());

        projects.update(new Project(created.id(), "Personal", "Updated",
                created.createdAt(), "2026-07-06T01:00:00Z"));

        Project updated = projects.findById(created.id()).orElseThrow();
        assertEquals("Personal", updated.name());
        assertEquals("Updated", updated.description());

        projects.delete(created.id());

        assertTrue(projects.findById(created.id()).isEmpty());
    }

    @Test
    void createsUpdatesArchivesAndDeletesChat() throws Exception {
        Project project = projects.insert(new Project(0, "Project", null,
                "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));
        Chat chat = chats.insert(new Chat(0, project.id(), Source.plainText, "Original",
                null, null, "2026-07-06T00:00:00Z", false));

        chats.updateTitle(chat.id(), "Renamed");
        chats.setArchived(chat.id(), true);
        chats.assignProject(chat.id(), null);

        Chat updated = chats.findById(chat.id()).orElseThrow();
        assertEquals("Renamed", updated.title());
        assertTrue(updated.archived());
        assertEquals(null, updated.projectId());

        chats.delete(chat.id());

        assertFalse(chats.findById(chat.id()).isPresent());
    }

    @Test
    void findsMostRecentChatByImportedAtAndId() throws Exception {
        assertTrue(chats.findMostRecent().isEmpty());

        chats.insert(new Chat(0, null, Source.plainText, "First",
                null, null, "2026-07-06T00:00:00Z", false));
        Chat second = chats.insert(new Chat(0, null, Source.plainText, "Second",
                null, null, "2026-07-06T01:00:00Z", false));

        assertEquals(second.id(), chats.findMostRecent().orElseThrow().id());
        assertEquals("Second", chats.findMostRecent().orElseThrow().title());
    }

    @Test
    void findMostRecentSkipsArchivedChats() throws Exception {
        Chat older = chats.insert(new Chat(0, null, Source.plainText, "Older",
                null, null, "2026-07-06T00:00:00Z", false));
        Chat newer = chats.insert(new Chat(0, null, Source.plainText, "Newer",
                null, null, "2026-07-06T01:00:00Z", false));

        assertEquals(newer.id(), chats.findMostRecent().orElseThrow().id());

        chats.setArchived(newer.id(), true);

        assertEquals(older.id(), chats.findMostRecent().orElseThrow().id(),
                "an archived chat must not be selected as the fallback 'latest chat'");

        chats.setArchived(older.id(), true);

        assertTrue(chats.findMostRecent().isEmpty(), "no non-archived chat left");
    }

    @Test
    void createsUpdatesDeletesAndSearchesMessages() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.plainText, "Searchable",
                null, null, "2026-07-06T00:00:00Z", false));
        Message first = messages.insert(new Message(0, chat.id(), "user",
                "storage foundation alpha", 0, null, null));
        Message second = messages.insert(new Message(0, chat.id(), "assistant",
                "repository beta", 1, null, null));

        assertEquals(List.of(first, second), messages.findByChat(chat.id()));
        assertEquals(List.of(first.id()), messages.searchText("alpha"));

        messages.updateText(first.id(), "storage foundation gamma");

        assertTrue(messages.searchText("alpha").isEmpty());
        assertEquals(List.of(first.id()), messages.searchText("gamma"));

        messages.delete(second.id());

        assertEquals(List.of(new Message(first.id(), chat.id(), "user",
                "storage foundation gamma", 0, null, null)), messages.findByChat(chat.id()));
        assertTrue(messages.searchText("beta").isEmpty());
    }

    @Test
    void insertsBatchOfMessagesInSingleOperation() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.plainText, "Batch", null, null, "2026-07-06T00:00:00Z", false));
        List<Message> batch = List.of(
                new Message(0, chat.id(), "user", "Batch item 1", 0, null, null),
                new Message(0, chat.id(), "assistant", "Batch item 2", 1, null, null)
        );

        messages.insertAll(batch);

        List<Message> stored = messages.findByChat(chat.id());
        assertEquals(2, stored.size());
        assertEquals("Batch item 1", stored.get(0).text());
        assertEquals("Batch item 2", stored.get(1).text());
    }

    @Test
    void findsMessagesForManyChatsInSequenceOrder() throws Exception {
        Chat first = chats.insert(new Chat(0, null, Source.plainText, "First",
                null, null, "2026-07-06T00:00:00Z", false));
        Chat second = chats.insert(new Chat(0, null, Source.plainText, "Second",
                null, null, "2026-07-06T00:01:00Z", false));
        Chat empty = chats.insert(new Chat(0, null, Source.plainText, "Empty",
                null, null, "2026-07-06T00:02:00Z", false));
        Message firstTwo = messages.insert(new Message(0, first.id(), "assistant", "two", 2, null, null));
        Message firstOne = messages.insert(new Message(0, first.id(), "user", "one", 1, null, null));
        Message secondOne = messages.insert(new Message(0, second.id(), "user", "other", 0, null, null));

        var byChat = messages.findByChatIds(List.of(first.id(), second.id(), empty.id()));

        assertEquals(List.of(firstOne, firstTwo), byChat.get(first.id()));
        assertEquals(List.of(secondOne), byChat.get(second.id()));
        assertEquals(List.of(), byChat.get(empty.id()));
        assertTrue(messages.findByChatIds(List.of()).isEmpty());
    }

    @Test
    void assignsFindsRemovesAndCascadesTags() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.plainText, "Tagged",
                null, null, "2026-07-06T00:00:00Z", false));
        Tag tag = tags.insert(new Tag(0, "MVP"));

        assertEquals(tag, tags.findByName("mvp").orElseThrow());

        tags.assignToChat(chat.id(), tag.id());
        tags.assignToChat(chat.id(), tag.id());

        assertEquals(List.of(tag), tags.findByChat(chat.id()));

        tags.removeFromChat(chat.id(), tag.id());

        assertTrue(tags.findByChat(chat.id()).isEmpty());

        tags.assignToChat(chat.id(), tag.id());
        chats.delete(chat.id());

        assertTrue(tags.findByChat(chat.id()).isEmpty());
        assertEquals(tag, tags.findById(tag.id()).orElseThrow());
    }

    @Test
    void findsTagsForManyChatsInNameOrder() throws Exception {
        Chat first = chats.insert(new Chat(0, null, Source.plainText, "First",
                null, null, "2026-07-06T00:00:00Z", false));
        Chat second = chats.insert(new Chat(0, null, Source.plainText, "Second",
                null, null, "2026-07-06T00:01:00Z", false));
        Chat empty = chats.insert(new Chat(0, null, Source.plainText, "Empty",
                null, null, "2026-07-06T00:02:00Z", false));
        Tag zebra = tags.insert(new Tag(0, "zebra"));
        Tag alpha = tags.insert(new Tag(0, "Alpha"));
        Tag beta = tags.insert(new Tag(0, "beta"));
        tags.assignToChat(first.id(), zebra.id());
        tags.assignToChat(first.id(), alpha.id());
        tags.assignToChat(second.id(), beta.id());

        var byChat = tags.findByChatIds(List.of(first.id(), second.id(), empty.id()));

        assertEquals(List.of(alpha, zebra), byChat.get(first.id()));
        assertEquals(List.of(beta), byChat.get(second.id()));
        assertEquals(List.of(), byChat.get(empty.id()));
        assertTrue(tags.findByChatIds(List.of()).isEmpty());
    }

    @Test
    void findByNameIsCaseInsensitiveIncludingNonAscii() throws Exception {
        Project project = projects.insert(new Project(0, "München Notes", null,
                "2026-08-10T00:00:00Z", "2026-08-10T00:00:00Z"));

        assertEquals(project, projects.findByName("münchen notes").orElseThrow());
        assertEquals(project, projects.findByName("MÜNCHEN NOTES").orElseThrow());
        assertTrue(projects.findByName("no such project").isEmpty());
    }

    @Test
    void enforcesUniqueAsciiCaseInsensitiveProjectNames() throws Exception {
        projects.insert(new Project(0, "Foo", null, "2026-08-10T00:00:00Z", "2026-08-10T00:00:00Z"));

        SQLException thrown = assertThrows(SQLException.class,
                () -> projects.insert(new Project(0, "foo", null,
                        "2026-08-10T00:00:00Z", "2026-08-10T00:00:00Z")));

        assertTrue(ProjectRepository.isDuplicateNameViolation(thrown), thrown.getMessage());
    }

    @Test
    void enforcesForeignKeysAndCaseInsensitiveUniqueTags() throws Exception {
        assertThrows(SQLException.class, () -> messages.insert(new Message(0, 999, "user",
                "orphan", 0, null, null)));

        tags.insert(new Tag(0, "SQLite"));

        assertThrows(SQLException.class, () -> tags.insert(new Tag(0, "sqlite")));
    }

    @Test
    void enforcesUniqueExternalProviderIdentity() throws Exception {
        chats.insert(new Chat(0, null, Source.chatGptWeb, "First", null, null,
                "2026-08-05T00:00:00Z", false, new ImportMetadata("same-id", "https://chatgpt.com/c/same-id",
                "hash-one", null, "2026-08-05T00:00:00Z")));

        assertThrows(SQLException.class, () -> chats.insert(new Chat(0, null, Source.chatGptWeb,
                "Second", null, null, "2026-08-05T00:00:00Z", false, new ImportMetadata("same-id",
                "https://chatgpt.com/c/same-id", "hash-two", null, "2026-08-05T00:00:00Z"))));
    }

    @Test
    void latestSummaryOnlyReturnsCurrentContentHash() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.chatGptWeb, "Summarized", null, null,
                "2026-08-05T00:00:00Z", false, new ImportMetadata("summary-id", "https://chatgpt.com/c/summary-id",
                "old-hash", null, "2026-08-05T00:00:00Z")));
        ChatSummary summary = summaries.insert(new ChatSummary(0, chat.id(), "Old summary",
                "claude", "2026-08-05T00:01:00Z", "old-hash"));

        assertEquals(summary, summaries.findLatestForChat(chat.id()).orElseThrow());

        chats.updateImportMetadata(chat.id(), chat.title(), chat.sourceUri(), "new-hash",
                chat.sourceUpdatedAt(), "2026-08-05T00:02:00Z");

        assertTrue(summaries.findLatestForChat(chat.id()).isEmpty());
        assertEquals(summary, summaries.findLatestStoredForChat(chat.id()).orElseThrow());
    }

    @Test
    void transactionRunnerSynchronizesConcurrentlyOnLock() throws Exception {
        Object lock = new Object();
        TransactionRunner runner = new TransactionRunner(conn, lock);
        boolean[] ran = new boolean[1];
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);

        synchronized (lock) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                started.countDown();
                try {
                    runner.inTransaction(() -> {
                        ran[0] = true;
                        return null;
                    });
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(ran[0], "Transaction should be blocked while lock is held by caller");
        }

        runner.inTransaction(() -> {
            chats.insert(new Chat(0, null, Source.plainText, "ThreadSafe", null, null, "2026-08-09T00:00:00Z", false));
            return null;
        });

        assertEquals(1, chats.findAll().size());
    }
}
