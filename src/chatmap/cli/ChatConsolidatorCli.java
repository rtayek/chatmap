package chatmap.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.ProjectRepository;

/**
 * Command-line tool for scanning workspace projects, importing all chat logs and transcripts,
 * and consolidating them into project-level Markdown handoff summaries.
 */
public final class ChatConsolidatorCli {

    private static final Set<String> IGNORE_DIRS = Set.of(
            ".git", ".gradle", ".settings", ".venv", "__pycache__", "node_modules",
            ".pytest_cache", ".ruff_cache", "build", "bin", "target", ".metadata", "gradle"
    );

    public static void main(String[] args) {
        Path rootPath = args.length > 0 ? Paths.get(args[0]) : Paths.get("..");
        Path outputPath = args.length > 1 ? Paths.get(args[1]) : Paths.get("consolidated_output");

        System.out.println("==================================================");
        System.out.println("🚀 ChatMap Java Workspace Chat Consolidator");
        System.out.println("==================================================");
        System.out.println("Scanning root: " + rootPath.toAbsolutePath().normalize());
        System.out.println("Output dir:    " + outputPath.toAbsolutePath().normalize());
        System.out.println();

        try (Connection conn = Database.connectInMemory()) {
            Database.initialize(conn);
            CliBootstrap.CliContext context = CliBootstrap.createContext(conn, null);

            ChatRepository chats = context.services().chats();
            ProjectRepository projects = context.services().projects();

            ImportService importService = context.services().importService();
            ExportService exportService = context.services().exportService();

            Map<String, List<Path>> projectFiles = scanWorkspace(rootPath);

            if (projectFiles.isEmpty()) {
                System.out.println("⚠️ No chat files found in workspace.");
                return;
            }

            Files.createDirectories(outputPath);
            String timestamp = Instant.now().toString();

            for (Map.Entry<String, List<Path>> entry : projectFiles.entrySet()) {
                String projectName = entry.getKey();
                List<Path> files = entry.getValue();

                System.out.printf("📦 Consolidating Project [%s] (%d files)...%n", projectName, files.size());

                Project project = projects.insert(new Project(0, projectName,
                        "Consolidated chats for " + projectName, timestamp, timestamp));

                for (Path file : files) {
                    try {
                        Chat imported = importService.importFile(file);
                        chats.assignProject(imported.id(), project.id());
                        System.out.println("   + Imported: " + file.getFileName());
                    } catch (Exception e) {
                        System.err.println("   ! Error importing " + file + ": " + e.getMessage());
                    }
                }

                // Generate consolidated output via ExportService / HandoffExporter
                String consolidatedMd = exportService.exportProjectHandoff(project.id(), timestamp)
                        .orElseThrow(() -> new IllegalStateException("Failed to export handoff for project " + projectName));
                Path outFile = outputPath.resolve(projectName + "_CONSOLIDATED.md");
                Files.writeString(outFile, consolidatedMd);

                System.out.println("   => Written: " + outFile.toAbsolutePath().normalize() + "\n");
            }

            System.out.println("==================================================");
            System.out.println("✅ Consolidation complete for " + projectFiles.size() + " project(s)!");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("❌ Fatal Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static Map<String, List<Path>> scanWorkspace(Path root) throws IOException {
        Map<String, List<Path>> map = new HashMap<>();

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return map;
        }

        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !isIgnoredDirName(p.getFileName().toString()))
                  .forEach(projDir -> {
                      List<Path> found = findChatFiles(projDir);
                      if (!found.isEmpty()) {
                          map.put(projDir.getFileName().toString(), found);
                      }
                  });
        }
        return map;
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
    static boolean isIgnoredDirName(String name) {
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        return IGNORE_DIRS.contains(name) || name.startsWith(".");
    }

    static boolean isCandidateChatFile(Path file) {
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
