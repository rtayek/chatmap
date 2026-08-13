package chatmap.application.service;

import chatmap.app.ServiceGraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.AiBackendUnsupportedRequestException;
import chatmap.application.port.provider.ChatProvider;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;
import chatmap.application.model.ImportedChat;
import chatmap.infrastructure.persistence.sqlite.Database;

class ServiceGraphTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultGraphDoesNotConfigureOptionalLiveProvidersOrSummaryBackend() throws Exception {
        try (Connection connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
                ServiceGraph graph = ServiceGraph.create(connection, paths())) {
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
                Message message = new Message(0, 0, MessageRole.user, "injected body", 0, null, null);
                return Optional.of(new ImportedChat(chat, List.of(message)));
            }
        };

        try (Connection connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
                ServiceGraph graph = ServiceGraph.create(
                        connection,
                        new ServiceGraph.Integrations(List.of(provider), request -> {
                            throw new AssertionError("summary backend should not run");
                        }),
                        paths())) {
            LiveChatFetchService.Resolution resolution = graph.liveChatFetchService().resolve(null);

            assertEquals("Injected Chat", graph.chats().findById(resolution.chatId()).orElseThrow().title());
        }
    }

    @Test
    void promptServiceIsProvidedByServiceGraph() throws Exception {
        try (Connection connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
                ServiceGraph graph = ServiceGraph.create(connection, paths())) {
            org.junit.jupiter.api.Assertions.assertNotNull(graph.promptService());
        }
    }

    @Test
    void promptServiceWritesTranscriptsUnderResolvedHome() throws Exception {
        ResolvedPaths paths = paths();
        ServiceGraph.Integrations integrations = new ServiceGraph.Integrations(
                List.of(),
                request -> {
                    throw new AssertionError("summary backend should not run");
                },
                Map.of("fake", request -> new AiResponse(
                        "response", new BackendId("fake"), Duration.ZERO)));

        try (Connection connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
                ServiceGraph graph = ServiceGraph.create(connection, integrations, paths)) {
            PromptResult result = graph.promptService().submit("fake", "prompt");

            Path transcript = result.transcript().orElseThrow();
            assertTrue(transcript.startsWith(paths.transcriptsDirectory()));
            assertTrue(Files.isRegularFile(transcript));
        }
    }

    private ResolvedPaths paths() {
        Path home = tempDir.resolve("home");
        return new ResolvedPaths(home, home.resolve("chatmap.db"));
    }
}
