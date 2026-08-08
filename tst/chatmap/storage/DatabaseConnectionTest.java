package chatmap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class DatabaseConnectionTest {

    @Test
    void openConfiguresForeignKeysAndBusyTimeout() throws Exception {
        try (Connection conn = Database.connectInMemory()) {
            assertEquals(1, pragmaValue(conn, "foreign_keys"));
            assertEquals(5000, pragmaValue(conn, "busy_timeout"));
        }
    }

    @Test
    void openClosesConnectionWhenConfigurationFails() {
        AtomicBoolean closed = new AtomicBoolean();
        Connection connection = connectionThatFailsConfiguration(closed, false);
        Database database = new Database("jdbc:test", ignored -> connection);

        SQLException failure = assertThrows(SQLException.class, database::open);

        assertEquals("configuration failed", failure.getMessage());
        assertTrue(closed.get());
    }

    @Test
    void openPreservesConfigurationFailureWhenCloseAlsoFails() {
        AtomicBoolean closed = new AtomicBoolean();
        Connection connection = connectionThatFailsConfiguration(closed, true);
        Database database = new Database("jdbc:test", ignored -> connection);

        SQLException failure = assertThrows(SQLException.class, database::open);

        assertEquals("configuration failed", failure.getMessage());
        assertTrue(closed.get());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("close failed", failure.getSuppressed()[0].getMessage());
    }

    private static int pragmaValue(Connection conn, String name) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA " + name)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Connection connectionThatFailsConfiguration(AtomicBoolean closed, boolean closeFails) {
        return (Connection) Proxy.newProxyInstance(
                DatabaseConnectionTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createStatement" -> throw new SQLException("configuration failed");
                    case "close" -> {
                        closed.set(true);
                        if (closeFails) {
                            throw new SQLException("close failed");
                        }
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected Connection call: " + method.getName());
                });
    }
}
