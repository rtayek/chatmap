package chatmap.cli;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.file.Files;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.ChatMapPaths.ResolvedPaths;
import chatmap.service.ServiceGraph;
import chatmap.storage.Database;

/** Shared bootstrapping helper for CLI entry points. */
public final class CliBootstrap {

    private CliBootstrap() {
    }

    /**
     * A CLI's ChatMap home paths plus the shared {@link ServiceGraph}. Reach
     * repositories and services through {@link #services()} — the wiring lives in
     * exactly one place ({@link ServiceGraph#create}).
     */
    public record CliContext(ServiceGraph services, ResolvedPaths paths) implements AutoCloseable {

        @Override
        public void close() throws SQLException {
            services.close();
        }
    }

    public static CliContext open(String[] args) throws IOException, SQLException {
        return open(ChatMapPaths.parse(args));
    }

    public static CliContext open(ParsedArguments parsedArguments) throws IOException, SQLException {
        return open(parsedArguments, ServiceGraph.Integrations.none());
    }

    public static CliContext open(ParsedArguments parsedArguments, ServiceGraph.Integrations integrations)
            throws IOException, SQLException {
        ResolvedPaths paths = parsedArguments.paths();
        Files.createDirectories(paths.homeDirectory());
        Connection conn = new Database("jdbc:sqlite:" + paths.databasePath()).openAndInitialize();
        return createContext(conn, paths, integrations);
    }

    public static CliContext createContext(Connection connection, ResolvedPaths paths) {
        return createContext(connection, paths, ServiceGraph.Integrations.none());
    }

    public static CliContext createContext(
            Connection connection, ResolvedPaths paths, ServiceGraph.Integrations integrations) {
        return new CliContext(ServiceGraph.create(connection, integrations), paths);
    }
}
