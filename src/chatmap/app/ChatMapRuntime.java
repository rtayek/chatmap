package chatmap.app;

import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.slf4j.Logger;

import chatmap.app.bootstrap.ChatMapPaths;
import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;
import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.app.ServiceGraph;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.presentation.ui.ChatMapController;
import chatmap.application.support.Log;

/**
 * Owns application paths, database/service wiring, background work, and shutdown.
 *
 * Background work runs on two independent lanes so a slow LLM-backend or live web/CDP
 * fetch (can run for minutes) never queues behind, or blocks, a fast DB-only action
 * (search, chat-list load, import) and vice versa. Both lanes are single-threaded:
 * storage access already serializes structurally inside the repositories
 * (synchronized(conn), see ChatRepository), so neither lane needs more than one
 * thread for correctness -- this only removes the queueing they forced on each other
 * by sharing one thread.
 */
public final class ChatMapRuntime implements AutoCloseable {
    private static final Duration shutdownTimeout = Duration.ofSeconds(5);

    private final Logger log;
    private final ResolvedPaths paths;
    private final ServiceGraph services;
    private final ChatMapController controller;
    private final SerializedTaskExecutor dbExecutor;
    private final SerializedTaskExecutor backendExecutor;

    private ChatMapRuntime(Logger log, ResolvedPaths paths, ServiceGraph services, ChatMapController controller,
            SerializedTaskExecutor dbExecutor, SerializedTaskExecutor backendExecutor) {
        this.log = log;
        this.paths = paths;
        this.services = services;
        this.controller = controller;
        this.dbExecutor = dbExecutor;
        this.backendExecutor = backendExecutor;
    }

    public static ChatMapRuntime open(List<String> rawArguments) throws Exception {
        ParsedArguments parsedArguments = LoggingBootstrap.bootstrap(rawArguments, ChatMapRuntime::validateArguments);
        ResolvedPaths paths = parsedArguments.paths();
        Logger log = Log.of(ChatMapRuntime.class);
        log.info(ChatMapPaths.diagnostics(paths));
        Files.createDirectories(paths.homeDirectory());

        var connection = new Database("jdbc:sqlite:" + paths.databasePath()).openAndInitialize();
        try {
            ServiceGraph services = ServiceGraph.create(connection, DefaultServiceIntegrations.create(), paths);
            SerializedTaskExecutor dbExecutor = new SerializedTaskExecutor("chatmap-db");
            SerializedTaskExecutor backendExecutor = new SerializedTaskExecutor("chatmap-llm-backend");
            return new ChatMapRuntime(log, paths, services, new ChatMapController(services), dbExecutor,
                    backendExecutor);
        } catch (Exception failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static void validateArguments(ParsedArguments parsedArguments) {
        if (!parsedArguments.remainingArgs().isEmpty()) {
            throw new IllegalArgumentException("Usage: ChatMap [--home <directory>]");
        }
    }

    public ResolvedPaths paths() {
        return paths;
    }

    public ChatMapController controller() {
        return controller;
    }

    /** Fast lane: DB-only work (search, list, import, project/tag operations). */
    public Future<?> submit(Runnable task) {
        return dbExecutor.submit(task);
    }

    /** Fast lane: DB-only work (search, list, import, project/tag operations). */
    public <T> Future<T> submit(Callable<T> task) {
        return dbExecutor.submit(task);
    }

    /** Slow lane: LLM backend calls (summarize) and live provider fetches (web/CDP). */
    public Future<?> submitBackendWork(Runnable task) {
        return backendExecutor.submit(task);
    }

    /** Slow lane: LLM backend calls (summarize) and live provider fetches (web/CDP). */
    public <T> Future<T> submitBackendWork(Callable<T> task) {
        return backendExecutor.submit(task);
    }

    @Override
    public void close() throws Exception {
        boolean dbStopped = dbExecutor.shutdownAndAwait(shutdownTimeout);
        boolean backendStopped = backendExecutor.shutdownAndAwait(shutdownTimeout);
        if (dbStopped && backendStopped) {
            services.close();
        } else {
            log.warn("Background work did not stop in time; "
                    + "leaving the database connection for the JVM to release on exit.");
        }
    }
}
