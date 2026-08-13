package chatmap.application.port.persistence;

import java.sql.SQLException;

/** Runs an application unit of work atomically. */
public interface TransactionManager {

    @FunctionalInterface
    interface Work<T> {
        T run() throws SQLException;
    }

    <T> T inTransaction(Work<T> work) throws SQLException;
}
