package chatmap.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Source-level dependency checks that require no architecture-test dependency. */
class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src");

    @Test
    void domainHasNoDependenciesOnOtherChatMapPackages() throws IOException {
        List<SourceFile> domainSources = sourcesIn("chatmap.domain");

        assertFalse(domainSources.isEmpty(), "No chatmap.domain sources were found");
        for (SourceFile source : domainSources) {
            assertFalse(source.text().contains("import chatmap."),
                    () -> source.path() + " makes the domain depend on another ChatMap package");
        }
    }

    @Test
    void persistenceDoesNotDependOnApplicationOrPresentation() throws IOException {
        List<SourceFile> persistenceSources = sourcesIn("chatmap.infrastructure.persistence.sqlite");

        assertFalse(persistenceSources.isEmpty(), "No chatmap.infrastructure.persistence.sqlite sources were found");
        for (SourceFile source : persistenceSources) {
            assertFalse(source.text().contains("import chatmap.application.service."),
                    () -> source.path() + " makes persistence depend on application services");
            assertFalse(source.text().contains("import chatmap.presentation.ui."),
                    () -> source.path() + " makes persistence depend on presentation");
            assertFalse(source.text().contains("import chatmap.presentation.cli."),
                    () -> source.path() + " makes persistence depend on CLI presentation");
            assertFalse(source.text().contains("import chatmap.app."),
                    () -> source.path() + " makes persistence depend on the composition root");
        }
    }

    @Test
    void applicationDoesNotDependOnInfrastructureOrPresentation() throws IOException {
        List<SourceFile> applicationSources = sourcesIn("chatmap.application");

        assertFalse(applicationSources.isEmpty(), "No chatmap.application sources were found");
        for (SourceFile source : applicationSources) {
            assertFalse(source.text().contains("import chatmap.infrastructure."),
                    () -> source.path() + " makes application depend on infrastructure");
            assertFalse(source.text().contains("import chatmap.presentation."),
                    () -> source.path() + " makes application depend on presentation");
        }
    }

    @Test
    void javafxIsConfinedToUiAndCompositionPackages() throws IOException {
        for (SourceFile source : allSources()) {
            if (!source.text().contains("import javafx.")) {
                continue;
            }
            assertTrue(source.packageName().startsWith("chatmap.presentation.ui")
                            || source.packageName().startsWith("chatmap.app"),
                    () -> source.path() + " introduces JavaFX outside presentation or composition");
        }
    }

    @Test
    void presentationDoesNotConstructInfrastructureAdapters() throws IOException {
        List<SourceFile> presentationSources = sourcesIn("chatmap.presentation");

        assertFalse(presentationSources.isEmpty(), "No chatmap.presentation sources were found");
        for (SourceFile source : presentationSources) {
            assertFalse(source.text().contains("import chatmap.infrastructure."),
                    () -> source.path() + " bypasses the application or composition boundary");
        }
    }

    @Test
    void cliExecutionDoesNotRestoreLegacyStandardBackend() {
        assertFalse(Files.exists(SOURCE_ROOT.resolve("chatmap/infrastructure/ai/StandardCliBackend.java")),
                "StandardCliBackend is a removed legacy AiBackend path; use provider-specific CliAiProvider subclasses");
    }

    @Test
    void aiResponseCannotDefaultToFalseClaudeMetadata() throws IOException {
        Path source = SOURCE_ROOT.resolve("chatmap/application/port/ai/AiResponse.java");
        String text = Files.readString(source);

        assertFalse(text.contains("ProviderId.claudeCli, backendId.value(), \"default\""),
                "AiResponse must not silently label responses as Claude/default");
    }

    private static List<SourceFile> sourcesIn(String packagePrefix) throws IOException {
        return allSources().stream()
                .filter(source -> source.packageName().equals(packagePrefix)
                        || source.packageName().startsWith(packagePrefix + "."))
                .toList();
    }

    private static List<SourceFile> allSources() throws IOException {
        try (var paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(ArchitectureBoundaryTest::read)
                    .toList();
        }
    }

    private static SourceFile read(Path path) {
        try {
            String text = Files.readString(path);
            String packageName = text.lines()
                    .filter(line -> line.startsWith("package "))
                    .map(line -> line.substring("package ".length(), line.indexOf(';')).trim())
                    .findFirst()
                    .orElse("");
            return new SourceFile(path, packageName, text);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read " + path, failure);
        }
    }

    private record SourceFile(Path path, String packageName, String text) {
    }
}
