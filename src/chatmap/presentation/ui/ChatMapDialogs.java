package chatmap.presentation.ui;

import java.util.Optional;

import chatmap.domain.ConversationInventory;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;

/** Helper methods for creating standard JavaFX modal dialogs in ChatMap. */
public final class ChatMapDialogs {

    private ChatMapDialogs() {
    }

    public static Optional<String> requestName(String title, String prompt) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait();
    }

    public static void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ChatMap");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showConversationInventory(ConversationInventory inventory, int fontSize) {
        TextArea text = new TextArea(ConversationInventoryFormatter.format(inventory));
        text.setEditable(false);
        text.setWrapText(false);
        text.setPrefColumnCount(110);
        text.setPrefRowCount(28);
        text.setStyle("-fx-font-size: " + fontSize + "pt;");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("ChatMap");
        alert.setHeaderText("All discoverable conversations");
        alert.getDialogPane().setContent(text);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
