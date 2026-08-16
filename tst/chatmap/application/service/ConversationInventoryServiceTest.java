package chatmap.application.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.application.port.provider.ChatProvider;
import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.ConversationInventory;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Source;
import chatmap.application.model.ImportedChat;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;

class ConversationInventoryServiceTest {

    private Connection conn;
    private ChatRepository chats;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void providerFailureDoesNotSuppressSuccessfulInventories() throws Exception {
        ConversationInventoryService service = new ConversationInventoryService(List.of(
                failingProvider(),
                provider("ok", List.of(candidate(Source.codexCli, "one")))), chats);

        ConversationInventory inventory = service.inventory();

        assertEquals(2, inventory.providers().size());
        assertEquals(0, inventory.providers().get(0).conversations().size());
        assertTrue(inventory.providers().get(0).diagnostic().contains("boom"));
        assertEquals(1, inventory.providers().get(1).conversations().size());
    }

    @Test
    void importedStatusUsesExternalIdentityAndDoesNotWrite() throws Exception {
        chats.insert(Chat.builder()
                .id(0)
                .source(Source.codexCli)
                .title("Stored")
                .importedAt("now")
                .externalConversationId("stored-id")
                .sourceUri("source://stored")
                .sourceUpdatedAt("now")
                .lastImportedAt("now")
                .build());
        long before = rowCount("chats");
        ConversationInventoryService service = new ConversationInventoryService(List.of(
                provider("codex", List.of(
                        candidate(Source.codexCli, "stored-id"),
                        candidate(Source.codexCli, "missing-id")))), chats);

        ConversationInventory inventory = service.inventory();

        assertEquals(before, rowCount("chats"));
        assertEquals(true, inventory.providers().getFirst().conversations().get(0).alreadyImported());
        assertEquals(false, inventory.providers().getFirst().conversations().get(1).alreadyImported());
    }

    @Test
    void duplicateCandidatesAreRemovedDeterministically() throws Exception {
        ConversationInventoryService service = new ConversationInventoryService(List.of(
                provider("dupes", List.of(
                        candidate(Source.chatGptWeb, "same"),
                        new ConversationCandidate(Source.chatGptWeb, "same", "Second",
                                "https://chatgpt.com/c/same?x=1", null)))), chats);

        ConversationInventory inventory = service.inventory();

        assertEquals(1, inventory.providers().getFirst().conversations().size());
        assertTrue(inventory.providers().getFirst().diagnostic().contains("Removed 1 duplicate"));
        assertEquals("same", inventory.providers().getFirst().conversations().getFirst()
                .candidate().externalConversationId());
    }

    private long rowCount(String table) throws Exception {
        try (Statement st = conn.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static ConversationCandidate candidate(Source source, String id) {
        return new ConversationCandidate(source, id, "Title " + id, "source://" + id, null);
    }

    private static ChatProvider provider(String name, List<ConversationCandidate> candidates) {
        return new ChatProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<ImportedChat> latestChat() {
                return Optional.empty();
            }

            @Override
            public List<ConversationCandidate> listChats() {
                return candidates;
            }
        };
    }

    private static ChatProvider failingProvider() {
        return new ChatProvider() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public Optional<ImportedChat> latestChat() {
                return Optional.empty();
            }

            @Override
            public List<ConversationCandidate> listChats() {
                throw new IllegalStateException("boom");
            }
        };
    }
}
