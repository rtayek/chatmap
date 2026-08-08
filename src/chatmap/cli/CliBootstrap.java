package chatmap.cli;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.file.Files;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.ChatMapPaths.ResolvedPaths;
import chatmap.service.ChatGptArchiveImportService;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.LiveChatFetchService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
import chatmap.service.ServiceGraph;
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

    /**
     * A CLI's ChatMap home paths plus the shared {@link ServiceGraph}. The flat
     * accessors delegate to the graph so the repository/service wiring lives in
     * exactly one place ({@link ServiceGraph#create}).
     */
    public record CliContext(ServiceGraph services, ResolvedPaths paths) implements AutoCloseable {

        public Connection connection() {
            return services.connection();
        }

        public ChatRepository chats() {
            return services.chats();
        }

        public MessageRepository messages() {
            return services.messages();
        }

        public ProjectRepository projects() {
            return services.projects();
        }

        public TagRepository tags() {
            return services.tags();
        }

        public SummaryRepository summaries() {
            return services.summaries();
        }

        public SearchRepository search() {
            return services.search();
        }

        public ImportService importService() {
            return services.importService();
        }

        public ChatGptArchiveImportService archiveImportService() {
            return services.archiveImportService();
        }

        public SummaryService summaryService() {
            return services.summaryService();
        }

        public LiveChatFetchService liveChatFetchService() {
            return services.liveChatFetchService();
        }

        public ExportService exportService() {
            return services.exportService();
        }

        public SearchService searchService() {
            return services.searchService();
        }

        public ProjectService projectService() {
            return services.projectService();
        }

        public TagService tagService() {
            return services.tagService();
        }

        @Override
        public void close() throws SQLException {
            services.close();
        }
    }

    public static CliContext open(String[] args) throws IOException, SQLException {
        return open(ChatMapPaths.parse(args));
    }

    public static CliContext open(ParsedArguments parsedArguments) throws IOException, SQLException {
        ResolvedPaths paths = parsedArguments.paths();
        Files.createDirectories(paths.homeDirectory());
        Connection conn = new Database("jdbc:sqlite:" + paths.databasePath()).openAndInitialize();
        return createContext(conn, paths);
    }

    public static CliContext createContext(Connection connection, ResolvedPaths paths) {
        return new CliContext(ServiceGraph.create(connection), paths);
    }
}
