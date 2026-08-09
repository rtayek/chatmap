package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import chatmap.backend.ai.AiBackendUnsupportedRequestException;
import chatmap.backend.providers.ChatProvider;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;
import chatmap.storage.Database;

class ServiceGraphTest {

    @Test
    void defaultGraphDoesNotConfigureOptionalLiveProvidersOrSummaryBackend() throws Exception {
        try (Connection connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
                ServiceGraph graph = ServiceGraph.create(connection)) {
            Chat stored = graph.chats().insert(new Chat(
                    0, null, Source.plainText, "Stored", null, null, "2026-08-08T00:00:00Z", false));

            LiveChatFetchService.Resolution resolution = graph.liveChatFetchService().resolve(null);

            assertEquals(stored.id(), resolution.chatId());
            assertThrows(AiBackendUnsupportedRequestException.class,
                    () -> graph.summaryService().summarize(stored.id()));
        }
    }

    @Test
    void injectedProviderIsUsedForLiveFetch() throws Exception {
        ChatProvider provider = new ChatProvider() {
            @Override
            public String name() {
                return "Injected";
            }

            @Override
            public Optional<ImportedChat> latestChat() {
                Chat chat = new Chat(
                        0, null, Source.plainText, "Injected Chat",
                        null, null, "2026-08-08T00:00:00Z", false);
                Message message = new Message(0, 0, "user", "injected body", 0, null, null);
                return Optional.of(new ImportedChat(chat, List.of(message)));
            }
        };

        try (Connection connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
                ServiceGraph graph = ServiceGraph.create(
                        connection,
                        new ServiceGraph.Integrations(List.of(provider), request -> {
                            throw new AssertionError("summary backend should not run");
                        }))) {
            LiveChatFetchService.Resolution resolution = graph.liveChatFetchService().resolve(null);

            assertEquals("Injected Chat", graph.chats().findById(resolution.chatId()).orElseThrow().title());
        }
    }
}
