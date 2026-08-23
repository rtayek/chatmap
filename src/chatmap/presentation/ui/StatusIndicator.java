package chatmap.presentation.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** High-visibility state treatment for background action status. */
final class StatusIndicator {
    static final Color BUSY = Color.web("#B45309");
    static final Color READY = Color.web("#15803D");
    static final Color ERROR = Color.web("#B91C1C");
    static final Color NEUTRAL = Color.web("#374151");

    private static final Duration READY_TRANSITION = Duration.millis(650);

    private final Label label;
    private final ObjectProperty<Color> currentColor = new SimpleObjectProperty<>(NEUTRAL);
    private Timeline transition;

    StatusIndicator(Label label) {
        this.label = label;
        this.label.setPadding(new Insets(3, 8, 3, 8));
        this.label.setMaxWidth(Double.MAX_VALUE);
        this.currentColor.addListener((observable, oldColor, newColor) -> applyColor(newColor));
        applyColor(NEUTRAL);
    }

    void busy(String text) {
        stopTransition();
        if (text != null) {
            label.setText(text);
        }
        currentColor.set(BUSY);
    }

    void ready() {
        stopTransition();
        transition = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(currentColor, currentColor.get())),
                new KeyFrame(READY_TRANSITION, new KeyValue(currentColor, READY)));
        transition.play();
    }

    void error() {
        stopTransition();
        currentColor.set(ERROR);
    }

    boolean transitionRunning() {
        return transition != null
                && transition.getStatus() == javafx.animation.Animation.Status.RUNNING;
    }

    Color currentColor() {
        return currentColor.get();
    }

    private void stopTransition() {
        if (transition != null) {
            transition.stop();
        }
    }

    private void applyColor(Color color) {
        label.setBackground(new Background(new BackgroundFill(color, new CornerRadii(3), Insets.EMPTY)));
        label.setTextFill(Color.WHITE);
    }
}
