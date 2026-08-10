package chatmap.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

/**
 * Runs service-level units of work without taking ownership of caller transactions.
 *
 * Concurrency policy: all storage access serializes on the shared Connection's
 * monitor. Repositories acquire it per statement; this class holds it for the
 * whole transaction so multi-statement writes are atomic with respect to
 * readers on other threads. The monitor is reentrant, so repository calls
 * inside the transaction work are fine. Callers above the storage layer must
 * not take this lock themselves.
 */
public final class TransactionRunner {

    @FunctionalInterface
    public interface Work<T> {
        T run() throws SQLException;
    }

    private final Connection conn;
    private final Object lock;

    public TransactionRunner(Connection conn) {
        this.conn = java.util.Objects.requireNonNull(conn, "conn");
        this.lock = conn;
    }

    public TransactionRunner(Connection conn, Object lock) {
        this.conn = java.util.Objects.requireNonNull(conn, "conn");
        this.lock = lock == null ? conn : lock;
    }

    public <T> T inTransaction(Work<T> work) throws SQLException {
        synchronized (lock) {
            if (!conn.getAutoCommit()) {
                return inCallerTransaction(work);
            }

            conn.setAutoCommit(false);
            try {
                T result = work.run();
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private <T> T inCallerTransaction(Work<T> work) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            T result = work.run();
            conn.releaseSavepoint(savepoint);
            return result;
        } catch (SQLException | RuntimeException e) {
            conn.rollback(savepoint);
            try {
                conn.releaseSavepoint(savepoint);
            } catch (SQLException ignored) {
                // Some drivers invalidate a savepoint when rolling back to it.
            }
            throw e;
        }
    }
}
