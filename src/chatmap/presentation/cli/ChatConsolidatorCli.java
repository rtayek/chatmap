package chatmap.presentation.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.application.service.ExportService;
import chatmap.application.service.ImportService;
import chatmap.application.service.ProjectService;
import chatmap.application.service.WorkspaceScanService;
import chatmap.application.port.persistence.ChatStore;

/**
 * Command-line tool for scanning workspace projects, importing all chat logs and transcripts,
 * and consolidating them into project-level Markdown handoff summaries.
 */
public final class ChatConsolidatorCli {

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, usage());
        List<String> remaining = parsedArguments.remainingArgs();
        Path rootPath = !remaining.isEmpty() ? Paths.get(remaining.get(0)) : Paths.get("..");
        Path outputPath = remaining.size() > 1 ? Paths.get(remaining.get(1)) : Paths.get("consolidated_output");

        System.out.println("==================================================");
        System.out.println("🚀 ChatMap Java Workspace Chat Consolidator");
        System.out.println("==================================================");
        System.out.println("Scanning root: " + rootPath.toAbsolutePath().normalize());
        System.out.println("Output dir:    " + outputPath.toAbsolutePath().normalize());
        System.out.println();

        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            ChatStore chats = context.services().chats();
            ProjectService projectService = context.services().projectService();

            ImportService importService = context.services().importService();
            ExportService exportService = context.services().exportService();

            Map<String, List<Path>> projectFiles = WorkspaceScanService.scanWorkspace(rootPath);

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

                // Each file's import commits independently (via ImportService's own
                // transaction), so one bad file — or a later failure reading the
                // handoff — never rolls back files that already imported successfully.
                Project project = projectService.findOrCreate(
                        projectName, "Consolidated chats for " + projectName, timestamp);

                for (Path file : files) {
                    try {
                        Chat imported = importService.importFile(file);
                        chats.assignProject(imported.id(), project.id());
                        System.out.println("   + Imported: " + file.getFileName());
                    } catch (Exception e) {
                        System.err.println("   ! Error importing " + file + ": " + e.getMessage());
                    }
                }

                String consolidatedMd = exportService.exportProjectHandoff(project.id(), timestamp)
                        .orElseThrow(() -> new IllegalStateException(
                                "Failed to export handoff for project " + projectName));

                Path outFile = outputPath.resolve(projectName + "_CONSOLIDATED.md");
                Files.writeString(outFile, consolidatedMd);

                System.out.println("   => Written: " + outFile.toAbsolutePath().normalize() + "\n");
            }

            System.out.println("==================================================");
            System.out.println("✅ Consolidation complete for " + projectFiles.size() + " project(s)!");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("❌ Fatal Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String usage() {
        return "Usage: chatConsolidator [--home <directory>] [<root>] [<outputDir>]";
    }
}
