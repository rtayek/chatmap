package chatmap.infrastructure.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;

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
            chats.insert(Chat.builder()
                    .id(0)
                    .source(Source.chatGptWeb)
                    .title("One")
                    .importedAt("2026-08-05T00:00:00Z")
                    .externalConversationId("abc")
                    .sourceUri("https://chatgpt.com/c/abc")
                    .contentHash("hash")
                    .lastImportedAt("2026-08-05T00:00:00Z")
                    .build());

            assertDoesNotThrow(() -> Database.initialize(conn), "migration must be idempotent");
            assertEquals(1, chats.findAll().size());
        }
    }

    @Test
    void initializeMergesPreExistingDuplicateProjectNamesBeforeAddingTheUniqueIndex() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createOldSchema(conn);

            // Pre-migration schema has no uniqueness constraint on projects.name, so a
            // database from before this migration could already hold case-variant
            // duplicates. Simulate that: two rows for "Foo", each owning a chat.
            long survivorId = insertOldProject(conn, "Foo", "2026-08-10T00:00:00Z");
            long duplicateId = insertOldProject(conn, "FOO", "2026-08-10T00:01:00Z");
            Chat onSurvivor = insertOldChat(conn, survivorId, "Survivor's chat");
            Chat onDuplicate = insertOldChat(conn, duplicateId, "Duplicate's chat");

            Database.initialize(conn);

            ProjectRepository projects = new ProjectRepository(conn);
            List<Project> remaining = projects.findAll();
            assertEquals(1, remaining.size(), "the duplicate-named project must be merged away");
            assertEquals(survivorId, remaining.get(0).id(), "the older row survives");

            ChatRepository chats = new ChatRepository(conn);
            assertEquals(survivorId, chats.findById(onSurvivor.id()).orElseThrow().projectId());
            assertEquals(survivorId, chats.findById(onDuplicate.id()).orElseThrow().projectId(),
                    "the duplicate's chat must be repointed at the survivor, not orphaned or deleted");

            SQLException thrown = assertThrows(SQLException.class,
                    () -> projects.insert(new Project(0, "foo", null,
                            "2026-08-10T00:02:00Z", "2026-08-10T00:02:00Z")));
            assertTrue(ProjectRepository.isDuplicateNameViolation(thrown),
                    "the unique index must be active after migration: " + thrown.getMessage());
        }
    }

    @Test
    void initializeMergesPreExistingDuplicateContentHashChatsBeforeAddingTheUniqueIndex() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            // Simulates a database that already ran an earlier version of this migration
            // (contentHash column present) but predates chatsContentHashIndex, so it could
            // already hold duplicates -- most plausibly from before plainText/markdown
            // dedup existed at all (see ImportService). Only the duplicate is organized,
            // to verify that organization is carried onto the survivor, not lost.
            Database.applySchema(conn);
            long projectId = insertOldProject(conn, "Some Project", "2026-08-10T00:00:00Z");
            long tagId = insertTag(conn, "important");
            long survivorId = insertChatWithContentHash(conn, "plainText", "hash-1", "Survivor",
                    "2026-08-10T00:00:00Z");
            long duplicateId = insertChatWithContentHash(conn, "plainText", "hash-1", "Duplicate",
                    "2026-08-10T00:01:00Z");
            assignProject(conn, duplicateId, projectId);
            assignTag(conn, duplicateId, tagId);
            insertSummary(conn, duplicateId, "a summary", "claude", "2026-08-10T00:02:00Z");

            Database.applyMigrations(conn);

            ChatRepository chats = new ChatRepository(conn);
            List<Chat> remaining = chats.findAll();
            assertEquals(1, remaining.size(), "the duplicate-content chat must be merged away");
            assertEquals(survivorId, remaining.get(0).id(), "the older row survives");
            assertEquals(projectId, remaining.get(0).projectId(), "duplicate's project assignment carried over");

            TagRepository tags = new TagRepository(conn);
            assertEquals(List.of("important"), tags.findByChat(survivorId).stream().map(Tag::name).toList(),
                    "duplicate's tag carried over");

            SummaryRepository summaries = new SummaryRepository(conn);
            assertEquals(1, summaries.findAllForChat(survivorId).size(), "duplicate's summary reassigned, not lost");

            SQLException thrown = assertThrows(SQLException.class, () -> chats.insert(
                    Chat.builder()
                            .id(0)
                            .source(Source.plainText)
                            .title("New dup")
                            .importedAt("2026-08-10T00:03:00Z")
                            .contentHash("hash-1")
                            .build()));
            assertTrue(ChatRepository.isUniqueConstraintViolation(thrown),
                    "the unique index must be active after migration: " + thrown.getMessage());
        }
    }

    private static long insertTag(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tags (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertChatWithContentHash(Connection conn, String source, String contentHash,
            String title, String timestamp) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO chats (source, title, importedAt, archived, contentHash) VALUES (?, ?, ?, 0, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, source);
            ps.setString(2, title);
            ps.setString(3, timestamp);
            ps.setString(4, contentHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void assignProject(Connection conn, long chatId, long projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET projectId = ? WHERE id = ?")) {
            ps.setLong(1, projectId);
            ps.setLong(2, chatId);
            ps.executeUpdate();
        }
    }

    private static void assignTag(Connection conn, long chatId, long tagId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO chatTags (chatId, tagId) VALUES (?, ?)")) {
            ps.setLong(1, chatId);
            ps.setLong(2, tagId);
            ps.executeUpdate();
        }
    }

    private static void insertSummary(Connection conn, long chatId, String summary, String generatedBy,
            String generatedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO chatSummaries (chatId, summary, generatedBy, generatedAt) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, chatId);
            ps.setString(2, summary);
            ps.setString(3, generatedBy);
            ps.setString(4, generatedAt);
            ps.executeUpdate();
        }
    }

    private static long insertOldProject(Connection conn, String name, String timestamp) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO projects (name, description, createdAt, updatedAt) VALUES (?, NULL, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, timestamp);
            ps.setString(3, timestamp);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static Chat insertOldChat(Connection conn, long projectId, String title) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO chats (projectId, source, title, importedAt, archived) VALUES (?, ?, ?, ?, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, projectId);
            ps.setString(2, Source.plainText.dbValue());
            ps.setString(3, title);
            ps.setString(4, "2026-08-10T00:00:00Z");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return Chat.builder()
                               .id(id)
                               .projectId(projectId)
                               .source(Source.plainText)
                               .title(title)
                               .createdAt(null)
                               .updatedAt(null)
                               .importedAt("2026-08-10T00:00:00Z")
                               .archived(false)
                               .build();
            }
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
