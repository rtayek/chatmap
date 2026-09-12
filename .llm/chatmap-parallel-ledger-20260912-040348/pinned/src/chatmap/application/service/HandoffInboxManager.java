package chatmap.application.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;

import chatmap.application.port.command.CommandResult;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.application.port.handoff.HandoffFileStoreException;
import chatmap.application.support.Log;
import chatmap.domain.HandoffTask;

class HandoffInboxManager {
    private static final Logger LOG = Log.of(HandoffInboxManager.class);
    static final String ARCHIVE_SUBDIR = ".archive";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final HandoffFileStore fileStore;
    private final Clock clock;

    HandoffInboxManager(HandoffFileStore fileStore, Clock clock) {
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    List<Path> scanForTaskFiles(Path inboxRepo) {
        List<Path> files = new ArrayList<>();
        for (Path projectDir : fileStore.listDirectories(inboxRepo).stream()
                .filter(HandoffInboxManager::isNotHidden).toList()) {
            fileStore.listFiles(projectDir).stream()
                    .filter(HandoffInboxManager::isEligibleTaskFile)
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    String projectKeyOf(Path file) {
        Path parent = file.getParent();
        Path parentName = parent == null ? null : parent.getFileName();
        return parentName == null ? "unknown" : parentName.toString();
    }

    Path writeResultFile(Path archivedTaskFile, HandoffTask task, CommandResult agentResult) {
        Path resultPath = resultFilePath(archivedTaskFile);
        StringBuilder content = new StringBuilder()
                .append("# Agent Result: ").append(archivedTaskFile.getFileName()).append("\n\n")
                .append("- Project: ").append(task.projectKey()).append("\n")
                .append("- Agent: ").append(task.agent()).append("\n")
                .append("- Branch: ").append(task.branch()).append("\n")
                .append("- Timestamp: ").append(clock.instant()).append("\n")
                .append("- Exit code: ").append(agentResult.exitCode()).append("\n\n")
                .append("- Full stdout: ")
                .append(optionalRelativeName(archivedTaskFile.getParent(), agentResult.standardOutputPath())).append("\n")
                .append("- Full stderr: ")
                .append(optionalRelativeName(archivedTaskFile.getParent(), agentResult.standardErrorPath())).append("\n");
        MarkdownFences.appendFencedSection(content, "Output", agentResult.standardOutput());
        try {
            fileStore.writeString(resultPath, content.toString());
            return resultPath;
        } catch (HandoffFileStoreException failure) {
            LOG.warn("Could not write agent result file {}: {}", resultPath, failure.getMessage());
            return null;
        }
    }

    Path writeFailureReport(Path taskFile, String projectKey, String reason, CommandResult agentResult, Path inboxRepo) {
        Path reportPath = failureReportPath(taskFile);
        StringBuilder report = new StringBuilder()
                .append("# Handoff Failure Report\n\n")
                .append("- Source file: ").append(GitWorkspaceManager.relativeName(inboxRepo, taskFile)).append("\n")
                .append("- Project: ").append(projectKey).append("\n")
                .append("- Timestamp: ").append(clock.instant()).append("\n\n")
                .append("## Reason\n\n")
                .append(reason).append("\n");
        if (agentResult != null) {
            MarkdownFences.appendFencedSection(report, "Agent Output", agentResult.standardOutput());
        }
        fileStore.writeString(reportPath, report.toString());
        return reportPath;
    }

    Path archiveTask(Path taskFile) {
        return fileStore.archive(taskFile, ARCHIVE_SUBDIR);
    }

    Path agentOutputPath(Path taskFile, String suffix) {
        Path fileName = taskFile.getFileName();
        String baseName = (fileName == null ? "task" : fileName.toString()).replaceFirst("(?i)\\.md$", "");
        Path taskParent = taskFile.getParent();
        Path parent = taskParent == null ? Path.of(".") : taskParent;
        return parent.resolve(ARCHIVE_SUBDIR).resolve(baseName + "." + suffix);
    }

    private Path resultFilePath(Path archivedTaskFile) {
        Path fileName = archivedTaskFile.getFileName();
        String baseName = (fileName == null ? "task" : fileName.toString()).replaceFirst("(?i)\\.md$", "");
        return archivedTaskFile.resolveSibling(baseName + ".result.md");
    }

    private Path failureReportPath(Path taskFile) {
        String stamp = TIMESTAMP.format(clock.instant().atZone(java.time.ZoneOffset.UTC));
        Path fileName = taskFile.getFileName();
        String baseName = (fileName == null ? "task" : fileName.toString()).replaceFirst("(?i)\\.md$", "");
        return taskFile.resolveSibling("failure-report-" + baseName + "-" + stamp + ".md");
    }

    private static boolean isNotHidden(Path dir) {
        Path name = dir.getFileName();
        return name != null && !name.toString().startsWith(".");
    }

    private static boolean isEligibleTaskFile(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".md")
                && !name.equalsIgnoreCase("template.md")
                && !name.startsWith("failure-report-");
    }

    private static String optionalRelativeName(Path root, Path file) {
        return file == null ? "(not captured)" : GitWorkspaceManager.relativeName(root, file);
    }
}
