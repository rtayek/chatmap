package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.Channel;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Project;
import chatmap.domain.PromptClassificationLevel;
import chatmap.domain.PromptRouteRecord;
import chatmap.domain.Source;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.PromptRouteRepository;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;

class PromptRouterServiceTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ProjectRepository projects;
    private PromptRouteRepository promptRoutes;
    private RecordingProvider provider;
    private PromptRouterService router;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        projects = new ProjectRepository(conn);
        promptRoutes = new PromptRouteRepository(conn);
        provider = new RecordingProvider();
        ImportService importService = new ImportService(chats, messages, new TransactionRunner(conn));
        ProjectService projectService = new ProjectService(projects, chats);
        PromptService promptService = new PromptService(
                providers(provider),
                importService,
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC),
                Files.createDirectories(tempDir.resolve("transcripts")));
        router = new PromptRouterService(
                new DeterministicPromptClassifier(),
                PromptRouteSelector.defaults(),
                promptService,
                projectService,
                promptRoutes,
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void persistsClassificationProjectProviderAndModel() throws Exception {
        PromptRoutingResult result = router.route(
                ProjectContext.of("Foo", Path.of("C:/work/foo")),
                new ConversationContext("foo-current-task"),
                "Explain this compile error in Foo.java.");

        assertEquals(PromptClassificationLevel.LIGHTWEIGHT, result.classification().level());
        assertEquals(ModelTarget.ollamaQwen257b.id(), result.route().target().id());
        PromptRouteRecord stored = promptRoutes.findByChatId(result.promptResult().chatId()).orElseThrow();
        assertEquals("chatmap", stored.chatMapProjectIdentity());
        assertTrue(stored.workingProjectId() > 0);
        assertEquals(stored.workingProjectId(), result.projectContext().projectId());
        assertEquals("Foo", stored.workingProjectIdentity());
        assertEquals("C:\\work\\foo", stored.repositoryPath().orElseThrow());
        assertEquals("foo-current-task", stored.conversationId());
        assertEquals(PromptClassificationLevel.LIGHTWEIGHT, stored.classificationLevel());
        assertEquals(Channel.ollama.name(), stored.routeProviderId());
        assertEquals(ModelTarget.ollamaQwen257b.id(), stored.routeModelTargetId());
        assertEquals(ModelTarget.ollamaQwen257b.providerModelName(), stored.providerModelName());
        assertEquals("SUCCEEDED", stored.requestStatus());
    }

    @Test
    void isolatesProjectsAndDoesNotAttachAnotherProjectsPrompt() throws Exception {
        PromptRoutingResult foo = router.route(
                ProjectContext.of("Foo", null),
                new ConversationContext("current"),
                "Explain this compile error in Foo.java.");
        PromptRoutingResult bar = router.route(
                ProjectContext.of("Bar", null),
                new ConversationContext("current"),
                "Explain this compile error in Bar.java.");

        assertEquals("Foo", projects.findById(chats.findById(foo.promptResult().chatId()).orElseThrow()
                .projectId()).orElseThrow().name());
        assertEquals("Bar", projects.findById(chats.findById(bar.promptResult().chatId()).orElseThrow()
                .projectId()).orElseThrow().name());
        assertEquals("Explain this compile error in Foo.java.", provider.requests.get(0).prompt());
        assertEquals("Explain this compile error in Bar.java.", provider.requests.get(1).prompt());
    }

    @Test
    void keepsConversationIdentityWhenProviderRouteChanges() throws Exception {
        router.route(ProjectContext.of("Foo", null), new ConversationContext("same"),
                "Explain this compile error in Foo.java.");
        router.route(ProjectContext.of("Foo", null), new ConversationContext("same"),
                "Review architecture across multiple modules.");

        long projectId = projects.findByName("Foo").orElseThrow().id();
        List<PromptRouteRecord> records = promptRoutes.findByWorkingProjectIdAndConversation(projectId, "same");
        assertEquals(2, records.size());
        assertEquals(ModelTarget.ollamaQwen257b.id(), records.get(0).routeModelTargetId());
        assertEquals(ModelTarget.claude.id(), records.get(1).routeModelTargetId());
        assertTrue(records.stream().allMatch(record -> record.conversationId().equals("same")));
    }

    @Test
    void resumedPromptUsesSelectedChatSessionAndAppendsToExistingChat() throws Exception {
        Project project = projects.insert(new Project(0, "Foo", null,
                "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z"));
        Chat existing = chats.insert(Chat.builder()
                .id(0)
                .projectId(project.id())
                .source(Source.claudeCliPrompt)
                .title("Existing conversation")
                .createdAt("2026-08-20T00:00:00Z")
                .updatedAt("2026-08-20T00:00:00Z")
                .importedAt("2026-08-20T00:00:00Z")
                .archived(false)
                .originatedBy(chatmap.domain.ChatOrigin.generated)
                .channelId(Channel.claudeCli.name())
                .modelTargetId(ModelTarget.claude.id())
                .providerSessionId("session-abc")
                .build());
        messages.insertAll(List.of(
                new Message(0, existing.id(), MessageRole.user, "old prompt", 0, null, null),
                new Message(0, existing.id(), MessageRole.assistant, "old response", 1, null, null)));

        PromptRoutingResult result = router.route(ProjectContext.from(project), new ConversationContext("resume"),
                "Explain this compile error.", existing);

        assertEquals(existing.id(), result.promptResult().chatId());
        assertEquals(ModelTarget.claude.id(), result.route().target().id());
        assertEquals("session-abc", provider.requests.getFirst().sessionId().orElseThrow());
        assertEquals(List.of("old prompt", "old response", "Explain this compile error.",
                "response for claude"), messages.findByChat(existing.id()).stream().map(Message::text).toList());
        assertEquals(1, chats.findAll().size());
    }

    @Test
    void routeWithoutResumeStillCreatesNewChatForEachPrompt() throws Exception {
        Project project = projects.insert(new Project(0, "Foo", null,
                "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z"));

        PromptRoutingResult first = router.route(ProjectContext.from(project), new ConversationContext("same"),
                "Explain this compile error.");
        PromptRoutingResult second = router.route(ProjectContext.from(project), new ConversationContext("same"),
                "Explain this other compile error.");

        assertTrue(first.promptResult().chatId() != second.promptResult().chatId());
        assertEquals(2, chats.findAll().size());
    }

    @Test
    void refusesToResumeChatFromAnotherProject() throws Exception {
        Project foo = projects.insert(new Project(0, "Foo", null,
                "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z"));
        Project bar = projects.insert(new Project(0, "Bar", null,
                "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z"));
        Chat barChat = chats.insert(Chat.builder()
                .id(0)
                .projectId(bar.id())
                .source(Source.claudeCliPrompt)
                .title("Bar conversation")
                .importedAt("2026-08-20T00:00:00Z")
                .channelId(Channel.claudeCli.name())
                .modelTargetId(ModelTarget.claude.id())
                .providerSessionId("bar-session")
                .build());

        IllegalArgumentException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> router.route(ProjectContext.from(foo), new ConversationContext("resume"),
                        "Explain this compile error.", barChat));

        assertEquals("Resume chat belongs to a different project.", thrown.getMessage());
    }

    private static Map<Channel, LlmProvider> providers(LlmProvider provider) {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) {
            providers.put(channel, provider);
        }
        return providers;
    }

    private static final class RecordingProvider implements LlmProvider {
        private final List<LlmRequest> requests = new ArrayList<>();

        @Override
        public LlmResponse execute(ModelTarget target, LlmRequest request) {
            requests.add(request);
            String sessionId = request.sessionId().isPresent() ? null : target.id() + "-session-" + requests.size();
            return new LlmResponse("response for " + target.id(), new BackendId("Fake " + target.id()),
                    Duration.ofMillis(1), target, sessionId);
        }

        @Override
        public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
            return Set.of(chatmap.application.port.llm.LlmCapability.sessions);
        }
    }
}
