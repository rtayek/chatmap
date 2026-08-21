package chatmap.presentation.ui;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import chatmap.app.ChatMapRuntime;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Runs blocking UI actions on one of ChatMap's two background lanes: the fast
 * DB-only lane (default) or the slow LLM-backend/live-fetch lane (the run*OnBackendLane
 * variants), so a multi-minute summarize or live web fetch never makes a search or
 * chat-list load wait behind it.
 */
final class BackgroundActionRunner {
    private final ChatMapRuntime runtime;
    private final Label status;
    private final Consumer<Exception> errorReporter;

    private static final System.Logger LOGGER = System.getLogger(BackgroundActionRunner.class.getName());

    BackgroundActionRunner(ChatMapRuntime runtime, Label status, Consumer<Exception> errorReporter) {
        this.runtime = runtime;
        this.status = status;
        this.errorReporter = errorReporter;
    }

    void runSnapshot(String pendingStatus, Button triggerButton, SnapshotCall call,
            Consumer<ChatListState.Snapshot> onSuccess, Consumer<Exception> onFailure) {
        setPending(pendingStatus, triggerButton);
        runtime.submit(snapshotRunnable(call, triggerButton, onSuccess, onFailure));
    }

    void runSnapshotOnBackendLane(String pendingStatus, Button triggerButton, SnapshotCall call,
            Consumer<ChatListState.Snapshot> onSuccess, Consumer<Exception> onFailure) {
        setPending(pendingStatus, triggerButton);
        runtime.submitBackendWork(snapshotRunnable(call, triggerButton, onSuccess, onFailure));
    }

    private Runnable snapshotRunnable(SnapshotCall call, Button triggerButton,
            Consumer<ChatListState.Snapshot> onSuccess, Consumer<Exception> onFailure) {
        return () -> {
            try {
                ChatListState.Snapshot snapshot = call.run();
                Platform.runLater(() -> {
                    finish(triggerButton);
                    onSuccess.accept(snapshot);
                });
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Background snapshot action failed", exception);
                Platform.runLater(() -> {
                    finish(triggerButton);
                    onFailure.accept(exception);
                });
            }
        };
    }

    <T> void runValue(String pendingStatus, Button triggerButton,
            Callable<T> call, Consumer<T> onSuccess) {
        setPending(pendingStatus, triggerButton);
        runtime.submit(valueRunnable(call, triggerButton, onSuccess));
    }

    <T> void runValueOnBackendLane(String pendingStatus, Button triggerButton,
            Callable<T> call, Consumer<T> onSuccess) {
        setPending(pendingStatus, triggerButton);
        runtime.submitBackendWork(valueRunnable(call, triggerButton, onSuccess));
    }

    private <T> Runnable valueRunnable(Callable<T> call, Button triggerButton, Consumer<T> onSuccess) {
        return () -> {
            try {
                T result = call.call();
                Platform.runLater(() -> {
                    finish(triggerButton);
                    onSuccess.accept(result);
                });
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Background value action failed", exception);
                Platform.runLater(() -> {
                    finish(triggerButton);
                    errorReporter.accept(exception);
                });
            }
        };
    }

    private void setPending(String pendingStatus, Button triggerButton) {
        if (pendingStatus != null) {
            status.setText(pendingStatus);
        }
        if (triggerButton != null) {
            triggerButton.setDisable(true);
        }
    }

    private static void finish(Button triggerButton) {
        if (triggerButton != null) {
            triggerButton.setDisable(false);
        }
    }

    @FunctionalInterface
    interface SnapshotCall {
        ChatListState.Snapshot run() throws Exception;
    }
}
