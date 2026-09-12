package chatmap.infrastructure.persistence.sqlite;

import org.slf4j.Logger;
import chatmap.application.support.Log;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import chatmap.application.port.persistence.TransactionManager;

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
public final class TransactionRunner implements TransactionManager {

    private static final Logger LOG = Log.of(TransactionRunner.class);

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

    @Override
    public <T> T inTransaction(TransactionManager.Work<T> work) throws SQLException {
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

    private <T> T inCallerTransaction(TransactionManager.Work<T> work) throws SQLException {
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
            LOG.debug("Silenced exception: {}", ignored.getMessage(), ignored);
        }
            throw e;
        }
    }
}
