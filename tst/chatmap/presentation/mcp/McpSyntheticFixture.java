package chatmap.presentation.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import chatmap.infrastructure.persistence.sqlite.Database;

final class McpSyntheticFixture {

    static Fixture create(Path directory) throws SQLException, IOException {
        Path home = Files.createDirectories(directory.resolve("home"));
        Path projectRoot = Files.createDirectories(directory.resolve("project"));
        Path documentDirectory = Files.createDirectories(projectRoot.resolve(".llm"));
        Path workingContext = documentDirectory.resolve("working-context.md");
        Files.writeString(workingContext, workingContextText(), StandardCharsets.UTF_8);
        Path database = home.resolve("chatmap.db");
        try (Connection connection = new Database("jdbc:sqlite:" + database).openAndInitialize()) {
            insertProjects(connection);
            insertChats(connection);
            insertMessages(connection);
        }
        return new Fixture(home, projectRoot, database, workingContext, oversizedText());
    }

    static String workingContextText() {
        return """
                # Synthetic project working context

                Current task: expose read-only memory access through three bounded MCP tools.
                Governing prototype storage decision: keep SQLite.
                Deferred: semantic extraction.
                Evidence must cite stored message ids and this mutable document identity.
                """;
    }

    static String oversizedText() {
        return "Oversized Unicode evidence: " + "alpha🙂β".repeat(2200) + " END";
    }

    private static void insertProjects(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO projects (id, name, description, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)")) {
            insertProject(statement, selectedProjectId, "Synthetic selected project");
            insertProject(statement, otherProjectId, "Synthetic other project");
        }
    }

    private static void insertProject(PreparedStatement statement, long id, String name) throws SQLException {
        statement.setLong(1, id);
        statement.setString(2, name);
        statement.setString(3, "Synthetic fixture only");
        statement.setString(4, timestamp);
        statement.setString(5, timestamp);
        statement.executeUpdate();
    }

    private static void insertChats(Connection connection) throws SQLException {
        String sql = "INSERT INTO chats (id, projectId, source, title, createdAt, updatedAt, importedAt, "
                + "archived, externalConversationId, sourceUri, contentHash) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            insertChat(statement, historyChatId, selectedProjectId, "Synthetic architecture history", "fixture-history");
            insertChat(statement, currentChatId, selectedProjectId, "Synthetic current work", "fixture-current");
            insertChat(statement, otherChatId, otherProjectId, "Forbidden other project", "fixture-other");
        }
    }

    private static void insertChat(PreparedStatement statement, long id, long projectId, String title,
            String identity) throws SQLException {
        statement.setLong(1, id);
        statement.setLong(2, projectId);
        statement.setString(3, "plainText");
        statement.setString(4, title);
        statement.setString(5, timestamp);
        statement.setString(6, timestamp);
        statement.setString(7, timestamp);
        statement.setString(8, identity);
        statement.setString(9, "synthetic://" + identity);
        statement.setString(10, "stored-" + identity + "-hash");
        statement.executeUpdate();
    }

    private static void insertMessages(Connection connection) throws SQLException {
        String sql = "INSERT INTO messages (id, chatId, role, text, sequence, timestamp, rawJson) "
                + "VALUES (?, ?, ?, ?, ?, ?, NULL)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            insertMessage(statement, proposalMessageId, historyChatId, 0,
                    "Earlier proposal: use hosted storage for shared memory.");
            insertMessage(statement, decisionMessageId, historyChatId, 1,
                    "Later decision: keep SQLite for this bounded prototype.");
            insertMessage(statement, currentTaskMessageId, currentChatId, 0,
                    "Current task: expose read-only memory access through MCP.");
            insertMessage(statement, deferredMessageId, currentChatId, 0,
                    "Explicitly deferred: semantic extraction.");
            insertMessage(statement, oversizedMessageId, currentChatId, 1, oversizedText());
            insertMessage(statement, otherMessageId, otherChatId, 0,
                    "FORBIDDEN-OTHER-PROJECT-CONTENT must never cross project scope.");
        }
    }

    private static void insertMessage(PreparedStatement statement, long id, long chatId, int sequence,
            String text) throws SQLException {
        statement.setLong(1, id);
        statement.setLong(2, chatId);
        statement.setString(3, "user");
        statement.setString(4, text);
        statement.setInt(5, sequence);
        statement.setString(6, timestamp);
        statement.executeUpdate();
    }

    record Fixture(Path home, Path projectRoot, Path database, Path workingContext, String oversizedText) {
    }

    static final long selectedProjectId = 1;
    static final long otherProjectId = 2;
    static final long historyChatId = 101;
    static final long currentChatId = 102;
    static final long otherChatId = 201;
    static final long proposalMessageId = 1001;
    static final long decisionMessageId = 1002;
    static final long currentTaskMessageId = 2001;
    static final long deferredMessageId = 2002;
    static final long oversizedMessageId = 2003;
    static final long otherMessageId = 3001;
    private static final String timestamp = "2026-10-30T12:00:00Z";
}
