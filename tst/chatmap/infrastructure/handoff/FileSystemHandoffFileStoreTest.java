package chatmap.infrastructure.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.handoff.HandoffFileStoreException;

class FileSystemHandoffFileStoreTest {

    private final FileSystemHandoffFileStore store = new FileSystemHandoffFileStore();

    @TempDir
    Path tempDir;

    @Test
    void listDirectoriesReturnsOnlyImmediateSubdirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("a"));
        Files.createDirectories(tempDir.resolve("b"));
        Files.writeString(tempDir.resolve("file.txt"), "not a directory");

        List<Path> found = store.listDirectories(tempDir);

        assertEquals(2, found.size());
        assertTrue(found.contains(tempDir.resolve("a")));
        assertTrue(found.contains(tempDir.resolve("b")));
    }

    @Test
    void listFilesReturnsOnlyImmediateRegularFiles() throws IOException {
        Files.createDirectories(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("a.md"), "content");

        List<Path> found = store.listFiles(tempDir);

        assertEquals(List.of(tempDir.resolve("a.md")), found);
    }

    @Test
    void writeStringThenReadStringRoundTrips() {
        Path file = tempDir.resolve("note.txt");

        store.writeString(file, "hello world");

        assertEquals("hello world", store.readString(file));
    }

    @Test
    void readStringThrowsHandoffFileStoreExceptionWhenFileIsMissing() {
        assertThrows(HandoffFileStoreException.class, () -> store.readString(tempDir.resolve("missing.txt")));
    }

    @Test
    void archiveMovesFileIntoNamedSubdirOfItsOwnParent() throws IOException {
        Path file = tempDir.resolve("task1.md");
        Files.writeString(file, "task body");

        Path archived = store.archive(file, ".archive");

        assertEquals(tempDir.resolve(".archive").resolve("task1.md"), archived);
        assertFalse(Files.exists(file));
        assertEquals("task body", Files.readString(archived));
    }

    @Test
    void archiveReplacesAnExistingFileOfTheSameName() throws IOException {
        Path file = tempDir.resolve("task1.md");
        Files.writeString(file, "new content");
        Files.createDirectories(tempDir.resolve(".archive"));
        Files.writeString(tempDir.resolve(".archive/task1.md"), "old content");

        Path archived = store.archive(file, ".archive");

        assertEquals("new content", Files.readString(archived));
    }

    @Test
    void allocateWorktreeDirectoryReturnsAUniqueNonexistentPath() {
        Path allocated = store.allocateWorktreeDirectory("feature/my-branch");

        assertFalse(Files.exists(allocated), "the path must not already exist, since git worktree add requires that");
        assertTrue(allocated.getFileName().toString().contains("my-branch"));
    }

    @Test
    void deleteRecursivelyRemovesADirectoryTree() throws IOException {
        Path dir = tempDir.resolve("tree");
        Files.createDirectories(dir.resolve("nested"));
        Files.writeString(dir.resolve("nested/file.txt"), "content");

        store.deleteRecursively(dir);

        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteRecursivelyOnAMissingPathIsANoOp() {
        store.deleteRecursively(tempDir.resolve("never-existed"));
    }
}
