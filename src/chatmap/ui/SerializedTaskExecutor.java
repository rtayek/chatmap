package chatmap.ui;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class SerializedTaskExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;

    SerializedTaskExecutor(String threadName) {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    int queuedTaskCount() {
        return executor.getQueue().size();
    }

    /**
     * Interrupts the running task, refuses new ones, and waits up to {@code timeout}
     * for the worker to finish.
     *
     * @return {@code true} if the worker thread terminated within the timeout, so any
     *     resource it was using (e.g. the shared DB connection) is safe to close;
     *     {@code false} if a task is still running.
     */
    boolean shutdownAndAwait(Duration timeout) throws InterruptedException {
        executor.shutdownNow();
        return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws InterruptedException {
        shutdownAndAwait(Duration.ofSeconds(5));
    }
}
