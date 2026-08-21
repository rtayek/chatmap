package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.Channel;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.service.PromptRoutingResult;
import chatmap.domain.PromptClassificationLevel;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.PromptRouteRepository;

class RoutePromptCliTest {

    @org.junit.jupiter.api.AfterEach
    void releaseLogFile() {
        LoggingBootstrap.initializeTemporaryFallback();
    }

    @Test
    void executeRoutesPromptWithExplicitProject(@TempDir Path tempDir) throws Exception {
        Path home = tempDir.resolve(".chatmap");
        Files.createDirectories(home);
        String[] args = new String[]{
                "--home", home.toString(),
                "--project", "Foo",
                "--conversation", "foo-task",
                "Explain this compile error"};

        PromptRoutingResult result = RoutePromptCli.execute(args, providers(), Clock.fixed(
                Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));

        assertEquals("Foo", result.projectContext().workingProjectIdentity());
        assertEquals("foo-task", result.conversationContext().id());
        assertEquals(PromptClassificationLevel.LIGHTWEIGHT, result.classification().level());
        assertEquals(ModelTarget.ollamaQwen257b.id(), result.route().target().id());

        try (Connection conn = new Database("jdbc:sqlite:" + home.resolve("chatmap.db")).openAndInitialize()) {
            PromptRouteRepository routes = new PromptRouteRepository(conn);
            assertTrue(routes.findByChatId(result.promptResult().chatId()).isPresent());
        }
    }

    private static Map<Channel, LlmProvider> providers() {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        LlmProvider provider = new LlmProvider() {
            @Override
            public LlmResponse execute(ModelTarget target, LlmRequest request) {
                return new LlmResponse("ok", new BackendId("Fake"), Duration.ofMillis(1), target, "session-1");
            }

            @Override
            public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
                return Set.of();
            }
        };
        for (Channel channel : Channel.values()) {
            providers.put(channel, provider);
        }
        return providers;
    }
}
