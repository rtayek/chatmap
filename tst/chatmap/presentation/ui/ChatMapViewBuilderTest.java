package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.layout.Pane;

final class ChatMapViewBuilderTest {

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
    void promptPaneShowsRouteAndClassificationLabelsButHidesHistoryByDefault() {
        ChatMapViewBuilder.PromptPaneWidgets widgets = ChatMapViewBuilder.createPromptPane(() -> {
        }, exception -> {
        });

        assertTrue(containsNode(widgets.promptPane(), widgets.classificationLabel()));
        assertTrue(containsNode(widgets.promptPane(), widgets.routeLabel()));
        assertTrue(containsNode(widgets.promptPane(), widgets.historyArea()));
        assertFalse(widgets.historyArea().isVisible());
        assertFalse(widgets.historyArea().isManaged());
    }

    @Test
    void historyPanelToggleControlsVisibleAndManagedState() {
        ChatMapViewBuilder.PromptPaneWidgets widgets = ChatMapViewBuilder.createPromptPane(() -> {
        }, exception -> {
        });
        CheckMenuItem toggle = new CheckMenuItem("History panel");

        ChatMapApp.bindVisibility(widgets.historyArea(), toggle);

        assertFalse(widgets.historyArea().isVisible());
        assertFalse(widgets.historyArea().isManaged());

        toggle.setSelected(true);
        assertTrue(widgets.historyArea().isVisible());
        assertTrue(widgets.historyArea().isManaged());

        toggle.setSelected(false);
        assertFalse(widgets.historyArea().isVisible());
        assertFalse(widgets.historyArea().isManaged());
    }

    private static boolean containsNode(Node root, Node target) {
        if (root == target) {
            return true;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsNode(child, target)) {
                    return true;
                }
            }
        } else if (root instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                if (containsNode(child, target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
