package chatmap.infrastructure.persistence.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.sqlite.SQLiteConfig;

/** Opens an existing ChatMap database without initialization or persistent pragmas. */
public final class ReadOnlyDatabase {

    private static final Map<String, Set<String>> requiredColumns = requiredColumns();

    private ReadOnlyDatabase() {
    }

    public static Connection open(Path databasePath) throws SQLException {
        Path path = databasePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new SQLException("Read-only ChatMap database does not exist: " + path);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setExplicitReadOnly(true);
        config.setBusyTimeout(5000);
        Connection connection = config.createConnection("jdbc:sqlite:" + path);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA query_only = ON");
            }
            validateSchema(connection);
            return connection;
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public static void validateSchema(Connection connection) throws SQLException {
        for (Map.Entry<String, Set<String>> entry : requiredColumns.entrySet()) {
            Set<String> actual = tableColumns(connection, entry.getKey());
            if (!actual.containsAll(entry.getValue())) {
                throw new SQLException("Incompatible ChatMap schema: " + entry.getKey()
                        + " requires columns " + entry.getValue() + " but found " + actual);
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'messageFts'")) {
            if (!result.next()) {
                throw new SQLException("Incompatible ChatMap schema: required FTS table messageFts is missing.");
            }
        }
    }

    private static Set<String> tableColumns(Connection connection, String table) throws SQLException {
        java.util.LinkedHashSet<String> columns = new java.util.LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        return Set.copyOf(columns);
    }

    private static Map<String, Set<String>> requiredColumns() {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        columns.put("projects", Set.of("id", "name"));
        columns.put("chats", Set.of("id", "projectId", "source", "title", "createdAt", "updatedAt",
                "importedAt", "archived", "externalConversationId", "sourceUri", "contentHash"));
        columns.put("messages", Set.of("id", "chatId", "role", "text", "sequence", "timestamp", "rawJson"));
        columns.put("tags", Set.of("id", "name"));
        columns.put("chatTags", Set.of("chatId", "tagId"));
        return Map.copyOf(columns);
    }
}
