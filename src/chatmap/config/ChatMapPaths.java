package chatmap.config;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/** Resolves ChatMap runtime data paths without creating files or directories. */
public final class ChatMapPaths {
    public static final String CHATMAP_HOME = "CHATMAP_HOME";
    private static final String DEFAULT_DIR = ".chatmap";
    private static final String DATABASE_FILE = "chatmap.db";

    private ChatMapPaths() {
    }

    public static Path dataDirectory() {
        return resolve(System.getenv(), Path.of(System.getProperty("user.home")));
    }

    public static Path databasePath() {
        return dataDirectory().resolve(DATABASE_FILE);
    }

    static Path resolve(Map<String, String> environment, Path userHome) {
        String override = environment.get(CHATMAP_HOME);
        Path selected = override == null || override.isBlank()
                ? userHome.resolve(DEFAULT_DIR)
                : Path.of(normalizeEnvironmentPath(override));
        return selected.toAbsolutePath().normalize();
    }

    private static String normalizeEnvironmentPath(String path) {
        if (File.separatorChar == '\\' && path.matches("^/[A-Za-z]/.*")) {
            return Character.toUpperCase(path.charAt(1)) + ":" + path.substring(2).replace('/', '\\');
        }
        return path;
    }
}
