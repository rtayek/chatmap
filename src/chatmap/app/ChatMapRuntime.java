package chatmap.app;

import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.ChatMapPaths.ResolvedPaths;
import chatmap.service.ServiceGraph;
import chatmap.storage.Database;
import chatmap.ui.ChatMapController;

/** Owns application paths, database/service wiring, background work, and shutdown. */
public final class ChatMapRuntime implements AutoCloseable {
    private static final Duration shutdownTimeout = Duration.ofSeconds(5);

    private final ResolvedPaths paths;
    private final ServiceGraph services;
    private final ChatMapController controller;
    private final SerializedTaskExecutor backgroundExecutor;

    private ChatMapRuntime(ResolvedPaths paths, ServiceGraph services,
            ChatMapController controller, SerializedTaskExecutor backgroundExecutor) {
        this.paths = paths;
        this.services = services;
        this.controller = controller;
        this.backgroundExecutor = backgroundExecutor;
    }

    public static ChatMapRuntime open(List<String> rawArguments) throws Exception {
        ParsedArguments parsedArguments = ChatMapPaths.parse(rawArguments);
        if (!parsedArguments.remainingArgs().isEmpty()) {
            throw new IllegalArgumentException("Usage: ChatMap [--home <directory>]");
        }
        ResolvedPaths paths = parsedArguments.paths();
        System.out.println(ChatMapPaths.diagnostics(paths));
        Files.createDirectories(paths.homeDirectory());

        var connection = new Database("jdbc:sqlite:" + paths.databasePath()).openAndInitialize();
        try {
            ServiceGraph services = ServiceGraph.create(connection, DefaultServiceIntegrations.create());
            SerializedTaskExecutor executor = new SerializedTaskExecutor("chatmap-background");
            return new ChatMapRuntime(paths, services, new ChatMapController(services), executor);
        } catch (Exception failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public ResolvedPaths paths() {
        return paths;
    }

    public ChatMapController controller() {
        return controller;
    }

    public Future<?> submit(Runnable task) {
        return backgroundExecutor.submit(task);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return backgroundExecutor.submit(task);
    }

    @Override
    public void close() throws Exception {
        boolean workerStopped = backgroundExecutor.shutdownAndAwait(shutdownTimeout);
        if (workerStopped) {
            services.close();
        } else {
            System.err.println("[ChatMap] Background work did not stop in time; "
                    + "leaving the database connection for the JVM to release on exit.");
        }
    }
}
