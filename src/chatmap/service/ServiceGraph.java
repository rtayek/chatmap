package chatmap.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chatmap.backend.ai.AiBackend;
import chatmap.backend.ai.AiRequest;
import chatmap.backend.ai.AiResponse;
import chatmap.backend.ai.BackendId;
import chatmap.backend.providers.ChatProvider;
import chatmap.backend.ai.AiBackendUnsupportedRequestException;
import chatmap.storage.ChatRepository;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.SearchRepository;
import chatmap.storage.SummaryRepository;
import chatmap.storage.TagRepository;
import chatmap.storage.TransactionRunner;

/**
 * The single wiring of repositories and services over one SQLite connection.
 * Shared by the JavaFX app and the CLIs so the two never drift; change how a
 * repository or service is constructed in one place here.
 */
public record ServiceGraph(
        Connection connection,
        Object databaseLock,
        ChatRepository chats,
        MessageRepository messages,
        ProjectRepository projects,
        TagRepository tags,
        SummaryRepository summaries,
        SearchRepository search,
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
    public static ServiceGraph create(Connection connection) {
        return create(connection, Integrations.none());
    }

    /** Builds every repository and service over {@code connection}. Caller owns the connection. */
    public static ServiceGraph create(Connection connection, Integrations integrations) {
        Objects.requireNonNull(integrations, "integrations");
        ChatRepository chats = new ChatRepository(connection);
        MessageRepository messages = new MessageRepository(connection);
        ProjectRepository projects = new ProjectRepository(connection);
        TagRepository tags = new TagRepository(connection);
        SummaryRepository summaries = new SummaryRepository(connection);
        SearchRepository search = new SearchRepository(connection);
        Object databaseLock = new Object();

        TransactionRunner transactionRunner = new TransactionRunner(connection, databaseLock);
        ImportService importService = new ImportService(chats, messages, transactionRunner);
        ChatGptArchiveImportService archiveImportService =
                new ChatGptArchiveImportService(importService);
        ConversationInventoryService conversationInventoryService =
                new ConversationInventoryService(integrations.chatProviders(), chats);
        SummaryService summaryService = new SummaryService(chats, messages, summaries, tags,
                integrations.summaryBackend(), transactionRunner);
        LiveChatFetchService liveChatFetchService =
                new LiveChatFetchService(integrations.chatProviders(), importService, chats, databaseLock);
        ExportService exportService = new ExportService(chats, messages, projects, tags);
        SearchService searchService = new SearchService(search);
        ProjectService projectService = new ProjectService(projects, chats);
        TagService tagService = new TagService(tags, chats);
        PromptService promptService = new PromptService(
                integrations.promptBackends(),
                importService,
                java.time.Clock.systemUTC());

        return new ServiceGraph(connection, databaseLock, chats, messages, projects, tags, summaries, search,
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
