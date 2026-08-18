package chatmap.infrastructure.persistence.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Opens SQLite connections and applies schema.sql.
 *
 * Responsibilities:
 * - open a connection to a given JDBC URL (file-backed or in-memory)
 * - enable foreign key enforcement on every connection (SQLite default is OFF)
 * - configure a bounded wait for transient SQLite locks
 * - execute schema.sql from the classpath (idempotent: all CREATE ... IF NOT EXISTS)
 *
 * Tests use "jdbc:sqlite::memory:" for a fresh throwaway database.
 * The application uses a file URL such as "jdbc:sqlite:C:/.../chatmap.db".
 *
 * NOTE on in-memory databases: each new connection to :memory: is a separate
 * database. Callers that use :memory: must keep a single connection open and
 * pass it around; do not open a second connection expecting the same data.
 */
public final class Database {

    private static final String schemaResource = "/chatmap/infrastructure/persistence/sqlite/schema.sql";
    private static final int busyTimeoutMilliseconds = 5000;

    private final String jdbcUrl;
    private final ConnectionOpener connectionOpener;

    public Database(String jdbcUrl) {
        this(jdbcUrl, DriverManager::getConnection);
    }

    Database(String jdbcUrl, ConnectionOpener connectionOpener) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.connectionOpener = Objects.requireNonNull(connectionOpener, "connectionOpener");
    }

    /** Opens a configured in-memory SQLite connection. Caller closes it. */
    public static Connection connectInMemory() throws SQLException {
        return new Database("jdbc:sqlite::memory:").open();
    }

    /** Applies schema.sql to the given connection. */
    public static void initialize(Connection conn) throws SQLException, IOException {
        applySchema(conn);
        applyMigrations(conn);
    }

    /** Opens and configures a connection. The caller owns and closes a successful result. */
    public Connection open() throws SQLException {
        Connection conn = connectionOpener.open(jdbcUrl);
        try {
            configureConnection(conn);
            return conn;
        } catch (SQLException | RuntimeException | Error failure) {
            closeAfterFailure(conn, failure);
            throw failure;
        }
    }

    /** Opens a connection, checks database integrity, and applies the schema to it. Caller closes it. */
    public Connection openAndInitialize() throws SQLException, IOException {
        Connection conn = open();
        try {
            quickCheck(conn);
            applySchema(conn);
            applyMigrations(conn);
        } catch (SQLException | IOException | RuntimeException | Error failure) {
            closeAfterFailure(conn, failure);
            throw failure;
        }
        return conn;
    }

    /** Runs PRAGMA quick_check to verify SQLite B-tree integrity. */
    public static void quickCheck(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
            if (rs.next()) {
                String result = rs.getString(1);
                if (!"ok".equalsIgnoreCase(result)) {
                    throw new SQLException("SQLite database integrity check failed: " + result);
                }
            }
        }
    }

    private void configureConnection(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = " + busyTimeoutMilliseconds);
            if (!jdbcUrl.contains(":memory:")) {
                st.execute("PRAGMA journal_mode = WAL");
            }
        }
    }

    private static void closeAfterFailure(Connection conn, Throwable failure) {
        try {
            conn.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    /** Executes schema.sql against an existing connection. Idempotent. */
    public static void applySchema(Connection conn) throws SQLException, IOException {
        String sql = readSchemaResource();
        try (Statement st = conn.createStatement()) {
            for (String statement : parseSchemaStatements(sql)) {
                st.execute(statement);
            }
        }
    }

    /**
     * Splits schema.sql into executable SQL statements, ignoring comments and
     * keeping CREATE TRIGGER blocks intact.
     */
    static List<String> parseSchemaStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;
        for (String line : sql.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
            if (upper.startsWith("CREATE TRIGGER")) {
                inTrigger = true;
            }
            boolean statementEnds = inTrigger ? upper.endsWith("END;") : trimmed.endsWith(";");
            if (statementEnds) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
                inTrigger = false;
            }
        }
        String leftover = current.toString().trim();
        if (!leftover.isEmpty()) {
            statements.add(leftover);
        }
        return statements;
    }

    /** Applies additive migrations that CREATE TABLE IF NOT EXISTS cannot perform on old databases. */
    public static void applyMigrations(Connection conn) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        if (previousAutoCommit) {
            conn.setAutoCommit(false);
        }
        Throwable thrown = null;
        try {
            addColumnIfMissing(conn, "chats", "externalConversationId", "TEXT");
            addColumnIfMissing(conn, "chats", "sourceUri", "TEXT");
            addColumnIfMissing(conn, "chats", "contentHash", "TEXT");
            addColumnIfMissing(conn, "chats", "sourceUpdatedAt", "TEXT");
            addColumnIfMissing(conn, "chats", "lastImportedAt", "TEXT");
            addColumnIfMissing(conn, "chats", "originatedBy", "TEXT NOT NULL DEFAULT 'IMPORTED'");
            addColumnIfMissing(conn, "chats", "providerId", "TEXT");
            addColumnIfMissing(conn, "chats", "modelTargetId", "TEXT");
            addColumnIfMissing(conn, "chats", "providerModelName", "TEXT");
            addColumnIfMissing(conn, "chats", "providerSessionId", "TEXT");
            addColumnIfMissing(conn, "chatSummaries", "contentHash", "TEXT");

            try (Statement st = conn.createStatement()) {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS chatsExternalIdentityIndex "
                        + "ON chats(source, externalConversationId) WHERE externalConversationId IS NOT NULL");
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS chatsPromptSessionIndex "
                        + "ON chats(providerId, modelTargetId, providerSessionId) "
                        + "WHERE providerId IS NOT NULL AND modelTargetId IS NOT NULL "
                        + "AND providerSessionId IS NOT NULL");
            }

            // Same reasoning as chatsExternalIdentityIndex, for the other dedup path:
            // ImportService.findBySourceAndContentHash was, until now, an application-level
            // check with no database backstop, so a database from before this migration could
            // already hold duplicate (source, contentHash) rows -- most plausibly from before
            // plainText/markdown dedup existed at all. Merge those first (see
            // mergeDuplicateContentHashChats) so the index can be created.
            //
            // Scoped to externalConversationId IS NULL, matching findBySourceAndContentHash's
            // own scoping: content-hash dedup only applies within the identity-less pool
            // (plainText/markdown/no-identity provider fetches). An externally-identified
            // chat coincidentally sharing a content hash with an unrelated identity-less
            // import is not a duplicate and must not collide with it here.
            mergeDuplicateContentHashChats(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS chatsContentHashIndex "
                        + "ON chats(source, contentHash) "
                        + "WHERE contentHash IS NOT NULL AND externalConversationId IS NULL");
            }

            // A unique index needs zero pre-existing duplicates to create successfully, so any
            // project names that already collide (case-insensitively) on an existing database
            // must be merged first. Idempotent: once merged, later runs find nothing to merge.
            mergeDuplicateProjectNames(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS projectsNameIndex "
                        + "ON projects(name COLLATE NOCASE)");
            }

            if (previousAutoCommit) {
                conn.commit();
            }
        } catch (SQLException | RuntimeException | Error e) {
            thrown = e;
            if (previousAutoCommit) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
            }
            throw e;
        } finally {
            if (previousAutoCommit) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException autoCommitFailure) {
                    if (thrown != null) {
                        thrown.addSuppressed(autoCommitFailure);
                    } else {
                        throw autoCommitFailure;
                    }
                }
            }
        }
    }

    /**
     * Merges projects that share a name, case-insensitively, into the oldest row
     * in each group: every chat pointing at a duplicate is repointed at the
     * survivor, then the duplicate row is deleted. Grouping normalizes with
     * {@code toUpperCase(Locale.ROOT)}, which folds a broader range of
     * characters than the ASCII-only {@code COLLATE NOCASE} unique index this
     * prepares the table for — so this also catches non-ASCII case collisions
     * (e.g. "München" vs "MÜNCHEN") that the index alone cannot prevent.
     */
    private static void mergeDuplicateProjectNames(Connection conn) throws SQLException {
        Map<String, List<Long>> idsByNormalizedName = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, name FROM projects ORDER BY id")) {
            while (rs.next()) {
                String key = rs.getString("name").toUpperCase(Locale.ROOT);
                idsByNormalizedName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rs.getLong("id"));
            }
        }

        for (List<Long> ids : idsByNormalizedName.values()) {
            if (ids.size() < 2) {
                continue;
            }
            long survivorId = ids.get(0);
            for (long duplicateId : ids.subList(1, ids.size())) {
                try (PreparedStatement reassign =
                        conn.prepareStatement("UPDATE chats SET projectId = ? WHERE projectId = ?")) {
                    reassign.setLong(1, survivorId);
                    reassign.setLong(2, duplicateId);
                    reassign.executeUpdate();
                }
                try (PreparedStatement delete = conn.prepareStatement("DELETE FROM projects WHERE id = ?")) {
                    delete.setLong(1, duplicateId);
                    delete.executeUpdate();
                }
            }
        }
    }

    /**
     * Merges chats that share a (source, contentHash) pair into the oldest row in
     * each group (same tie-break as {@code ChatRepository.findBySourceAndContentHash}'s
     * {@code ORDER BY importedAt, id}), so a pre-existing database can still adopt the
     * unique index this prepares the table for. A duplicate's project assignment (if
     * the survivor has none) and tags are carried onto the survivor, and any generated
     * summaries are reassigned to it, before the duplicate row is deleted -- its
     * messages and chatTags rows are identical content by definition (same hash) and
     * are removed via ON DELETE CASCADE. Rows with a NULL contentHash, or a non-NULL
     * externalConversationId, are never considered duplicates of each other (matching
     * the index's own WHERE clause).
     */
    private static void mergeDuplicateContentHashChats(Connection conn) throws SQLException {
        Map<String, List<Long>> idsByKey = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, source, contentHash FROM chats "
                        + "WHERE contentHash IS NOT NULL AND externalConversationId IS NULL "
                        + "ORDER BY importedAt, id")) {
            while (rs.next()) {
                String key = rs.getString("source") + "" + rs.getString("contentHash");
                idsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rs.getLong("id"));
            }
        }

        for (List<Long> ids : idsByKey.values()) {
            if (ids.size() < 2) {
                continue;
            }
            long survivorId = ids.get(0);
            for (long duplicateId : ids.subList(1, ids.size())) {
                adoptProjectIfMissing(conn, survivorId, duplicateId);
                mergeChatTags(conn, survivorId, duplicateId);
                reassignChatSummaries(conn, survivorId, duplicateId);
                try (PreparedStatement delete = conn.prepareStatement("DELETE FROM chats WHERE id = ?")) {
                    delete.setLong(1, duplicateId);
                    delete.executeUpdate();
                }
            }
        }
    }

    private static void adoptProjectIfMissing(Connection conn, long survivorId, long duplicateId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE chats SET projectId = (SELECT projectId FROM chats WHERE id = ?) "
                        + "WHERE id = ? AND projectId IS NULL")) {
            ps.setLong(1, duplicateId);
            ps.setLong(2, survivorId);
            ps.executeUpdate();
        }
    }

    private static void mergeChatTags(Connection conn, long survivorId, long duplicateId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO chatTags (chatId, tagId) SELECT ?, tagId FROM chatTags WHERE chatId = ?")) {
            ps.setLong(1, survivorId);
            ps.setLong(2, duplicateId);
            ps.executeUpdate();
        }
    }

    private static void reassignChatSummaries(Connection conn, long survivorId, long duplicateId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chatSummaries SET chatId = ? WHERE chatId = ?")) {
            ps.setLong(1, survivorId);
            ps.setLong(2, duplicateId);
            ps.executeUpdate();
        }
    }

    private static void addColumnIfMissing(Connection conn, String table, String column, String type)
            throws SQLException {
        if (columns(conn, table).contains(column)) {
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private static Set<String> columns(Connection conn, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private static String readSchemaResource() throws IOException {
        try (InputStream in = Database.class.getResourceAsStream(schemaResource)) {
            if (in == null) {
                throw new IOException("schema resource not found on classpath: " + schemaResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    interface ConnectionOpener {
        Connection open(String jdbcUrl) throws SQLException;
    }
}
