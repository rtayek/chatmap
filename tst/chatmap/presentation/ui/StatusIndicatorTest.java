package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.control.Label;

final class StatusIndicatorTest {

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void busyAppliesBusyColorAndStatusText() throws Exception {
        runOnFxThread(() -> {
            Label label = new Label("Ready");
            StatusIndicator indicator = new StatusIndicator(label);

            indicator.busy("Working...");

            assertEquals("Working...", label.getText());
            assertEquals(StatusIndicator.BUSY, indicator.currentColor());
        });
    }

    @Test
    void readyStartsAnimatedTransition() throws Exception {
        runOnFxThread(() -> {
            StatusIndicator indicator = new StatusIndicator(new Label("Working"));
            indicator.busy("Working...");

            indicator.ready();

            assertTrue(indicator.transitionRunning());
        });
    }

    @Test
    void errorDoesNotAnimateToReady() throws Exception {
        runOnFxThread(() -> {
            StatusIndicator indicator = new StatusIndicator(new Label("Working"));
            indicator.busy("Working...");

            indicator.error();

            assertEquals(StatusIndicator.ERROR, indicator.currentColor());
            assertFalse(indicator.transitionRunning());
        });
    }

    private static void runOnFxThread(CheckedRunnable action) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Exception exception) {
                failure.set(exception);
            } finally {
                finished.countDown();
            }
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
