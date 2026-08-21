package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.domain.Project;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;

class SeedWorkspaceProjectsCliTest {

    private static final List<String> PROJECT_NAMES = List.of(
            "cjatmanager",
            "dotfiles",
            "dotmdfiles",
            "dotskills",
            "incoming",
            "myclaw",
            "speech",
            "util",
            "watchais");

    @AfterEach
    void releaseLogFile() {
        LoggingBootstrap.initializeTemporaryFallback();
    }

    @Test
    void seedsWorkspaceProjectsWithRepositoryPaths(@TempDir Path tempDir) throws Exception {
        Path workspace = createWorkspace(tempDir);
        Path dotfiles = Files.createDirectories(tempDir.resolve("dotfiles"));
        Path home = tempDir.resolve("home");
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);

        List<Project> projects = SeedWorkspaceProjectsCli.execute(new String[] {
                "--home", home.toString(),
                "--workspace", workspace.toString(),
                "--project", "dotfiles=" + dotfiles
        }, clock);

        assertEquals(PROJECT_NAMES, projects.stream().map(Project::name).toList());
        assertEquals(workspace.resolve("cjatmanager").toAbsolutePath().normalize().toString(),
                projects.getFirst().localPath());

        try (Connection conn = new Database("jdbc:sqlite:" + home.resolve("chatmap.db")).openAndInitialize()) {
            ProjectRepository repository = new ProjectRepository(conn);
            assertEquals(PROJECT_NAMES, repository.findAll().stream().map(Project::name).toList());
            assertEquals(dotfiles.toAbsolutePath().normalize().toString(),
                    repository.findByName("dotfiles").orElseThrow().localPath());
            assertEquals(workspace.resolve("watchais").toAbsolutePath().normalize().toString(),
                    repository.findByName("watchais").orElseThrow().localPath());
        }
    }

    @Test
    void seedingTwiceDoesNotDuplicateProjects(@TempDir Path tempDir) throws Exception {
        Path workspace = createWorkspace(tempDir);
        Path dotfiles = Files.createDirectories(tempDir.resolve("dotfiles"));
        Path home = tempDir.resolve("home");
        String[] args = new String[] {
                "--home", home.toString(),
                "--workspace", workspace.toString(),
                "--project", "dotfiles=" + dotfiles
        };

        SeedWorkspaceProjectsCli.execute(args, Clock.systemUTC());
        SeedWorkspaceProjectsCli.execute(args, Clock.systemUTC());

        try (Connection conn = new Database("jdbc:sqlite:" + home.resolve("chatmap.db")).openAndInitialize()) {
            assertEquals(9, new ProjectRepository(conn).findAll().size());
        }
    }

    @Test
    void rejectsMissingWorkspaceProjectDirectory(@TempDir Path tempDir) {
        Path home = tempDir.resolve("home");
        Path workspace = tempDir.resolve("workspace");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> SeedWorkspaceProjectsCli.execute(new String[] {
                        "--home", home.toString(),
                        "--workspace", workspace.toString()
                }, Clock.systemUTC()));

        assertEquals("Project directory does not exist: cjatmanager="
                + workspace.resolve("cjatmanager").toAbsolutePath().normalize(), thrown.getMessage());
    }

    @Test
    void allowsAnySeedProjectPathToBeOverridden(@TempDir Path tempDir) throws Exception {
        Path workspace = createWorkspace(tempDir);
        Path dotfiles = Files.createDirectories(tempDir.resolve("dotfiles"));
        Path utilOverride = Files.createDirectories(tempDir.resolve("elsewhere").resolve("util"));
        Path home = tempDir.resolve("home");

        SeedWorkspaceProjectsCli.execute(new String[] {
                "--home", home.toString(),
                "--workspace", workspace.toString(),
                "--project", "dotfiles=" + dotfiles,
                "--project", "util=" + utilOverride
        }, Clock.systemUTC());

        try (Connection conn = new Database("jdbc:sqlite:" + home.resolve("chatmap.db")).openAndInitialize()) {
            ProjectRepository repository = new ProjectRepository(conn);
            assertEquals(utilOverride.toAbsolutePath().normalize().toString(),
                    repository.findByName("util").orElseThrow().localPath());
        }
    }

    private static Path createWorkspace(Path tempDir) throws Exception {
        Path workspace = tempDir.resolve("workspace");
        for (String name : PROJECT_NAMES) {
            Files.createDirectories(workspace.resolve(name));
        }
        return workspace;
    }
}
