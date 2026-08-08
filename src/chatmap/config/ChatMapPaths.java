package chatmap.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Resolves ChatMap runtime data paths without creating files or directories. */
public final class ChatMapPaths {
    public static final String CHATMAP_HOME = "CHATMAP_HOME";
    private static final String PROJECT_LOCAL_DIR = ".chatmap-local";
    private static final String LEGACY_DIR = ".chatmap";
    private static final String DATABASE_FILE = "chatmap.db";

    private ChatMapPaths() {
    }

    public static Path homeDirectory() {
        return parse(List.of()).paths().homeDirectory();
    }

    public static Path databasePath() {
        return parse(List.of()).paths().databasePath();
    }

    public static Path transcriptsDirectory() {
        return parse(List.of()).paths().transcriptsDirectory();
    }

    public static ParsedArguments parse(String[] args) {
        return parse(Arrays.asList(args));
    }

    public static ParsedArguments parse(List<String> args) {
        return resolve(args, System.getenv(), Path.of(System.getProperty("user.home")), Path.of(""));
    }

    public static String diagnostics(ResolvedPaths paths) {
        return "ChatMap home: " + paths.homeDirectory() + System.lineSeparator()
                + "ChatMap database: " + paths.databasePath();
    }

    static ParsedArguments resolve(List<String> args, Map<String, String> environment, Path userHome, Path currentDirectory) {
        ParsedOptions options = parseOptions(args);
        Path home = selectHome(options.explicitHome(), environment, userHome, currentDirectory);
        return new ParsedArguments(new ResolvedPaths(home, home.resolve(DATABASE_FILE)), options.remainingArgs());
    }

    private static ParsedOptions parseOptions(List<String> args) {
        Path explicitHome = null;
        List<String> remaining = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--home".equals(arg)) {
                if (i + 1 >= args.size()) {
                    throw new IllegalArgumentException("Missing value for --home <directory>.");
                }
                String value = args.get(++i);
                if (value.isBlank()) {
                    throw new IllegalArgumentException("--home requires a nonblank directory.");
                }
                explicitHome = normalize(value);
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + arg + ". Supported option: --home <directory>.");
            } else {
                remaining.add(arg);
            }
        }
        return new ParsedOptions(explicitHome, remaining);
    }

    private static Path selectHome(Path explicitHome, Map<String, String> environment, Path userHome, Path currentDirectory) {
        Path currentHome = currentDirectory.toAbsolutePath().normalize().resolve(PROJECT_LOCAL_DIR).normalize();
        Path legacyHome = userHome.toAbsolutePath().normalize().resolve(LEGACY_DIR).normalize();
        if (explicitHome != null) {
            return explicitHome;
        }
        String configuredHome = environment.get(CHATMAP_HOME);
        if (configuredHome != null && !configuredHome.isBlank()) {
            return normalize(configuredHome);
        }
        if (Files.isDirectory(currentHome)) {
            return currentHome;
        }
        if (Files.isDirectory(legacyHome)) {
            return legacyHome;
        }
        throw new IllegalArgumentException("No ChatMap home was selected. Checked current-directory home "
                + currentHome + " and legacy home " + legacyHome
                + ". Use --home <directory> or set CHATMAP_HOME to choose a ChatMap home.");
    }

    private static Path normalize(String path) {
        return Path.of(normalizeEnvironmentPath(path)).toAbsolutePath().normalize();
    }

    private static String normalizeEnvironmentPath(String path) {
        if (File.separatorChar == '\\' && path.matches("^/[A-Za-z]/.*")) {
            return Character.toUpperCase(path.charAt(1)) + ":" + path.substring(2).replace('/', '\\');
        }
        return path;
    }

    public record ResolvedPaths(Path homeDirectory, Path databasePath) {
        public ResolvedPaths {
            homeDirectory = homeDirectory.toAbsolutePath().normalize();
            databasePath = databasePath.toAbsolutePath().normalize();
        }

        public Path transcriptsDirectory() {
            return homeDirectory.resolve("transcripts");
        }
    }

    public record ParsedArguments(ResolvedPaths paths, List<String> remainingArgs) {
        public ParsedArguments {
            remainingArgs = List.copyOf(remainingArgs);
        }
    }

    private record ParsedOptions(Path explicitHome, List<String> remainingArgs) {
        private ParsedOptions {
            remainingArgs = List.copyOf(remainingArgs);
        }
    }
}
