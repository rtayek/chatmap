package chatmap.application.port.handoff;

import java.nio.file.Path;
import java.util.List;

/**
 * Filesystem operations {@code HandoffOrchestratorService} needs, named
 * around what it actually does with them rather than as a generic
 * filesystem abstraction -- task classification (which files count as
 * eligible tasks) stays in the application layer; this only does the raw
 * listing/reading/writing/archiving underneath it.
 */
public interface HandoffFileStore {

    /** Immediate subdirectories of {@code dir}. */
    List<Path> listDirectories(Path dir);

    /** Immediate regular files in {@code dir}. */
    List<Path> listFiles(Path dir);

    String readString(Path file);

    void writeString(Path file, String content);

    /**
     * Moves {@code source} into an {@code archiveSubdirName} folder inside
     * its own parent directory (creating that folder if needed), replacing
     * any existing file of the same name. Returns the archived file's new
     * path.
     */
    Path archive(Path source, String archiveSubdirName);

    /** A unique, currently-nonexistent directory path suitable for {@code git worktree add}. */
    Path allocateWorktreeDirectory(String branchHint);

    /** Best-effort recursive delete; a missing or already-partially-removed path is not an error. */
    void deleteRecursively(Path root);
}
