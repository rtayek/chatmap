package chatmap.presentation.mcp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import chatmap.app.ReadOnlyMcpRuntime;

/** Explicit, non-defaulting entry point for the read-only MCP stdio server. */
public final class ReadOnlyMcpServerCli {

    private ReadOnlyMcpServerCli() {
    }

    public static void main(String[] args) {
        System.setProperty("logback.configurationFile", "chatmap/presentation/mcp/read-only-mcp-logback.xml");
        try {
            Options options = parse(List.of(args));
            try (ReadOnlyMcpRuntime runtime = ReadOnlyMcpRuntime.open(
                    options.home(), options.projectId(), options.projectRoot())) {
                ReadOnlyMcpServer.run(runtime.memoryReads(), System.in, System.out);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.err.println("Read-only MCP server interrupted.");
            System.exit(1);
        } catch (Exception failure) {
            System.err.println("Could not start read-only MCP server: " + failure.getMessage());
            System.exit(2);
        }
    }

    static Options parse(List<String> args) {
        Path home = null;
        Path projectRoot = null;
        Long projectId = null;
        List<String> remaining = new ArrayList<>(args);
        while (!remaining.isEmpty()) {
            String option = remaining.removeFirst();
            switch (option) {
                case "--home" -> home = pathValue(option, remaining);
                case "--project-root" -> projectRoot = pathValue(option, remaining);
                case "--project-id" -> projectId = positiveLong(option, stringValue(option, remaining));
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        if (home == null) {
            throw new IllegalArgumentException("Required option missing: --home <directory>");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("Required option missing: --project-id <positive id>");
        }
        return new Options(home, projectId, projectRoot);
    }

    private static Path pathValue(String option, List<String> remaining) {
        return Path.of(stringValue(option, remaining)).toAbsolutePath().normalize();
    }

    private static String stringValue(String option, List<String> remaining) {
        if (remaining.isEmpty() || remaining.getFirst().isBlank()) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return remaining.removeFirst();
    }

    private static long positiveLong(String option, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(option + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(option + " must be a positive integer", failure);
        }
    }

    record Options(Path home, long projectId, Path projectRoot) {
    }
}
