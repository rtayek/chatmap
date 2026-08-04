package chatmap.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatConsolidatorCliTest {

    @Test
    void consolidatorProcessesProjectFiles(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("sampleProject");
        Files.createDirectories(projectDir);

        Path chatFile = projectDir.resolve("sample-chat.md");
        Files.writeString(chatFile, """
                # Sample Project Discussion
                
                User: How do we structure the consolidation module?
                
                Assistant: We build a clean Java CLI tool within ChatMap that scans workspace folders.
                """);

        Path outputDir = tempDir.resolve("output");

        // Run CLI on temporary workspace
        ChatConsolidatorCli.main(new String[]{tempDir.toString(), outputDir.toString()});

        Path consolidatedFile = outputDir.resolve("sampleProject_CONSOLIDATED.md");
        assertTrue(Files.exists(consolidatedFile), "Consolidated Markdown file should be generated");

        String content = Files.readString(consolidatedFile);
        assertTrue(content.contains("Consolidated Project Memory: sampleProject"));
        assertTrue(content.contains("Sample Project Discussion"));
        assertTrue(content.contains("How do we structure the consolidation module?"));
    }
}
