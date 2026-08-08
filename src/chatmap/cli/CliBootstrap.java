package chatmap.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import chatmap.backend.ChatProvider;
import chatmap.backend.ClaudeCliBackend;
import chatmap.backend.DefaultChatProviders;
import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.ChatMapPaths.ResolvedPaths;
import chatmap.service.ChatGptArchiveImportService;
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

/** Shared bootstrapping helper for CLI entry points. */
public final class CliBootstrap {

    private CliBootstrap() {
    }

    public record CliContext(
            Connection connection,
            ResolvedPaths paths,
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

        @Override
        public void close() throws SQLException {
            if (connection != null) {
                connection.close();
            }
        }
    }

    public static CliContext open(String[] args) throws IOException, SQLException {
        ParsedArguments parsedArguments = ChatMapPaths.parse(args);
        return open(parsedArguments);
    }

    public static CliContext open(ParsedArguments parsedArguments) throws IOException, SQLException {
        ResolvedPaths paths = parsedArguments.paths();
        Path dbPath = paths.databasePath();
        Files.createDirectories(paths.homeDirectory());

        Connection conn = new Database("jdbc:sqlite:" + dbPath).openAndInitialize();
        return createContext(conn, paths);
    }

    public static CliContext createContext(Connection connection, ResolvedPaths paths) {
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

        return new CliContext(
                connection,
                paths,
                chats,
                messages,
                projects,
                tags,
                summaries,
                search,
                importService,
                archiveImportService,
                summaryService,
                liveChatFetchService,
                exportService,
                searchService,
                projectService,
                tagService);
    }
}
