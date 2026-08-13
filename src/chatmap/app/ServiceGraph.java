package chatmap.app;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chatmap.application.port.ai.AiBackend;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.provider.ChatProvider;
import chatmap.application.port.ai.AiBackendUnsupportedRequestException;
import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.persistence.MessageStore;
import chatmap.application.port.persistence.ProjectStore;
import chatmap.application.port.persistence.SearchStore;
import chatmap.application.port.persistence.SummaryStore;
import chatmap.application.port.persistence.TagStore;
import chatmap.application.service.ConversationInventoryService;
import chatmap.application.service.ExportService;
import chatmap.application.service.ImportService;
import chatmap.application.service.LiveChatFetchService;
import chatmap.application.service.ProjectService;
import chatmap.application.service.PromptService;
import chatmap.application.service.SearchService;
import chatmap.application.service.SummaryService;
import chatmap.application.service.TagService;
import chatmap.config.ChatMapPaths.ResolvedPaths;
import chatmap.infrastructure.exporter.HandoffExporter;
import chatmap.infrastructure.exporter.MarkdownExporter;
import chatmap.application.service.ChatGptArchiveImportService;
import chatmap.infrastructure.importer.DefaultConversationFileReader;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
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
        PromptService promptService) implements AutoCloseable {

    /**
     * Optional outside-world capabilities. Core import/search/export can run with
     * {@link #none()}; UI and summarize entry points opt into concrete providers.
     */
    public record Integrations(
            List<ChatProvider> chatProviders,
            AiBackend summaryBackend,
            Map<String, AiBackend> promptBackends) {
        public Integrations {
            chatProviders = List.copyOf(Objects.requireNonNull(chatProviders, "chatProviders"));
            summaryBackend = Objects.requireNonNull(summaryBackend, "summaryBackend");
            promptBackends = Map.copyOf(Objects.requireNonNull(promptBackends, "promptBackends"));
        }

        public Integrations(List<ChatProvider> chatProviders, AiBackend summaryBackend) {
            this(chatProviders, summaryBackend, Map.of());
        }

        public static Integrations none() {
            return new Integrations(List.of(), new UnavailableSummaryBackend(), Map.of());
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
                integrations.promptBackends(),
                importService,
                java.time.Clock.systemUTC(),
                paths.transcriptsDirectory());

        return new ServiceGraph(connection, chats, messages, projects, tags, summaries, search,
                importService, archiveImportService, conversationInventoryService,
                summaryService, liveChatFetchService,
                exportService, searchService, projectService, tagService, promptService);
    }

    /** Closes the underlying connection. */
    @Override
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static final class UnavailableSummaryBackend implements AiBackend {
        @Override
        public AiResponse ask(AiRequest request) {
            throw new AiBackendUnsupportedRequestException(
                    "No summary AI backend configured.", new BackendId("unavailable"));
        }

        @Override
        public String toString() {
            return "unavailable summary backend";
        }
    }
}
