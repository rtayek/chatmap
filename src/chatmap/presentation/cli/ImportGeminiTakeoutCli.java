package chatmap.presentation.cli;

import java.io.IOException;
import java.nio.file.Path;

import chatmap.app.bootstrap.ChatMapPaths;
import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;

/** Imports an extracted Gemini Workspace Takeout directory into a selected ChatMap home. */
public final class ImportGeminiTakeoutCli {
    private ImportGeminiTakeoutCli() {
    }

    public static void main(String[] args) {
        ParsedArguments parsed = CliBootstrap.parseOrExit(args, usage);
        if (parsed.remainingArgs().size() != 1) {
            CliBootstrap.exitWithUsage(usage);
            return;
        }
        try {
            run(parsed);
        } catch (Exception e) {
            System.err.println("Could not import Gemini Takeout: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(ParsedArguments parsed) throws Exception {
        Path directory = Path.of(parsed.remainingArgs().getFirst());
        System.out.println(ChatMapPaths.diagnostics(parsed.paths()));
        try (CliBootstrap.CliContext context = CliBootstrap.open(parsed)) {
            var result = context.services().geminiTakeoutImportService().importDirectory(directory);
            System.out.println(result.summary());
            for (String failure : result.failures()) {
                System.err.println(failure);
            }
            if (!result.failures().isEmpty()) {
                throw new IOException("Failed to persist " + result.failures().size() + " Gemini conversation(s)");
            }
        }
    }

    private static final String usage =
            "Usage: importGeminiTakeout [--home <directory>] <extracted-Takeout-directory>";
}
