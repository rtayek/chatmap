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
        String content = "# Agent Result: " + archivedTaskFile.getFileName() + "\n\n"
                + "- Project: " + task.projectKey() + "\n"
                + "- Agent: " + task.agent() + "\n"
                + "- Branch: " + task.branch() + "\n"
                + "- Timestamp: " + clock.instant() + "\n"
                + "- Exit code: " + agentResult.exitCode() + "\n\n"
                + "- Full stdout: " + optionalRelativeName(archivedTaskFile.getParent(), agentResult.standardOutputPath()) + "\n"
                + "- Full stderr: " + optionalRelativeName(archivedTaskFile.getParent(), agentResult.standardErrorPath()) + "\n\n"
                + "## Output\n\n"
                + agentResult.standardOutput();
        try {
            fileStore.writeString(resultPath, content);
            return resultPath;
        } catch (HandoffFileStoreException failure) {
            LOG.warn("Could not write agent result file {}: {}", resultPath, failure.getMessage());
            return null;
        }
    }

    Path writeFailureReport(Path taskFile, String projectKey, String reason, CommandResult agentResult, Path inboxRepo) {
        Path reportPath = failureReportPath(taskFile);
        String report = "# Handoff Failure Report\n\n"
                + "- Source file: " + GitWorkspaceManager.relativeName(inboxRepo, taskFile) + "\n"
                + "- Project: " + projectKey + "\n"
                + "- Timestamp: " + clock.instant() + "\n\n"
                + "## Reason\n\n"
                + reason + "\n";
        if (agentResult != null) {
            report += "\n## Agent Output\n\n" + agentResult.standardOutput() + "\n";
        }
        fileStore.writeString(reportPath, report);
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
