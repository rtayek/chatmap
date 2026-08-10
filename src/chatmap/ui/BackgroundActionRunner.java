package chatmap.ui;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import chatmap.app.ChatMapRuntime;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/** Runs blocking UI actions on ChatMap's serialized background executor. */
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
        runtime.submit(() -> {
            try {
                ChatListState.Snapshot snapshot = call.run();
                Platform.runLater(() -> onSuccess.accept(snapshot));
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Background snapshot action failed", exception);
                Platform.runLater(() -> onFailure.accept(exception));
            }
        });
    }

    <T> void runValue(String pendingStatus, Button triggerButton,
            Callable<T> call, Consumer<T> onSuccess) {
        setPending(pendingStatus, triggerButton);
        runtime.submit(() -> {
            try {
                T result = call.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Background value action failed", exception);
                Platform.runLater(() -> errorReporter.accept(exception));
            }
        });
    }

    private void setPending(String pendingStatus, Button triggerButton) {
        if (pendingStatus != null) {
            status.setText(pendingStatus);
        }
        if (triggerButton != null) {
            triggerButton.setDisable(true);
        }
    }

    @FunctionalInterface
    interface SnapshotCall {
        ChatListState.Snapshot run() throws Exception;
    }
}
