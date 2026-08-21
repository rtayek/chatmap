package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.ConversationInventory;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Project;
import chatmap.domain.PromptClassificationLevel;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.application.port.llm.LlmBackend;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.Channel;
import chatmap.application.model.ChatExportModel;
import chatmap.application.service.DeterministicPromptClassifier;
import chatmap.application.service.ExportService;
import chatmap.application.service.ConversationInventoryService;
import chatmap.application.service.ImportService;
import chatmap.application.service.LiveChatFetchService;
import chatmap.application.service.ProjectService;
import chatmap.application.service.PromptRouteSelector;
import chatmap.application.service.PromptResult;
import chatmap.application.service.PromptRouterService;
import chatmap.application.service.PromptRoutingResult;
import chatmap.application.service.PromptService;
import chatmap.application.service.SearchService;
import chatmap.application.service.SummaryService;
import chatmap.application.service.TagService;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.PromptRouteRepository;
import chatmap.infrastructure.persistence.sqlite.SearchRepository;
import chatmap.infrastructure.persistence.sqlite.SummaryRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;

class ChatMapControllerTest {

    @TempDir
    Path tempDir;

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ProjectService projectService;
    private TagService tagService;
    private ChatMapController controller;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        ProjectRepository projects = new ProjectRepository(conn);
        TagRepository tags = new TagRepository(conn);
        projectService = new ProjectService(projects, chats);
        tagService = new TagService(tags, chats);
        ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
        SummaryService summaryService = new SummaryService(chats, messages,
                new SummaryRepository(conn), tags,
                summaryBackend());
        LiveChatFetchService liveChatFetchService =
                new LiveChatFetchService(java.util.List.of(), importService, chats);
        controller = new ChatMapController(
                importService,
                new ExportService(chats, messages, projects, tags, new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter()),
                new SearchService(new SearchRepository(conn)),
                projectService,
                tagService,
                summaryService,
                liveChatFetchService);
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
        assertEquals("Loaded 2 chats.", snapshot.statusText());
    }

    @Test
    void defaultControllerFactoryKeepsInteractiveProviderSet() {
        assertEquals(List.of(
                "Claude Code (CLI)", "Codex (CLI)", "Gemini (CLI)",
                "Claude (web)", "ChatGPT (web)", "Gemini (web)"),
                ChatMapControllerFactory.defaultIntegrations()
                        .chatProviders()
                        .stream()
                        .map(chatmap.application.port.provider.ChatProvider::name)
                        .toList());
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
    void loadsConversationInventoryThroughConfiguredService() throws Exception {
        Chat stored = chats.insert(Chat.builder()
                .id(0)
                .source(Source.codexCli)
                .title("Stored")
                .importedAt("2026-07-08T00:00:00Z")
                .externalConversationId("stored-id")
                .sourceUri("source://stored")
                .build());
        chatmap.application.port.provider.ChatProvider provider = new chatmap.application.port.provider.ChatProvider() {
            @Override
            public String name() {
                return "Codex (CLI)";
            }

            @Override
            public java.util.Optional<chatmap.application.model.ImportedChat> latestChat() {
                return java.util.Optional.empty();
            }

            @Override
            public List<ConversationCandidate> listChats() {
                return List.of(
                        new ConversationCandidate(Source.codexCli, "stored-id",
                                "Stored", "source://stored", null),
                        new ConversationCandidate(Source.codexCli, "missing-id",
                                "Missing", "source://missing", null));
            }
        };
        ChatMapController inventoryController = new ChatMapController(
                new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader()),
                new ExportService(chats, messages, new ProjectRepository(conn), new TagRepository(conn), new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter()),
                new SearchService(new SearchRepository(conn)),
                projectService,
                tagService,
                new SummaryService(chats, messages, new SummaryRepository(conn), new TagRepository(conn),
                        summaryBackend()),
                new LiveChatFetchService(List.of(provider), new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader()), chats),
                null,
                new ConversationInventoryService(List.of(provider), chats));

        ConversationInventory inventory = inventoryController.conversationInventory();

        assertEquals(1, inventory.providers().size());
        assertEquals(stored.id(), inventory.providers().getFirst().conversations().getFirst().importedChatId());
        assertEquals(false, inventory.providers().getFirst().conversations().get(1).alreadyImported());
    }

    @Test
    void executePromptThrowsWhenNotConfigured() {
        assertThrows(IllegalStateException.class, () -> controller.executePrompt("fake", "hi"));
    }

    @Test
    void executePromptDelegatesToConfiguredService() throws Exception {
        LlmProvider fakeBackend = new LlmProvider() {
            @Override
            public LlmResponse execute(ModelTarget target, LlmRequest request) {
                return new LlmResponse("pong", new BackendId("Fake"), Duration.ZERO, target, null);
            }

            @Override
            public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
                return Set.of();
            }
        };
        PromptService promptService = new PromptService(
                providers(fakeBackend),
                new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader()),
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC),
                tempDir);
        ChatMapController promptController = new ChatMapController(
                new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader()),
                new ExportService(chats, messages, new ProjectRepository(conn), new TagRepository(conn), new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter()),
                new SearchService(new SearchRepository(conn)),
                projectService,
                tagService,
                new SummaryService(chats, messages, new SummaryRepository(conn), new TagRepository(conn),
                        summaryBackend()),
                new LiveChatFetchService(List.of(), new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader()), chats),
                null,
                null,
                promptService);

        PromptResult result = promptController.executePrompt("claude", "ping");

        assertEquals("pong", result.response());
    }

    @Test
    void routePromptClassifiesRoutesAndPersistsTheTurn() throws Exception {
        ChatMapController promptController = promptController(recordingProvider("ok"));
        Project project = promptController.createProject("Foo");

        PromptRoutingResult result = promptController.routePrompt(project, "foo-current-task",
                "Explain this compile error.");

        assertEquals(PromptClassificationLevel.LIGHTWEIGHT, result.classification().level());
        assertEquals(ModelTarget.ollamaQwen257b.id(), result.route().target().id());
        assertEquals("ok " + ModelTarget.ollamaQwen257b.id(), result.promptResult().response());
        assertEquals(project.id(), result.projectContext().projectId());
        assertEquals("foo-current-task", result.conversationContext().id());
        assertEquals(project.id(), chats.findById(result.promptResult().chatId()).orElseThrow().projectId());
    }

    @Test
    void routePromptDisplaysActualMonsterTargetFromRouterResult() throws Exception {
        ChatMapController promptController = promptController(recordingProvider("ok"));
        Project project = promptController.createProject("Foo");

        PromptRoutingResult result = promptController.routePrompt(project, "foo-current-task",
                "Review architecture across multiple modules.");

        assertEquals(PromptClassificationLevel.MONSTER, result.classification().level());
        assertEquals(ModelTarget.claude.id(), result.route().target().id());
        assertEquals(Channel.claudeCli.name(), result.route().target().channel().name());
    }

    @Test
    void routePromptRejectsMissingProjectConversationAndPrompt() throws Exception {
        ChatMapController promptController = promptController(recordingProvider("ok"));
        Project project = promptController.createProject("Foo");

        assertThrows(IllegalArgumentException.class,
                () -> promptController.routePrompt(null, "foo-current-task", "hi"));
        assertThrows(IllegalArgumentException.class,
                () -> promptController.routePrompt(project, " ", "hi"));
        assertThrows(IllegalArgumentException.class,
                () -> promptController.routePrompt(project, "foo-current-task", " "));
    }

    @Test
    void routePromptSurfacesProviderFailure() throws Exception {
        ChatMapController promptController = promptController(new LlmProvider() {
            @Override
            public LlmResponse execute(ModelTarget target, LlmRequest request) {
                throw new IllegalStateException("provider unavailable");
            }

            @Override
            public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
                return Set.of();
            }
        });
        Project project = promptController.createProject("Foo");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> promptController.routePrompt(project, "foo-current-task", "Explain this compile error."));

        assertEquals("provider unavailable", thrown.getMessage());
    }

    @Test
    void routePromptKeepsFooAndBarProjectsSeparate() throws Exception {
        ChatMapController promptController = promptController(recordingProvider("ok"));
        Project foo = promptController.createProject("Foo");
        Project bar = promptController.createProject("Bar");

        PromptRoutingResult fooResult = promptController.routePrompt(foo, "current",
                "Explain this compile error in Foo.java.");
        PromptRoutingResult barResult = promptController.routePrompt(bar, "current",
                "Explain this compile error in Bar.java.");

        assertEquals(foo.id(), fooResult.projectContext().projectId());
        assertEquals(bar.id(), barResult.projectContext().projectId());
        assertEquals(foo.id(), chats.findById(fooResult.promptResult().chatId()).orElseThrow().projectId());
        assertEquals(bar.id(), chats.findById(barResult.promptResult().chatId()).orElseThrow().projectId());
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

    @Test
    void createsAndListsProjectsAndTags() throws Exception {
        Project project = controller.createProject(" Project Beta ");
        controller.createProject("Alpha");
        Tag tag = controller.createTag(" Tag Beta ");
        controller.createTag("Alpha");

        assertEquals("Project Beta", project.name());
        assertEquals(List.of("Alpha", "Project Beta"),
                controller.listProjects().stream().map(Project::name).toList());
        assertEquals("Tag Beta", tag.name());
        assertEquals(List.of("Alpha", "Tag Beta"),
                controller.listTags().stream().map(Tag::name).toList());
    }

    @Test
    void assignsFiltersAndClearsChatProject() throws Exception {
        Chat assigned = insertChat("Assigned", "project target");
        Chat outside = insertChat("Outside", "outside target");
        Project project = controller.createProject("Project");

        controller.assignProject(assigned.id(), project.id());
        ChatListState.Snapshot filtered = controller.filterByProject(project.id());

        assertEquals(List.of(assigned.id()), chatIds(filtered));
        assertEquals(project.id(), filtered.currentItems().getFirst().chat().projectId());

        ChatListState.Snapshot clearedFilters = controller.clearFilters();
        assertEquals(List.of(assigned.id(), outside.id()), chatIds(clearedFilters));

        controller.selectChat(assigned.id());
        ChatListState.Snapshot clearedProject = controller.clearProject(assigned.id());
        assertEquals(null, clearedProject.currentItems().getFirst().chat().projectId());
    }

    @Test
    void addsRemovesAndFiltersChatTag() throws Exception {
        Chat tagged = insertChat("Tagged", "tag target");
        insertChat("Outside", "outside target");
        Tag tag = controller.createTag("MVP");

        controller.addTag(tagged.id(), tag.id());
        ChatListState.Snapshot filtered = controller.filterByTag(tag.id());

        assertEquals(List.of(tagged.id()), chatIds(filtered));
        assertEquals(List.of(tag), filtered.currentItems().getFirst().tags());

        ChatListState.Snapshot afterRemoval = controller.removeTag(tagged.id(), tag.id());
        assertTrue(afterRemoval.currentItems().isEmpty());
    }

    @Test
    void searchRespectsActiveProjectFilter() throws Exception {
        Chat included = insertChat("Included", "shared search target");
        insertChat("Excluded", "shared search target");
        Project project = controller.createProject("Project");
        controller.assignProject(included.id(), project.id());

        controller.filterByProject(project.id());
        ChatListState.Snapshot searched = controller.searchChats("shared");

        assertEquals(List.of(included.id()), chatIds(searched));
    }

    @Test
    void readOperationsDoNotBlockDuringLongTasks() throws Exception {
        Chat chat = insertChat("Concurrency Test", "Read message");
        java.util.concurrent.CountDownLatch slowProviderStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch slowProviderCanFinish = new java.util.concurrent.CountDownLatch(1);

        chatmap.application.port.provider.ChatProvider slowProvider = new chatmap.application.port.provider.ChatProvider() {
            @Override
            public String name() {
                return "Slow Test Provider";
            }

            @Override
            public java.util.Optional<chatmap.application.model.ImportedChat> latestChat()
                    throws chatmap.application.port.provider.ChatProviderException {
                slowProviderStarted.countDown();
                boolean completed;
                try {
                    completed = slowProviderCanFinish.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new chatmap.application.port.provider.ChatProviderException("Interrupted", interrupted);
                }
                if (!completed) {
                    throw new chatmap.application.port.provider.ChatProviderException("Slow provider await timed out");
                }
                return java.util.Optional.empty();
            }
        };

        LiveChatFetchService slowFetchService = new LiveChatFetchService(List.of(slowProvider), new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader()), chats);
        ChatMapController asyncController = new ChatMapController(
                new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader()),
                new ExportService(chats, messages, new ProjectRepository(conn), new TagRepository(conn), new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter()),
                new SearchService(new SearchRepository(conn)),
                projectService,
                tagService,
                new SummaryService(chats, messages, new SummaryRepository(conn), new TagRepository(conn), summaryBackend()),
                slowFetchService);

        java.util.concurrent.CompletableFuture<ChatListState.Snapshot> fetchTask =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return asyncController.fetchLatestChat();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        assertTrue(slowProviderStarted.await(2, java.util.concurrent.TimeUnit.SECONDS), "Slow provider should start running");

        // UI thread read query must execute immediately without blocking on the slow provider
        long startTime = System.currentTimeMillis();
        ChatExportModel details = asyncController.loadChatDetails(chat.id()).orElseThrow();
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(chat, details.chat());
        assertTrue(duration < 1000, "Read operation loadChatDetails took " + duration + " ms, expected under 1000 ms");

        // Release the slow provider and await completion
        slowProviderCanFinish.countDown();
        fetchTask.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static List<Long> chatIds(ChatListState.Snapshot snapshot) {
        return snapshot.currentItems().stream()
                .map(result -> result.chatId())
                .toList();
    }

    private static LlmBackend summaryBackend() {
        return request -> new LlmResponse("summary", new BackendId("Summary"), Duration.ZERO,
                ModelTarget.claude, null);
    }

    private static Map<Channel, LlmProvider> providers(LlmProvider claudeProvider) {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        LlmProvider noop = new LlmProvider() {
            @Override
            public LlmResponse execute(ModelTarget target, LlmRequest request) {
                throw new AssertionError("unexpected provider call for " + target);
            }

            @Override
            public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
                return Set.of();
            }
        };
        for (Channel id : Channel.values()) {
            providers.put(id, noop);
        }
        providers.put(Channel.claudeCli, claudeProvider);
        return providers;
    }

    private ChatMapController promptController(LlmProvider provider) throws Exception {
        ImportService importService = new ImportService(chats, messages, new TransactionRunner(conn),
                new chatmap.infrastructure.importer.DefaultConversationFileReader());
        ProjectRepository projects = new ProjectRepository(conn);
        TagRepository tags = new TagRepository(conn);
        PromptService promptService = new PromptService(
                allProviders(provider),
                importService,
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC),
                tempDir);
        PromptRouterService router = new PromptRouterService(
                new DeterministicPromptClassifier(),
                PromptRouteSelector.defaults(),
                promptService,
                new ProjectService(projects, chats),
                new PromptRouteRepository(conn),
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC));
        return new ChatMapController(
                importService,
                new ExportService(chats, messages, projects, tags, new chatmap.infrastructure.exporter.MarkdownExporter(),
                        new chatmap.infrastructure.exporter.HandoffExporter()),
                new SearchService(new SearchRepository(conn)),
                new ProjectService(projects, chats),
                new TagService(tags, chats),
                new SummaryService(chats, messages, new SummaryRepository(conn), tags, summaryBackend()),
                new LiveChatFetchService(List.of(), importService, chats),
                null,
                null,
                promptService,
                router);
    }

    private static LlmProvider recordingProvider(String responsePrefix) {
        return new LlmProvider() {
            @Override
            public LlmResponse execute(ModelTarget target, LlmRequest request) {
                return new LlmResponse(responsePrefix + " " + target.id(), new BackendId("Fake"),
                        Duration.ZERO, target, target.id() + "-session");
            }

            @Override
            public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
                return Set.of();
            }
        };
    }

    private static Map<Channel, LlmProvider> allProviders(LlmProvider provider) {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) {
            providers.put(channel, provider);
        }
        return providers;
    }

    private Chat insertChat(String title, String text) throws Exception {
        Chat chat = chats.insert(Chat.builder()
                                         .id(0)
                                         .projectId(null)
                                         .source(Source.plainText)
                                         .title(title)
                                         .createdAt(null)
                                         .updatedAt(null)
                                         .importedAt("2026-07-08T00:00:00Z")
                                         .archived(false)
                                         .build());
        messages.insert(new Message(0, chat.id(), MessageRole.unknown, text, 0, null, null));
        return chat;
    }
}
