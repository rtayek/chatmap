package handoff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import handoff.HandoffWatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import static handoff.HandoffWatcher.HANDOFF_PATTERN;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HandoffWatcher's filename-matching and file-moving behavior.
 *
 * Deliberately does NOT touch the real PROJECTS map (which points at
 * ~/eclipse-workspace/*) -- every test builds its own temp directories
 * and its own project map, so these are safe to run anywhere.
 */
class HandoffWatcherTestCase {

    // ---- Filename pattern matching ----

    @Test
    void matchesSimpleProjectName() {
        Matcher m = HandoffWatcher.HANDOFF_PATTERN.matcher("handoff-dotmdfiles-2026-08-26.md");
        assertTrue(m.matches());
        assertEquals("dotmdfiles", m.group(1));
        assertEquals("2026-08-26", m.group(2));
    }

    @Test
    void matchesHyphenatedProjectName() {
        // Non-greedy project capture must still grab the whole hyphenated
        // name and stop only at the trailing date, not at the first hyphen.
        Matcher m = HandoffWatcher.HANDOFF_PATTERN.matcher("handoff-cjat-manager-2026-08-26.md");
        assertTrue(m.matches());
        assertEquals("cjat-manager", m.group(1));
        assertEquals("2026-08-26", m.group(2));
    }

    @Test
    void rejectsMissingDate() {
        Matcher m = HandoffWatcher.HANDOFF_PATTERN.matcher("handoff-dotmdfiles.md");
        assertFalse(m.matches());
    }

    @Test
    void rejectsWrongExtension() {
        Matcher m = HandoffWatcher.HANDOFF_PATTERN.matcher("handoff-dotmdfiles-2026-08-26.txt");
        assertFalse(m.matches());
    }

    @Test
    void rejectsMissingHandoffPrefix() {
        Matcher m = HandoffWatcher.HANDOFF_PATTERN.matcher("dotmdfiles-2026-08-26.md");
        assertFalse(m.matches());
    }

    @Test
    void rejectsUppercaseProjectName() {
        // PROJECTS keys and filenames are expected lowercase kebab-case;
        // confirms that's enforced rather than silently accepted.
        Matcher m = HandoffWatcher.HANDOFF_PATTERN.matcher("handoff-DotMdFiles-2026-08-26.md");
        assertFalse(m.matches());
    }

    @Test
    void rejectsMalformedDate() {
        Matcher m = HandoffWatcher.HANDOFF_PATTERN.matcher("handoff-dotmdfiles-2026-8-6.md");
        assertFalse(m.matches());
    }

    // ---- File-moving behavior ----

    @Test
    void movesFileToKnownProjectDirectory(@TempDir Path downloads, @TempDir Path projectRoot) throws IOException {
        Path source = downloads.resolve("handoff-dotmdfiles-2026-08-26.md");
        Files.writeString(source, "# test handoff content");

        Map<String, Path> projects = Map.of("dotmdfiles", projectRoot);

        HandoffWatcher.handleNewFile(downloads, downloads.relativize(source), projects);

        Path expectedDest = projectRoot.resolve("handoff-dotmdfiles-2026-08-26.md");
        assertTrue(Files.exists(expectedDest), "file should have been moved to project dir");
        assertFalse(Files.exists(source), "file should no longer exist in downloads dir");
        assertEquals("# test handoff content", Files.readString(expectedDest));
    }

    @Test
    void leavesFileInPlaceWhenProjectUnknown(@TempDir Path downloads) throws IOException {
        Path source = downloads.resolve("handoff-mystery-project-2026-08-26.md");
        Files.writeString(source, "content");

        Map<String, Path> projects = Map.of("dotmdfiles", downloads); // no "mystery-project" entry

        HandoffWatcher.handleNewFile(downloads, downloads.relativize(source), projects);

        assertTrue(Files.exists(source), "unrecognized project should leave file untouched");
    }

    @Test
    void leavesFileInPlaceWhenFilenameDoesNotMatch(@TempDir Path downloads) throws IOException {
        Path source = downloads.resolve("some-other-download.md");
        Files.writeString(source, "content");

        Map<String, Path> projects = Map.of("dotmdfiles", downloads);

        HandoffWatcher.handleNewFile(downloads, downloads.relativize(source), projects);

        assertTrue(Files.exists(source), "non-handoff filename should be left alone");
    }

    @Test
    void leavesFileInPlaceWhenDestinationDirMissing(@TempDir Path downloads) throws IOException {
        Path source = downloads.resolve("handoff-dotmdfiles-2026-08-26.md");
        Files.writeString(source, "content");

        Path missingDir = downloads.resolve("does-not-exist");
        Map<String, Path> projects = Map.of("dotmdfiles", missingDir);

        HandoffWatcher.handleNewFile(downloads, downloads.relativize(source), projects);

        assertTrue(Files.exists(source), "file should stay put if destination dir doesn't exist");
        assertFalse(Files.exists(missingDir), "destination dir should not be created implicitly");
    }

    @Test
    void overwritesExistingFileAtDestination(@TempDir Path downloads, @TempDir Path projectRoot) throws IOException {
        Path source = downloads.resolve("handoff-dotmdfiles-2026-08-26.md");
        Files.writeString(source, "new content");

        Path existingDest = projectRoot.resolve("handoff-dotmdfiles-2026-08-26.md");
        Files.writeString(existingDest, "stale content from an earlier run");

        Map<String, Path> projects = Map.of("dotmdfiles", projectRoot);

        HandoffWatcher.handleNewFile(downloads, downloads.relativize(source), projects);

        assertEquals("new content", Files.readString(existingDest),
                "same-day re-run should overwrite the previous handoff file");
    }
}
