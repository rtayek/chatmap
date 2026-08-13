package chatmap.app.bootstrap;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.app.bootstrap.ChatMapPaths.ResolvedPaths;

/** Establishes the logging destination before any application logger is created. */
public final class LoggingBootstrap {
    // Smoke test verification
    public static final String LOG_DIRECTORY_PROPERTY = "chatmap.log.dir";

    private LoggingBootstrap() {
    }

    public static ParsedArguments bootstrap(String[] args) {
        return bootstrap(Arrays.asList(args));
    }

    public static ParsedArguments bootstrap(List<String> args) {
        return bootstrap(args, parsedArguments -> {
        });
    }

    public static ParsedArguments bootstrap(String[] args, Consumer<ParsedArguments> validator) {
        return bootstrap(Arrays.asList(args), validator);
    }

    public static ParsedArguments bootstrap(List<String> args, Consumer<ParsedArguments> validator) {
        establishTemporaryFallback();
        try {
            ParsedArguments parsedArguments = ChatMapPaths.parse(args);
            validator.accept(parsedArguments);
            initialize(parsedArguments.paths());
            return parsedArguments;
        } catch (RuntimeException failure) {
            recordEarlyFailure(failure);
            throw failure;
        }
    }

    public static void initialize(ResolvedPaths paths) {
        System.setProperty(LOG_DIRECTORY_PROPERTY, paths.logsDirectory().toString());
        initializeLogging();
    }

    public static void initializeTemporaryFallback() {
        establishTemporaryFallback();
        initializeLogging();
    }

    public static Path temporaryLogDirectory() {
        return Path.of(System.getProperty("java.io.tmpdir"), "chatmap-bootstrap", "logs")
                .toAbsolutePath()
                .normalize();
    }

    private static void establishTemporaryFallback() {
        System.setProperty(LOG_DIRECTORY_PROPERTY, temporaryLogDirectory().toString());
    }

    private static void recordEarlyFailure(RuntimeException failure) {
        try {
            initializeTemporaryFallback();
            LoggerFactory.getLogger(LoggingBootstrap.class)
                    .error("ChatMap startup failed before application initialization.", failure);
        } catch (RuntimeException loggingFailure) {
            failure.addSuppressed(loggingFailure);
        }
    }

    private static void initializeLogging() {
        String logDirectory = System.getProperty(LOG_DIRECTORY_PROPERTY);
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof LoggerContext context)
                || logDirectory.equals(context.getProperty(LOG_DIRECTORY_PROPERTY))) {
            return;
        }

        context.reset();
        context.putProperty(LOG_DIRECTORY_PROPERTY, logDirectory);
        try {
            new ContextInitializer(context).autoConfig();
        } catch (JoranException e) {
            throw new IllegalStateException("Could not initialize ChatMap logging in " + logDirectory, e);
        }
    }
}
