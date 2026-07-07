package chatmap.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens SQLite connections and applies schema.sql.
 *
 * Responsibilities:
 * - open a connection to a given JDBC URL (file-backed or in-memory)
 * - enable foreign key enforcement on every connection (SQLite default is OFF)
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

    private final String jdbcUrl;

    public Database(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    /** Opens a connection with foreign keys enabled. Caller closes it. */
    public Connection open() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /** Opens a connection and applies the schema to it. Caller closes it. */
    public Connection openAndInitialize() throws SQLException, IOException {
        Connection conn = open();
        try {
            applySchema(conn);
        } catch (SQLException | IOException e) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // preserve the original failure
            }
            throw e;
        }
        return conn;
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

    private static String readSchemaResource() throws IOException {
        try (InputStream in = Database.class.getResourceAsStream(schemaResource)) {
            if (in == null) {
                throw new IOException("schema resource not found on classpath: " + schemaResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
