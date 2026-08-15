package chatmap.presentation.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import chatmap.app.HandoffOrchestratorBootstrap;
import chatmap.application.service.HandoffOrchestratorService;
import chatmap.application.service.HandoffRunResult;

/**
 * Standalone entry point for the remote handoff orchestrator: polls a Git
 * inbox repository, runs each discovered task against an isolated worktree
 * of its target project, and reports success/failure back into the inbox.
 *
 * Deliberately does not go through {@link CliBootstrap} -- unlike every
 * other CLI here, this tool has nothing to do with ChatMap's own database or
 * {@code CHATMAP_HOME}; it drives arbitrary other project repositories.
 *
 * Push defaults to on when driven by {@value #DEFAULT_CONFIG} (set
 * {@code autoPush=false} there to opt back out); with no config file, an
 * explicit run needs {@code --auto-push} to enable it. Either way, every
 * run commits locally in whichever repos it touches regardless of the
 * push setting.
 *
 * {@code --inbox}/{@code --registry} may be omitted if
 * {@value #DEFAULT_CONFIG} (relative to the working directory, gitignored
 * and machine-specific) supplies {@code inbox}/{@code registry}/
 * {@code interval}/{@code autoPush} defaults -- explicit CLI flags still
 * override it field by field, so {@code gradle handoffOrchestrator} works
 * with no {@code -Pargs} for the common case.
 */
public final class HandoffOrchestratorCli {

    private static final String DEFAULT_CONFIG = ".chatmap-local/handoff-orchestrator.properties";
    private static final String USAGE = "Usage: handoffOrchestrator --inbox <dir> --registry <projects.properties> "
            + "[--interval <seconds>] [--auto-push]\n"
            + "(or supply inbox/registry/interval/autoPush defaults in " + DEFAULT_CONFIG + ")";

    public static void main(String[] args) throws IOException, InterruptedException {
        Options options = parse(args, loadDefaultConfig());
        if (options == null) {
            System.err.println(USAGE);
            System.exit(1);
            return;
        }

        Map<String, Path> registry = loadRegistry(options.registry());
        HandoffOrchestratorService service = HandoffOrchestratorBootstrap.create(
                registry, Clock.systemUTC(), options.autoPush());

        if (options.intervalSeconds() == null) {
            runOnce(service, options.inbox());
            return;
        }
        System.out.println("Polling " + options.inbox() + " every " + options.intervalSeconds() + "s "
                + "(auto-push=" + options.autoPush() + "). Ctrl-C to stop.");
        while (true) {
            runOnce(service, options.inbox());
            Thread.sleep(options.intervalSeconds() * 1000L);
        }
    }

    private static void runOnce(HandoffOrchestratorService service, Path inbox) {
        List<HandoffRunResult> results = service.processInboxOnce(inbox);
        if (results.isEmpty()) {
            System.out.println("No handoff tasks found.");
            return;
        }
        boolean anyPushPending = false;
        for (HandoffRunResult result : results) {
            System.out.println(result.outcome() + "\t" + result.projectKey()
                    + "\t" + result.sourceFile() + "\t" + result.detail());
            anyPushPending |= result.pushPending();
        }
        if (anyPushPending) {
            System.out.println("Local commits are pending push (--auto-push was not set). "
                    + "Failure reports will not sync to the phone until they are pushed.");
        }
    }

    private static Map<String, Path> loadRegistry(Path registryFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = java.nio.file.Files.newInputStream(registryFile)) {
            properties.load(in);
        }
        Map<String, Path> registry = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            registry.put(key, Path.of(properties.getProperty(key)).toAbsolutePath().normalize());
        }
        return registry;
    }

    /**
     * Loads {@value #DEFAULT_CONFIG} if present, or returns empty defaults.
     * A missing config file is not an error -- explicit {@code --inbox}/
     * {@code --registry} flags are always a valid way to run this tool.
     */
    private static Options loadDefaultConfig() throws IOException {
        Path configFile = Path.of(DEFAULT_CONFIG);
        if (!java.nio.file.Files.isRegularFile(configFile)) {
            return new Options(null, null, null, false);
        }
        Properties properties = new Properties();
        try (InputStream in = java.nio.file.Files.newInputStream(configFile)) {
            properties.load(in);
        }
        return optionsFromProperties(properties);
    }

    /** Package-private for direct testing without touching the filesystem. */
    static Options optionsFromProperties(Properties properties) {
        String inbox = properties.getProperty("inbox");
        String registry = properties.getProperty("registry");
        String interval = properties.getProperty("interval");
        return new Options(
                inbox == null ? null : Path.of(inbox),
                registry == null ? null : Path.of(registry),
                interval == null ? null : Long.parseLong(interval),
                Boolean.parseBoolean(properties.getProperty("autoPush", "true")));
    }

    /** Parses CLI flags, falling back field-by-field to {@code defaults} for anything not given explicitly. */
    static Options parse(String[] args, Options defaults) {
        Path inbox = defaults.inbox();
        Path registry = defaults.registry();
        Long intervalSeconds = defaults.intervalSeconds();
        boolean autoPush = defaults.autoPush();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--inbox" -> inbox = Path.of(requireValue(args, ++i));
                case "--registry" -> registry = Path.of(requireValue(args, ++i));
                case "--interval" -> intervalSeconds = Long.parseLong(requireValue(args, ++i));
                case "--auto-push" -> autoPush = true;
                default -> {
                    return null;
                }
            }
        }
        if (inbox == null || registry == null) {
            return null;
        }
        return new Options(inbox.toAbsolutePath().normalize(), registry.toAbsolutePath().normalize(),
                intervalSeconds, autoPush);
    }

    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    record Options(Path inbox, Path registry, Long intervalSeconds, boolean autoPush) {
    }
}
