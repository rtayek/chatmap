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
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.backend.ClaudeCliBackend;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.LiveChatFetchService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
import chatmap.service.SummaryService;
import chatmap.service.TagService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.SearchRepository;
import chatmap.storage.SummaryRepository;
import chatmap.storage.TagRepository;

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
        ImportService importService = new ImportService(chats, messages);
        SummaryService summaryService = new SummaryService(chats, messages,
                new SummaryRepository(conn), tags,
                new ClaudeCliBackend(java.time.Duration.ofMinutes(3)));
        LiveChatFetchService liveChatFetchService =
                new LiveChatFetchService(java.util.List.of(), importService, chats);
        controller = new ChatMapController(
                importService,
                new ExportService(chats, messages, projects, tags),
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
                "Claude (web)", "ChatGPT (web)", "Gemini (web)",
                "Claude Code (CLI)", "Codex (CLI)", "Gemini (CLI)"),
                ChatMapControllerFactory.defaultIntegrations()
                        .chatProviders()
                        .stream()
                        .map(chatmap.backend.ChatProvider::name)
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

        chatmap.backend.ChatProvider slowProvider = new chatmap.backend.ChatProvider() {
            @Override
            public String name() {
                return "Slow Test Provider";
            }

            @Override
            public java.util.Optional<chatmap.importer.ImportedChat> latestChat() throws Exception {
                slowProviderStarted.countDown();
                boolean completed = slowProviderCanFinish.await(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!completed) {
                    throw new java.util.concurrent.TimeoutException("Slow provider await timed out");
                }
                return java.util.Optional.empty();
            }
        };

        LiveChatFetchService slowFetchService = new LiveChatFetchService(List.of(slowProvider), new ImportService(chats, messages), chats);
        ChatMapController asyncController = new ChatMapController(
                new ImportService(chats, messages),
                new ExportService(chats, messages, new ProjectRepository(conn), new TagRepository(conn)),
                new SearchService(new SearchRepository(conn)),
                projectService,
                tagService,
                new SummaryService(chats, messages, new SummaryRepository(conn), new TagRepository(conn), new ClaudeCliBackend(java.time.Duration.ofMinutes(3))),
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

    private Chat insertChat(String title, String text) throws Exception {
        Chat chat = chats.insert(new Chat(
                0, null, Source.plainText, title, null, null, "2026-07-08T00:00:00Z", false));
        messages.insert(new Message(0, chat.id(), "unknown", text, 0, null, null));
        return chat;
    }
}
