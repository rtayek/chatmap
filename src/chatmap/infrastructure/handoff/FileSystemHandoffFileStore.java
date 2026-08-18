package chatmap.infrastructure.handoff;

import org.slf4j.Logger;
import chatmap.application.support.Log;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.application.port.handoff.HandoffFileStoreException;

/** Real {@code java.nio.file}-backed implementation of {@link HandoffFileStore}. */
public final class FileSystemHandoffFileStore implements HandoffFileStore {
    private static final Logger LOG = Log.of(FileSystemHandoffFileStore.class);


    @Override
    public List<Path> listDirectories(Path dir) {
        try (var entries = Files.list(dir)) {
            return entries.filter(Files::isDirectory).toList();
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not list directories in " + dir, failure);
        }
    }

    @Override
    public List<Path> listFiles(Path dir) {
        try (var entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile).toList();
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not list files in " + dir, failure);
        }
    }

    @Override
    public String readString(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return readAll(reader);
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not read " + file, failure);
        }
    }

    @Override
    public void writeString(Path file, String content) {
        try (Writer writer = Files.newBufferedWriter(file)) {
            writeAll(writer, content);
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not write " + file, failure);
        }
    }

    /**
     * Reads {@code reader} to exhaustion. Package-visible and file-agnostic so the
     * char-copy logic itself can be unit-tested directly against a {@code StringReader},
     * without touching disk.
     */
    static String readAll(Reader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[8192];
        int charsRead;
        while ((charsRead = reader.read(buffer)) != -1) {
            text.append(buffer, 0, charsRead);
        }
        return text.toString();
    }

    /**
     * Writes {@code content} to {@code writer}. Package-visible and file-agnostic so it
     * can be unit-tested directly against a {@code StringWriter}, without touching disk.
     */
    static void writeAll(Writer writer, String content) throws IOException {
        writer.write(content);
    }

    @Override
    public Path archive(Path source, String archiveSubdirName) {
        Path parent = source.getParent();
        Path fileName = source.getFileName();
        if (parent == null || fileName == null) {
            throw new HandoffFileStoreException(
                    "File has no parent directory or file name to archive: " + source,
                    new IOException("not archivable: " + source));
        }
        try {
            Path archiveDir = parent.resolve(archiveSubdirName);
            Files.createDirectories(archiveDir);
            Path destination = archiveDir.resolve(fileName);
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not archive " + source, failure);
        }
    }

    @Override
    public Path allocateWorktreeDirectory(String branchHint) {
        try {
            Path placeholder = Files.createTempDirectory("chatmap-handoff-" + sanitize(branchHint) + "-");
            // git worktree add requires the target path not already exist.
            Files.delete(placeholder);
            return placeholder;
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not allocate a worktree path", failure);
        }
    }

    private static String sanitize(String branch) {
        return branch.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    @Override
    public void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            LOG.debug("Silenced exception: {}", ignored.getMessage(), ignored);
        }
    }
}
