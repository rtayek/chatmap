package chatmap.app;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;

import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.infrastructure.persistence.sqlite.Database;

/** Opens the configured database and composes application services. */
public final class ApplicationBootstrap {

    private ApplicationBootstrap() {
    }

    public static ServiceGraph open(ResolvedPaths paths, ServiceGraph.Integrations integrations)
            throws IOException, SQLException {
        LoggingBootstrap.initialize(paths);
        Files.createDirectories(paths.homeDirectory());
        Connection connection = new Database("jdbc:sqlite:" + paths.databasePath()).openAndInitialize();
        try {
            return ServiceGraph.create(connection, integrations, paths);
        } catch (RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public static void foo() {
    }
}
