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
        ServiceGraph services = ServiceGraph.create(connection, integrations);
        return new ChatMapController(
                services.importService(),
                services.exportService(),
                services.searchService(),
                services.projectService(),
                services.tagService(),
                services.summaryService(),
                services.liveChatFetchService(),
                services.archiveImportService());
    }

    static Integrations defaultIntegrations() {
        return DefaultServiceIntegrations.create();
    }
}
