package chatmap.presentation.ui;

import java.sql.Connection;

import chatmap.app.DefaultServiceIntegrations;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.app.ServiceGraph;
import chatmap.app.ServiceGraph.Integrations;

/** Builds a {@link ChatMapController} from the shared {@link ServiceGraph} wiring. */
public final class ChatMapControllerFactory {

    private ChatMapControllerFactory() {
    }

    public static ChatMapController create(Connection connection, ResolvedPaths paths) {
        return create(connection, defaultIntegrations(), paths);
    }

    static ChatMapController create(Connection connection, Integrations integrations, ResolvedPaths paths) {
        return new ChatMapController(ServiceGraph.create(connection, integrations, paths));
    }

    static Integrations defaultIntegrations() {
        return DefaultServiceIntegrations.create();
    }
}
