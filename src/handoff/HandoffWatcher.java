package handoff;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches a Downloads folder for handoff markdown files and moves them
 * into the correct project directory, based on the project name embedded
 * in the filename.
 *
 * Expected filename format (kebab-case):
 *   handoff-<project-name>-YYYY-MM-DD.md
 *
 * Example:
 *   handoff-dotmdfiles-2026-08-26.md
 *
 * Project -> directory mappings live in the static PROJECTS map below.
 * Edit that map directly to add/remove/rename projects.
 *
 * Usage:
 *   java HandoffWatcher <downloads-dir>
 *
 * Notes / design choices:
 *   - No external dependencies. Just java.nio.file.WatchService.
 *   - Deterministic: unmatched files or unknown projects are left alone
 *     and logged, never guessed at or silently dropped.
 *   - Single-threaded polling loop; simple to read, simple to kill.
 */
public class HandoffWatcher {

    // Non-greedy project-name capture, fixed date suffix.
    static final Pattern HANDOFF_PATTERN =
            Pattern.compile("^handoff-([a-z0-9-]+?)-(\\d{4}-\\d{2}-\\d{2})\\.md$");

    private static final Map<String, Path> PROJECTS = Map.ofEntries(
            Map.entry("cjatmanager", Path.of(System.getProperty("user.home"), "eclipse-workspace",
                    "cjatmanager")),
            Map.entry("dotmdfiles", Path.of(System.getProperty("user.home"), "eclipse-workspace",
                    "dotmdfiles")),
            Map.entry("dotskills", Path.of(System.getProperty("user.home"), "eclipse-workspace",
                    "dotskills")),
            Map.entry("incoming", Path.of(System.getProperty("user.home"), "eclipse-workspace",
                    "incoming")),
            Map.entry("myclaw", Path.of(System.getProperty("user.home"), "eclipse-workspace", "myclaw")),
            Map.entry("speech", Path.of(System.getProperty("user.home"), "eclipse-workspace", "speech")),
            Map.entry("util", Path.of(System.getProperty("user.home"), "eclipse-workspace", "util")),
            Map.entry("watchais", Path.of(System.getProperty("user.home"), "eclipse-workspace",
                    "watchais")),
            Map.entry("dotfiles", Path.of(System.getProperty("user.home"), "dotfiles"))
    );

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: java HandoffWatcher <downloads-dir>");
            System.exit(1);
        }

        Path watchDir = Paths.get(args[0]);

        if (!Files.isDirectory(watchDir)) {
            System.err.println("Not a directory: " + watchDir);
            System.exit(1);
        }

        System.out.println("Loaded " + PROJECTS.size() + " project mapping(s).");
        System.out.println("Watching " + watchDir + " for handoff-*.md files...");

        WatchService watcher = FileSystems.getDefault().newWatchService();
        watchDir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);

        while (true) {
            WatchKey key = watcher.take(); // blocks until an event arrives

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path fileName = pathEvent.context();
                handleNewFile(watchDir, fileName);
            }

            boolean valid = key.reset();
            if (!valid) {
                System.err.println("Watch key no longer valid, exiting.");
                break;
            }
        }
    }

    private static void handleNewFile(Path watchDir, Path fileName) {
        String name = fileName.toString();
        Matcher m = HANDOFF_PATTERN.matcher(name);

        if (!m.matches()) {
            // Not a handoff file we recognize; leave it for the human.
            return;
        }

        String project = m.group(1);
        String date = m.group(2);
        Path destDir = PROJECTS.get(project);

        if (destDir == null) {
            System.out.println("[" + name + "] unknown project '" + project
                    + "' -- no entry in PROJECTS map, leaving in place.");
            return;
        }

        Path source = watchDir.resolve(fileName);
        Path dest = destDir.resolve(fileName);

        try {
            // Downloads can fire ENTRY_CREATE before the browser finishes
            // writing the file. Small settle delay avoids a partial move.
            Thread.sleep(300);

            if (!Files.isDirectory(destDir)) {
                System.out.println("[" + name + "] destination dir does not exist: " + destDir);
                return;
            }

            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[" + name + "] moved -> " + dest + " (project=" + project + ", date=" + date + ")");
        } catch (IOException | InterruptedException e) {
            System.err.println("[" + name + "] failed to move: " + e.getMessage());
        }
    }

}