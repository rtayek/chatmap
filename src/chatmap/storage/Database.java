package chatmap.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
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

    private static final String schemaResource = "/chatmap/storage/schema.sql";
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

    /** Opens a connection and applies the schema to it. Caller closes it. */
    public Connection openAndInitialize() throws SQLException, IOException {
        Connection conn = open();
        try {
            applySchema(conn);
            applyMigrations(conn);
        } catch (SQLException | IOException | RuntimeException | Error failure) {
            closeAfterFailure(conn, failure);
            throw failure;
        }
        return conn;
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
        // Split on ';' at end of line. Trigger bodies contain ';' followed by
        // more text on the same statement, but their inner statements also end
        // at line ends -- so we split on "END;" boundaries carefully instead:
        // simplest robust approach for this schema: split on ";\n" then stitch
        // trigger bodies back together by tracking BEGIN...END.
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;
        try (Statement st = conn.createStatement()) {
            for (String line : sql.split("\r?\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                String upper = trimmed.toUpperCase();
                if (upper.startsWith("CREATE TRIGGER")) {
                    inTrigger = true;
                }
                boolean statementEnds;
                if (inTrigger) {
                    statementEnds = upper.equals("END;");
                } else {
                    statementEnds = trimmed.endsWith(";");
                }
                if (statementEnds) {
                    st.execute(current.toString());
                    current.setLength(0);
                    inTrigger = false;
                }
            }
            String leftover = current.toString().trim();
            if (!leftover.isEmpty()) {
                st.execute(leftover);
            }
        }
    }

    /** Applies additive migrations that CREATE TABLE IF NOT EXISTS cannot perform on old databases. */
    public static void applyMigrations(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "chats", "externalConversationId", "TEXT");
        addColumnIfMissing(conn, "chats", "sourceUri", "TEXT");
        addColumnIfMissing(conn, "chats", "contentHash", "TEXT");
        addColumnIfMissing(conn, "chats", "sourceUpdatedAt", "TEXT");
        addColumnIfMissing(conn, "chats", "lastImportedAt", "TEXT");
        addColumnIfMissing(conn, "chatSummaries", "contentHash", "TEXT");

        try (Statement st = conn.createStatement()) {
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS chatsExternalIdentityIndex "
                    + "ON chats(source, externalConversationId) WHERE externalConversationId IS NOT NULL");
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