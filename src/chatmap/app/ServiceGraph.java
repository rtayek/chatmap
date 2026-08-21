package chatmap.app;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import chatmap.application.port.llm.LlmBackend;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.Channel;
import chatmap.application.port.provider.ChatProvider;
import chatmap.application.port.llm.LlmBackendUnsupportedRequestException;
import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.persistence.MessageStore;
import chatmap.application.port.persistence.ProjectStore;
import chatmap.application.port.persistence.PromptRouteStore;
import chatmap.application.port.persistence.SearchStore;
import chatmap.application.port.persistence.SummaryStore;
import chatmap.application.port.persistence.TagStore;
import chatmap.application.service.ConversationInventoryService;
import chatmap.application.service.ExportService;
import chatmap.application.service.ImportService;
import chatmap.application.service.LiveChatFetchService;
import chatmap.application.service.ProjectService;
import chatmap.application.service.PromptService;
import chatmap.application.service.PromptRouterService;
import chatmap.application.service.DeterministicPromptClassifier;
import chatmap.application.service.PromptRouteSelector;
import chatmap.application.service.SearchService;
import chatmap.application.service.SummaryService;
import chatmap.application.service.TagService;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.infrastructure.exporter.HandoffExporter;
import chatmap.infrastructure.exporter.MarkdownExporter;
import chatmap.application.service.ChatGptArchiveImportService;
import chatmap.infrastructure.importer.DefaultConversationFileReader;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.PromptRouteRepository;
import chatmap.infrastructure.persistence.sqlite.SearchRepository;
import chatmap.infrastructure.persistence.sqlite.SummaryRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;

/**
 * The single wiring of repositories and services over one SQLite connection.
 * Shared by the JavaFX app and the CLIs so the two never drift; change how a
 * repository or service is constructed in one place here.
 */
public record ServiceGraph(
        Connection connection,
        ChatStore chats,
        MessageStore messages,
        ProjectStore projects,
        PromptRouteStore promptRoutes,
        TagStore tags,
        SummaryStore summaries,
        SearchStore search,
        ImportService importService,
        ChatGptArchiveImportService archiveImportService,
        ConversationInventoryService conversationInventoryService,
        SummaryService summaryService,
        LiveChatFetchService liveChatFetchService,
        ExportService exportService,
        SearchService searchService,
        ProjectService projectService,
        TagService tagService,
        PromptService promptService,
        PromptRouterService promptRouterService) implements AutoCloseable {

    /**
     * Optional outside-world capabilities. Core import/search/export can run with
     * {@link #none()}; UI and summarize entry points opt into concrete providers.
     */
    public record Integrations(
            List<ChatProvider> chatProviders,
            LlmBackend summaryBackend,
            Map<Channel, LlmProvider> promptProviders) {
        public Integrations {
            chatProviders = List.copyOf(Objects.requireNonNull(chatProviders, "chatProviders"));
            summaryBackend = Objects.requireNonNull(summaryBackend, "summaryBackend");
            promptProviders = Map.copyOf(Objects.requireNonNull(promptProviders, "promptProviders"));
        }

        public Integrations(List<ChatProvider> chatProviders, LlmBackend summaryBackend) {
            this(chatProviders, summaryBackend, unavailablePromptProviders());
        }

        public static Integrations none() {
            return new Integrations(List.of(), new UnavailableSummaryBackend(), unavailablePromptProviders());
        }
    }

    /** Builds every repository and service over {@code connection}. Caller owns the connection. */
    public static ServiceGraph create(Connection connection, ResolvedPaths paths) {
        return create(connection, Integrations.none(), paths);
    }

    /** Builds every repository and service over {@code connection}. Caller owns the connection. */
    public static ServiceGraph create(Connection connection, Integrations integrations, ResolvedPaths paths) {
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(paths, "paths");
        ChatRepository chats = new ChatRepository(connection);
        MessageRepository messages = new MessageRepository(connection);
        ProjectRepository projects = new ProjectRepository(connection);
        PromptRouteRepository promptRoutes = new PromptRouteRepository(connection);
        TagRepository tags = new TagRepository(connection);
        SummaryRepository summaries = new SummaryRepository(connection);
        SearchRepository search = new SearchRepository(connection);
        TransactionRunner transactionRunner = new TransactionRunner(connection);
        ImportService importService = new ImportService(
                chats, messages, transactionRunner, new DefaultConversationFileReader());
        ChatGptArchiveImportService archiveImportService =
                new ChatGptArchiveImportService(new chatmap.infrastructure.importer.ChatGptArchiveImporter(), importService);
        ConversationInventoryService conversationInventoryService =
                new ConversationInventoryService(integrations.chatProviders(), chats);
        SummaryService summaryService = new SummaryService(chats, messages, summaries, tags,
                integrations.summaryBackend(), transactionRunner);
        LiveChatFetchService liveChatFetchService =
                new LiveChatFetchService(integrations.chatProviders(), importService, chats);
        ExportService exportService = new ExportService(
                chats, messages, projects, tags, new MarkdownExporter(), new HandoffExporter());
        SearchService searchService = new SearchService(search);
        ProjectService projectService = new ProjectService(projects, chats);
        TagService tagService = new TagService(tags, chats);
        PromptService promptService = new PromptService(
                integrations.promptProviders(),
                importService,
                java.time.Clock.systemUTC(),
                paths.transcriptsDirectory());
        PromptRouterService promptRouterService = new PromptRouterService(
                new DeterministicPromptClassifier(),
                PromptRouteSelector.defaults(),
                promptService,
                projectService,
                promptRoutes,
                java.time.Clock.systemUTC());

        return new ServiceGraph(connection, chats, messages, projects, promptRoutes, tags, summaries, search,
                importService, archiveImportService, conversationInventoryService,
                summaryService, liveChatFetchService,
                exportService, searchService, projectService, tagService, promptService, promptRouterService);
    }

    /** Closes the underlying connection. */
    @Override
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static final class UnavailableSummaryBackend implements LlmBackend {
        @Override
        public LlmResponse ask(LlmRequest request) {
            throw new LlmBackendUnsupportedRequestException(
                    "No summary LLM backend configured.", new BackendId("unavailable"));
        }

        @Override
        public String toString() {
            return "unavailable summary backend";
        }
    }

    private static Map<Channel, LlmProvider> unavailablePromptProviders() {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        LlmProvider unavailable = new UnavailablePromptProvider();
        for (Channel id : Channel.values()) {
            providers.put(id, unavailable);
        }
        return providers;
    }

    private static final class UnavailablePromptProvider implements LlmProvider {
        @Override
        public LlmResponse execute(ModelTarget target, LlmRequest request) {
            throw new LlmBackendUnsupportedRequestException(
                    "No LLM provider configured for target " + target.id() + ".", new BackendId(target.displayName()));
        }

        @Override
        public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
            return Set.of();
        }
    }
}
