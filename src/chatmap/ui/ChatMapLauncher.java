package chatmap.ui;

import javafx.application.Application;

/** Plain Java launcher for environments that do not launch JavaFX Application classes directly. */
public final class ChatMapLauncher {

    private ChatMapLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(ChatMapApp.class, args);
    }
}
