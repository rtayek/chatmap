package chatmap.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import chatmap.application.service.ExportService;
import chatmap.application.service.MemoryReadService;
import chatmap.application.service.SearchService;
import chatmap.infrastructure.document.FilesystemProjectDocumentReader;
import chatmap.infrastructure.exporter.HandoffExporter;
import chatmap.infrastructure.exporter.MarkdownExporter;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.ReadOnlyDatabase;
import chatmap.infrastructure.persistence.sqlite.SearchRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;

/** Minimal read-only composition root for the MCP adapter. */
public final class ReadOnlyMcpRuntime implements AutoCloseable {

    private ReadOnlyMcpRuntime(Connection connection, MemoryReadService memoryReads) {
        this.connection = connection;
        this.memoryReads = memoryReads;
    }

    public static ReadOnlyMcpRuntime open(Path home, long projectId, Path projectRoot) throws SQLException {
        Path normalizedHome = home.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedHome)) {
            throw new SQLException("Read-only ChatMap home does not exist: " + normalizedHome);
        }
        Path databasePath = normalizedHome.resolve("chatmap.db");
        Connection connection = ReadOnlyDatabase.open(databasePath);
        try {
            ChatRepository chats = new ChatRepository(connection);
            MessageRepository messages = new MessageRepository(connection);
            ProjectRepository projects = new ProjectRepository(connection);
            if (projects.findById(projectId).isEmpty()) {
                throw new SQLException("Configured project does not exist: " + projectId);
            }
            TagRepository tags = new TagRepository(connection);
            ExportService exportService = new ExportService(chats, messages, projects, tags,
                    new MarkdownExporter(), new HandoffExporter());
            SearchService searchService = new SearchService(new SearchRepository(connection));
            MemoryReadService memoryReads = new MemoryReadService(searchService, exportService,
                    new FilesystemProjectDocumentReader(projectRoot), projectId);
            return new ReadOnlyMcpRuntime(connection, memoryReads);
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public MemoryReadService memoryReads() {
        return memoryReads;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private final Connection connection;
    private final MemoryReadService memoryReads;
}
