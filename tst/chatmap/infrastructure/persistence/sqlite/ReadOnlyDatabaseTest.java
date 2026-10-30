package chatmap.infrastructure.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadOnlyDatabaseTest {

    @Test
    void opensExistingSchemaAndRejectsDmlAndDdlWithoutChangingJournalMode(@TempDir Path directory) throws Exception {
        Path database = directory.resolve("chatmap.db");
        try (Connection writer = new Database("jdbc:sqlite:" + database).openAndInitialize();
                Statement statement = writer.createStatement()) {
            statement.executeUpdate("INSERT INTO projects (id, name, createdAt, updatedAt) "
                    + "VALUES (1, 'fixture', 'now', 'now')");
        }
        String journalBefore = pragma(database, "journal_mode");
        byte[] bytesBefore = Files.readAllBytes(database);
        String schemaBefore = query(database,
                "SELECT group_concat(type || ':' || name || ':' || ifnull(sql, ''), char(10)) "
                        + "FROM (SELECT type, name, sql FROM sqlite_schema ORDER BY type, name)");

        try (Connection connection = ReadOnlyDatabase.open(database);
                Statement statement = connection.createStatement()) {
            assertEquals(1, scalar(statement, "SELECT count(*) FROM projects"));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate("INSERT INTO projects (name, createdAt, updatedAt) "
                            + "VALUES ('no', 'now', 'now')"));
            assertThrows(SQLException.class, () -> statement.execute("CREATE TABLE forbidden (id INTEGER)"));
        }

        assertEquals(journalBefore, pragma(database, "journal_mode"));
        assertEquals(schemaBefore, query(database,
                "SELECT group_concat(type || ':' || name || ':' || ifnull(sql, ''), char(10)) "
                        + "FROM (SELECT type, name, sql FROM sqlite_schema ORDER BY type, name)"));
        assertArrayEquals(bytesBefore, Files.readAllBytes(database));
    }

    @Test
    void missingDatabaseFailsWithoutCreatingIt(@TempDir Path directory) {
        Path missing = directory.resolve("missing.db");

        assertThrows(SQLException.class, () -> ReadOnlyDatabase.open(missing));

        assertFalse(Files.exists(missing));
    }

    @Test
    void incompatibleSchemaFailsClearly(@TempDir Path directory) throws Exception {
        Path database = directory.resolve("wrong.db");
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE projects (id INTEGER PRIMARY KEY)");
        }

        SQLException failure = assertThrows(SQLException.class, () -> ReadOnlyDatabase.open(database));

        assertTrue(failure.getMessage().contains("Incompatible ChatMap schema"));
    }

    private static String pragma(Path database, String name) throws SQLException {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                java.sql.ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            result.next();
            return result.getString(1);
        }
    }

    private static int scalar(Statement statement, String query) throws SQLException {
        try (java.sql.ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String query(Path database, String query) throws SQLException {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                java.sql.ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getString(1);
        }
    }
}
