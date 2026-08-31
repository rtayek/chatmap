package handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandoffWatcherTestCase {
    @Test
    void recognizesHandoffMarkdownFilesWithoutRequiringOneNamingConvention() {
        assertTrue(HandoffWatcher.isHandoff(Path.of("handoff-dotmdfiles-2026-08-26.md")));
        assertTrue(HandoffWatcher.isHandoff(Path.of("ChatMap Worker Handoff.MD")));
        assertTrue(HandoffWatcher.isHandoff(Path.of("worker-handoff-notes.md")));
    }

    @Test
    void rejectsNonHandoffFiles() {
        assertFalse(HandoffWatcher.isHandoff(Path.of("notes.md")));
        assertFalse(HandoffWatcher.isHandoff(Path.of("handoff.txt")));
    }

    @Test
    void collectsHandoffsFromTwoSources(@TempDir Path root) throws IOException {
        Path downloads = Files.createDirectory(root.resolve("downloads"));
        Path gitIncoming = Files.createDirectory(root.resolve("git-incoming"));
        Path inbox = root.resolve("inbox");
        Files.writeString(downloads.resolve("download-handoff.md"), "download");
        Files.writeString(gitIncoming.resolve("git-handoff.md"), "git");

        int collected = HandoffWatcher.collectExisting(List.of(downloads, gitIncoming), inbox);

        assertEquals(2, collected);
        assertEquals("download", Files.readString(inbox.resolve("download-handoff.md")));
        assertEquals("git", Files.readString(inbox.resolve("git-handoff.md")));
        assertFalse(Files.exists(downloads.resolve("download-handoff.md")));
        assertFalse(Files.exists(gitIncoming.resolve("git-handoff.md")));
    }

    @Test
    void leavesUnrelatedFilesAtTheirSource(@TempDir Path root) throws IOException {
        Path source = Files.createDirectory(root.resolve("source"));
        Path inbox = root.resolve("inbox");
        Path unrelated = Files.writeString(source.resolve("notes.md"), "notes");

        int collected = HandoffWatcher.collectExisting(List.of(source), inbox);

        assertEquals(0, collected);
        assertTrue(Files.exists(unrelated));
    }

    @Test
    void preservesBothFilesWhenNamesCollide(@TempDir Path root) throws IOException {
        Path source = Files.createDirectory(root.resolve("source"));
        Path inbox = Files.createDirectory(root.resolve("inbox"));
        Files.writeString(inbox.resolve("worker-handoff.md"), "first");
        Files.writeString(source.resolve("worker-handoff.md"), "second");

        assertTrue(HandoffWatcher.collect(source.resolve("worker-handoff.md"), inbox));

        assertEquals("first", Files.readString(inbox.resolve("worker-handoff.md")));
        assertEquals("second", Files.readString(inbox.resolve("worker-handoff-1.md")));
    }

    @Test
    void allocatesNextAvailableCollisionName(@TempDir Path inbox) throws IOException {
        Files.writeString(inbox.resolve("handoff.md"), "zero");
        Files.writeString(inbox.resolve("handoff-1.md"), "one");

        assertEquals(inbox.resolve("handoff-2.md"),
                HandoffWatcher.availableDestination(inbox, Path.of("handoff.md")));
    }

    @Test
    void missingSourceIsRetryableAndDoesNotCreateOutput(@TempDir Path root) {
        Path inbox = root.resolve("inbox");

        assertFalse(HandoffWatcher.collect(root.resolve("missing-handoff.md"), inbox));
        assertFalse(Files.exists(inbox));
    }

    @Test
    void continuousCollectionWaitsForAStableFile(@TempDir Path root) throws IOException {
        Path source = Files.createDirectory(root.resolve("source"));
        Path inbox = root.resolve("inbox");
        Path handoff = Files.writeString(source.resolve("worker-handoff.md"), "partial");
        Map<Path, HandoffWatcher.FileStamp> observations = new HashMap<>();

        assertEquals(0, HandoffWatcher.collectStableExisting(List.of(source), inbox, observations));
        assertTrue(Files.exists(handoff));

        Files.writeString(handoff, "complete");
        assertEquals(0, HandoffWatcher.collectStableExisting(List.of(source), inbox, observations));
        assertTrue(Files.exists(handoff));

        assertEquals(1, HandoffWatcher.collectStableExisting(List.of(source), inbox, observations));
        assertEquals("complete", Files.readString(inbox.resolve("worker-handoff.md")));
    }

    @Test
    void parsesMultipleSourcesAndOnceMode(@TempDir Path root) {
        Path inbox = root.resolve("inbox");
        Path first = root.resolve("first");
        Path second = root.resolve("second");

        HandoffWatcher.Configuration configuration = HandoffWatcher.Configuration.parse(new String[] {
                "--inbox", inbox.toString(),
                "--source", first.toString(),
                "--source", second.toString(),
                "--once"
        });

        assertEquals(inbox.toAbsolutePath().normalize(), configuration.inbox());
        assertEquals(List.of(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize()),
                configuration.sources());
        assertTrue(configuration.once());
    }
}
