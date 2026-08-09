package chatmap.ui;

import java.sql.Connection;

import chatmap.app.DefaultServiceIntegrations;
import chatmap.service.ServiceGraph;
import chatmap.service.ServiceGraph.Integrations;

/** Builds a {@link ChatMapController} from the shared {@link ServiceGraph} wiring. */
public final class ChatMapControllerFactory {

    private ChatMapControllerFactory() {
    }

    public static ChatMapController create(Connection connection) {
        return create(connection, defaultIntegrations());
    }

    static ChatMapController create(Connection connection, Integrations integrations) {
        return new ChatMapController(ServiceGraph.create(connection, integrations));
    }

    static Integrations defaultIntegrations() {
        return DefaultServiceIntegrations.create();
    }
}
