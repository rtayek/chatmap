package chatmap.application.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans a workspace directory tree for candidate chat/transcript files,
 * grouped by immediate project subfolder. Used by {@code ChatConsolidatorCli}
 * to discover what to import -- the file-type/heuristic classification of
 * "what counts as a chat file" is a business rule of this application, not a
 * CLI-presentation concern, so it lives here rather than in the CLI class.
 */
public final class WorkspaceScanService {

    private static final Set<String> IGNORE_DIRS = Set.of(
            ".git", ".gradle", ".settings", ".venv", "__pycache__", "node_modules",
            ".pytest_cache", ".ruff_cache", "build", "bin", "target", ".metadata", "gradle"
    );

    private WorkspaceScanService() {
    }

    public static Map<String, List<Path>> scanWorkspace(Path root) throws IOException {
        Map<String, List<Path>> map = new HashMap<>();

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return map;
        }

        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                  .filter(WorkspaceScanService::isEligibleProjectDir)
                  .forEach(projDir -> {
                      Path projDirName = projDir.getFileName();
                      List<Path> found = findChatFiles(projDir);
                      if (projDirName != null && !found.isEmpty()) {
                          map.put(projDirName.toString(), found);
                      }
                  });
        }
        return map;
    }

    private static boolean isEligibleProjectDir(Path dir) {
        Path name = dir.getFileName();
        return name != null && !isIgnoredDirName(name.toString());
    }

    static List<Path> findChatFiles(Path dir) {
        List<Path> list = new ArrayList<>();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path subDir, BasicFileAttributes attrs) {
                    // Prune ignored/hidden subtrees instead of walking into them: faster,
                    // and avoids errors from unreadable dirs like .git or node_modules.
                    Path fn = subDir.getFileName();
                    String name = (fn != null) ? fn.toString() : "";
                    if (!subDir.equals(dir) && isIgnoredDirName(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isCandidateChatFile(file)) {
                        list.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE; // skip anything we cannot read
                }
            });
        } catch (IOException ignored) {
            // ignore inaccessible roots
        }
        return list;
    }

    /**
     * True for directory names we never descend into. Matches the ignore list
     * and dot-prefixed hidden dirs (.git, .claude, ...), but explicitly not the
     * path-traversal segments "." and "..", which are not hidden directories --
     * treating ".." as hidden was what made a relative root like ".." match
     * nothing.
     */
    public static boolean isIgnoredDirName(String name) {
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        return IGNORE_DIRS.contains(name) || name.startsWith(".");
    }

    public static boolean isCandidateChatFile(Path file) {
        Path fn = file.getFileName();
        String name = (fn != null) ? fn.toString().toLowerCase() : "";
        if (name.endsWith(".java") || name.endsWith(".class") || name.endsWith(".jar")
                || name.endsWith(".gradle") || name.endsWith(".xml") || name.endsWith(".properties")) {
            return false;
        }
        // Source/script files whose names happen to contain a keyword (e.g.
        // "chatgpt-web-SESSIONS.sh") are not transcripts; exclude them.
        if (name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".ps1")
                || name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".ts")
                || name.endsWith(".kt") || name.endsWith(".go") || name.endsWith(".rb")) {
            return false;
        }
        // Binary/image files whose names happen to contain a keyword (e.g. "weCHAT_qr.png")
        // are not transcripts; exclude them so they are never fed to a text importer.
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".svg")
                || name.endsWith(".pdf") || name.endsWith(".zip") || name.endsWith(".gz")) {
            return false;
        }
        // Our own consolidated output must never be re-ingested (feedback loop).
        if (name.endsWith("_consolidated.md")) {
            return false;
        }

        if (name.endsWith("conversations.json") || name.contains("chat") || name.contains("session") || name.contains("handoff") || name.contains("transcript")) {
            return true;
        }

        Path parent = file.getParent();
        if (parent != null) {
            Path pFn = parent.getFileName();
            String pName = (pFn != null) ? pFn.toString().toLowerCase() : "";
            if (pName.equals("chats") || pName.equals("runs")) {
                return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".json");
            }
        }

        return false;
    }
}
