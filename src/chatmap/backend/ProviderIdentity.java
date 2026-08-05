package chatmap.backend;

import java.nio.file.Path;

final class ProviderIdentity {

    private ProviderIdentity() {
    }

    static String claudeWebId(String url) {
        return pathId(url, "/chat/");
    }

    static String chatGptWebId(String url) {
        return pathId(url, "/c/");
    }

    static String geminiWebId(String url) {
        String id = pathId(url, "/app/");
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.equalsIgnoreCase("app") ? null : id;
    }

    static String cliSessionId(Path root, Path file) {
        return root.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private static String pathId(String url, String marker) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.strip();
        int start = normalized.indexOf(marker);
        if (start < 0) {
            return null;
        }
        String id = normalized.substring(start + marker.length());
        int query = id.indexOf('?');
        int fragment = id.indexOf('#');
        int end = id.length();
        if (query >= 0) {
            end = Math.min(end, query);
        }
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        id = id.substring(0, end);
        int slash = id.indexOf('/');
        if (slash >= 0) {
            id = id.substring(0, slash);
        }
        return id.isBlank() ? null : id;
    }
}
