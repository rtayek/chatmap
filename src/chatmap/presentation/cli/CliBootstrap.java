package chatmap.presentation.cli;

import java.io.IOException;
import java.sql.SQLException;
import java.util.function.IntConsumer;

import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.app.ApplicationBootstrap;
import chatmap.app.ServiceGraph;

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
        return open(parse(args));
    }

    public static ParsedArguments parse(String[] args) {
        return LoggingBootstrap.bootstrap(args);
    }

    /**
     * Parses CLI arguments, printing {@code e.getMessage()} plus {@code usage}
     * and exiting with status 1 on failure instead of throwing.
     */
    public static ParsedArguments parseOrExit(String[] args, String usage) {
        return parseOrExit(args, usage, System::exit);
    }

    static ParsedArguments parseOrExit(String[] args, String usage, IntConsumer exitHandler) {
        try {
            return parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            exitWithUsage(usage, exitHandler);
            throw new AssertionError("unreachable");
        }
    }

    /** Prints {@code usage} to stderr and exits with status 1. */
    public static void exitWithUsage(String usage) {
        exitWithUsage(usage, System::exit);
    }

    static void exitWithUsage(String usage, IntConsumer exitHandler) {
        System.err.println(usage);
        exitHandler.accept(1);
    }

    public static CliContext open(ParsedArguments parsedArguments) throws IOException, SQLException {
        return open(parsedArguments, ServiceGraph.Integrations.none());
    }

    public static CliContext open(ParsedArguments parsedArguments, ServiceGraph.Integrations integrations)
            throws IOException, SQLException {
        ResolvedPaths paths = parsedArguments.paths();
        return new CliContext(ApplicationBootstrap.open(paths, integrations), paths);
    }
}
