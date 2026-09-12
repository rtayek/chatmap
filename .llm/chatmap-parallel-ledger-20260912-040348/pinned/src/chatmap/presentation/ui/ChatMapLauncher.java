package chatmap.presentation.ui;

import chatmap.app.bootstrap.LoggingBootstrap;
import javafx.application.Application;

/** Plain Java launcher for environments that do not launch JavaFX Application classes directly. */
public final class ChatMapLauncher {

    private ChatMapLauncher() {
    }

    public static void main(String[] args) {
        LoggingBootstrap.bootstrap(args, ChatMapLauncher::validateArguments);
        Application.launch(ChatMapApp.class, args);
    }

    private static void validateArguments(chatmap.app.bootstrap.ChatMapPaths.ParsedArguments parsedArguments) {
        if (!parsedArguments.remainingArgs().isEmpty()) {
            throw new IllegalArgumentException("Usage: ChatMap [--home <directory>]");
        }
    }
}
