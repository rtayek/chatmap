package chatmap.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import chatmap.backend.ChatProvider;
import chatmap.backend.ClaudeCliBackend;
import chatmap.backend.DefaultChatProviders;
import chatmap.storage.ChatRepository;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.SearchRepository;
import chatmap.storage.SummaryRepository;
import chatmap.storage.TagRepository;

/**
 * The single wiring of repositories and services over one SQLite connection.
 * Shared by the JavaFX app and the CLIs so the two never drift; change how a
 * repository or service is constructed in one place here.
 */
public record ServiceGraph(
        Connection connection,
        ChatRepository chats,
        MessageRepository messages,
        ProjectRepository projects,
        TagRepository tags,
        SummaryRepository summaries,
        SearchRepository search,
        ImportService importService,
        ChatGptArchiveImportService archiveImportService,
        SummaryService summaryService,
        LiveChatFetchService liveChatFetchService,
        ExportService exportService,
        SearchService searchService,
        ProjectService projectService,
        TagService tagService) implements AutoCloseable {

    /** Builds every repository and service over {@code connection}. Caller owns the connection. */
    public static ServiceGraph create(Connection connection) {
        ChatRepository chats = new ChatRepository(connection);
        MessageRepository messages = new MessageRepository(connection);
        ProjectRepository projects = new ProjectRepository(connection);
        TagRepository tags = new TagRepository(connection);
        SummaryRepository summaries = new SummaryRepository(connection);
        SearchRepository search = new SearchRepository(connection);

        ImportService importService = new ImportService(chats, messages);
        ChatGptArchiveImportService archiveImportService =
                new ChatGptArchiveImportService(importService);
        SummaryService summaryService = new SummaryService(chats, messages, summaries, tags,
                new ClaudeCliBackend(Duration.ofMinutes(3)));
        List<ChatProvider> providers = DefaultChatProviders.ordered();
        LiveChatFetchService liveChatFetchService =
                new LiveChatFetchService(providers, importService, chats);
        ExportService exportService = new ExportService(chats, messages, projects, tags);
        SearchService searchService = new SearchService(search);
        ProjectService projectService = new ProjectService(projects, chats);
        TagService tagService = new TagService(tags, chats);

        return new ServiceGraph(connection, chats, messages, projects, tags, summaries, search,
                importService, archiveImportService, summaryService, liveChatFetchService,
                exportService, searchService, projectService, tagService);
    }

    /** Closes the underlying connection. */
    @Override
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
