package chatmap.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.exporter.ChatExportModel;
import chatmap.exporter.MarkdownExporter;
import chatmap.exporter.ProjectHandoffModel;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.TagRepository;

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

            ChatRepository chats = new ChatRepository(conn);
            MessageRepository messages = new MessageRepository(conn);
            ProjectRepository projects = new ProjectRepository(conn);
            TagRepository tags = new TagRepository(conn);

            ImportService importService = new ImportService(chats, messages);
            ExportService exportService = new ExportService(chats, messages, projects, tags);

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

                System.out.printf("📦 Consolidating Project [%s] (%d files)...\n", projectName, files.size());

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

                // Generate consolidated output
                String consolidatedMd = generateConsolidatedProjectMarkdown(project, chats, messages, exportService, timestamp);
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

    private static Map<String, List<Path>> scanWorkspace(Path root) throws IOException {
        Map<String, List<Path>> map = new HashMap<>();

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return map;
        }

        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !IGNORE_DIRS.contains(p.getFileName().toString()))
                  .filter(p -> !p.getFileName().toString().startsWith("."))
                  .forEach(projDir -> {
                      List<Path> found = findChatFiles(projDir);
                      if (!found.isEmpty()) {
                          map.put(projDir.getFileName().toString(), found);
                      }
                  });
        }
        return map;
    }

    private static List<Path> findChatFiles(Path dir) {
        List<Path> list = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> !isIgnoredPath(p))
                  .filter(ChatConsolidatorCli::isCandidateChatFile)
                  .forEach(list::add);
        } catch (IOException e) {
            // ignore inaccessible dirs
        }
        return list;
    }

    private static boolean isIgnoredPath(Path path) {
        for (Path part : path) {
            if (IGNORE_DIRS.contains(part.toString()) || part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCandidateChatFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".java") || name.endsWith(".class") || name.endsWith(".jar")
                || name.endsWith(".gradle") || name.endsWith(".xml") || name.endsWith(".properties")) {
            return false;
        }

        if (name.endsWith("conversations.json") || name.contains("chat") || name.contains("session") || name.contains("handoff") || name.contains("transcript")) {
            return true;
        }

        Path parent = file.getParent();
        if (parent != null) {
            String pName = parent.getFileName().toString().toLowerCase();
            if (pName.equals("chats") || pName.equals("runs")) {
                return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".json");
            }
        }

        return false;
    }

    private static String generateConsolidatedProjectMarkdown(
            Project project,
            ChatRepository chats,
            MessageRepository messages,
            ExportService exportService,
            String timestamp) throws SQLException {

        StringBuilder sb = new StringBuilder();
        sb.append("# Consolidated Project Memory: ").append(project.name()).append("\n\n");
        sb.append("Exported: ").append(timestamp).append("\n");
        sb.append("Description: ").append(project.description()).append("\n\n");

        List<Chat> projectChats = chats.findByProject(project.id());
        sb.append("## Index of Consolidated Chats (Total: ").append(projectChats.size()).append(")\n\n");
        sb.append("| # | Title | Source | Imported At |\n");
        sb.append("|---|-------|--------|-------------|\n");

        for (int i = 0; i < projectChats.size(); i++) {
            Chat c = projectChats.get(i);
            sb.append(String.format("| %d | %s | %s | %s |\n", (i + 1), c.title(), c.source().dbValue(), c.importedAt()));
        }

        sb.append("\n---\n\n");
        sb.append("## Detailed Transcripts\n\n");

        MarkdownExporter mdExporter = new MarkdownExporter();
        for (int i = 0; i < projectChats.size(); i++) {
            Chat c = projectChats.get(i);
            sb.append("### Session ").append(i + 1).append(": ").append(c.title()).append("\n\n");

            var exportModel = exportService.loadChat(c.id());
            if (exportModel.isPresent()) {
                String chatMd = mdExporter.exportChat(exportModel.get());
                sb.append(chatMd).append("\n\n");
            }
            sb.append("---\n\n");
        }

        return sb.toString();
    }
}
