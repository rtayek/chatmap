package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SerializedTaskExecutorTest {

    @Test
    void submittedTasksDoNotRunConcurrently() throws Exception {
        try (SerializedTaskExecutor executor = new SerializedTaskExecutor("test-serialized")) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicInteger running = new AtomicInteger();
            AtomicInteger maxRunning = new AtomicInteger();

            Future<?> first = executor.submit(() -> {
                int current = running.incrementAndGet();
                maxRunning.accumulateAndGet(current, Math::max);
                firstStarted.countDown();
                await(releaseFirst);
                running.decrementAndGet();
            });

            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                int current = running.incrementAndGet();
                maxRunning.accumulateAndGet(current, Math::max);
                running.decrementAndGet();
            });

            assertEquals(1, executor.queuedTaskCount());
            assertFalse(second.isDone());

            releaseFirst.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);

            assertEquals(1, maxRunning.get());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
