package chatmap.ui;

import java.sql.Connection;

import chatmap.service.ServiceGraph;

/** Builds a {@link ChatMapController} from the shared {@link ServiceGraph} wiring. */
public final class ChatMapControllerFactory {

    private ChatMapControllerFactory() {
    }

    public static ChatMapController create(Connection connection) {
        ServiceGraph services = ServiceGraph.create(connection);
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
}
