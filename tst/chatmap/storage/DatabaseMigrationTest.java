package chatmap.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Source;

class DatabaseMigrationTest {

    @Test
    void initializeAddsProvenanceColumnsToOldSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createOldSchema(conn);

            Database.initialize(conn);

            Set<String> chatColumns = columns(conn, "chats");
            assertTrue(chatColumns.contains("externalConversationId"));
            assertTrue(chatColumns.contains("sourceUri"));
            assertTrue(chatColumns.contains("contentHash"));
            assertTrue(chatColumns.contains("sourceUpdatedAt"));
            assertTrue(chatColumns.contains("lastImportedAt"));
            assertTrue(columns(conn, "chatSummaries").contains("contentHash"));

            ChatRepository chats = new ChatRepository(conn);
            chats.insert(new Chat(0, null, Source.chatGptWeb, "One", null, null,
                    "2026-08-05T00:00:00Z", false, "abc", "https://chatgpt.com/c/abc",
                    "hash", null, "2026-08-05T00:00:00Z"));

            assertDoesNotThrow(() -> Database.initialize(conn), "migration must be idempotent");
            assertEquals(1, chats.findAll().size());
        }
    }

    private static void createOldSchema(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE projects (id INTEGER PRIMARY KEY, name TEXT NOT NULL, "
                    + "description TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
            st.execute("CREATE TABLE chats (id INTEGER PRIMARY KEY, "
                    + "projectId INTEGER REFERENCES projects(id) ON DELETE SET NULL, "
                    + "source TEXT NOT NULL, title TEXT NOT NULL, createdAt TEXT, updatedAt TEXT, "
                    + "importedAt TEXT NOT NULL, archived INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE messages (id INTEGER PRIMARY KEY, "
                    + "chatId INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE, "
                    + "role TEXT NOT NULL, text TEXT NOT NULL, sequence INTEGER NOT NULL, "
                    + "timestamp TEXT, rawJson TEXT)");
            st.execute("CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
            st.execute("CREATE TABLE chatTags (chatId INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE, "
                    + "tagId INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE, PRIMARY KEY (chatId, tagId))");
            st.execute("CREATE TABLE chatSummaries (id INTEGER PRIMARY KEY, "
                    + "chatId INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE, "
                    + "summary TEXT NOT NULL, generatedBy TEXT NOT NULL, generatedAt TEXT NOT NULL)");
            st.execute("CREATE VIRTUAL TABLE messageFts USING fts5(text, content='messages', content_rowid='id')");
        }
    }

    private static Set<String> columns(Connection conn, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }
}
