package handoff;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HandoffWatcher {
    private static final long RESCAN_MILLIS = 2_000;

    private HandoffWatcher() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Configuration configuration;
        try {
            configuration = Configuration.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }

        Files.createDirectories(configuration.inbox());
        validateSources(configuration.sources());

        if (configuration.once()) {
            int collected = collectExisting(configuration.sources(), configuration.inbox());
            System.out.println("Collected " + collected + " handoff file(s).");
            return;
        }

        watch(configuration.sources(), configuration.inbox());
    }

    static boolean isHandoff(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        return name.contains("handoff") && name.endsWith(".md");
    }
    
    static int collectExisting(List<Path> sources, Path inbox) {
        int collected = 0;
        for (Path source : sources) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(source)) {
                for (Path file : files) {
                    if (Files.isRegularFile(file) && isHandoff(file) && collect(file, inbox)) {
                        collected++;
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to scan " + source + ": " + e.getMessage());
            }
        }
        return collected;
    }

    static boolean collect(Path source, Path inbox) {
        if (!Files.isRegularFile(source) || !isHandoff(source)) {
            return false;
        }

        try {
            Files.createDirectories(inbox);
            Path destination = availableDestination(inbox, source.getFileName());
            Files.move(source, destination);
            System.out.println("Collected " + source + " -> " + destination);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to collect " + source + ": " + e.getMessage());
            return false;
        }
    }

    static Path availableDestination(Path inbox, Path fileName) {
        Path candidate = inbox.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String name = fileName.toString();
        int extension = name.toLowerCase(Locale.ROOT).lastIndexOf(".md");
        String stem = name.substring(0, extension);
        String suffix = name.substring(extension);

        for (int number = 1; ; number++) {
            candidate = inbox.resolve(stem + "-" + number + suffix);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    static int collectStableExisting(List<Path> sources, Path inbox, Map<Path, FileStamp> observations) {
        int collected = 0;
        for (Path source : sources) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(source)) {
                for (Path file : files) {
                    if (!Files.isRegularFile(file) || !isHandoff(file)) {
                        continue;
                    }

                    Path normalized = file.toAbsolutePath().normalize();
                    FileStamp current = FileStamp.read(file);
                    FileStamp previous = observations.put(normalized, current);
                    if (current.equals(previous) && collect(file, inbox)) {
                        observations.remove(normalized);
                        collected++;
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to scan " + source + ": " + e.getMessage());
            }
        }
        observations.keySet().removeIf(path -> !Files.exists(path));
        return collected;
    }

    private static void watch(List<Path> sources, Path inbox) throws InterruptedException {
        Map<Path, FileStamp> observations = new HashMap<>();
        System.out.println("Watching " + sources.size() + " source folder(s).");
        System.out.println("Collecting handoffs into " + inbox.toAbsolutePath().normalize());

        while (true) {
            collectStableExisting(sources, inbox, observations);
            Thread.sleep(RESCAN_MILLIS);
        }
    }

    private static void validateSources(List<Path> sources) {
        for (Path source : sources) {
            if (!Files.isDirectory(source)) {
                throw new IllegalArgumentException("Source is not a directory: " + source);
            }
        }
    }

    private static void printUsage() {
        System.err.println("Usage: HandoffWatcher --inbox <directory> --source <directory> "
                + "[--source <directory> ...] [--once]");
    }

    record Configuration(Path inbox, List<Path> sources, boolean once) {
        Configuration {
            sources = List.copyOf(sources);
        }

        static Configuration parse(String[] args) {
            Path inbox = null;
            List<Path> sources = new ArrayList<>();
            boolean once = false;

            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--inbox" -> inbox = Path.of(requireValue(args, ++index, "--inbox"));
                    case "--source" -> sources.add(Path.of(requireValue(args, ++index, "--source")));
                    case "--once" -> once = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[index]);
                }
            }

            if (inbox == null) {
                throw new IllegalArgumentException("Missing required --inbox directory.");
            }
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("At least one --source directory is required.");
            }
            return new Configuration(inbox.toAbsolutePath().normalize(), normalize(sources), once);
        }

        private static List<Path> normalize(List<Path> paths) {
            return paths.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option + ".");
            }
            return args[index];
        }
    }

    record FileStamp(long size, long modifiedMillis) {
        static FileStamp read(Path file) throws IOException {
            return new FileStamp(Files.size(file), Files.getLastModifiedTime(file).toMillis());
        }
    }
}
