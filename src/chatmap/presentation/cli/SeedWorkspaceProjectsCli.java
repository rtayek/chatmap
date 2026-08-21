package chatmap.presentation.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.domain.Project;

/** Adds Ray's known local projects to ChatMap's project registry. */
public final class SeedWorkspaceProjectsCli {

    private static final String USAGE = "Usage: seedWorkspaceProjects [--home <directory>] "
            + "[--workspace <directory>] [--project <name>=<directory>]...";
    private static final String DESCRIPTION = "Seeded from local project list";
    private static final List<String> WORKSPACE_PROJECT_NAMES = List.of(
            "cjatmanager",
            "dotmdfiles",
            "dotskills",
            "incoming",
            "myclaw",
            "speech",
            "util",
            "watchais");

    private SeedWorkspaceProjectsCli() {
    }

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, USAGE);
        ProjectSeedSet seedSet;
        try {
            seedSet = parseSeedSet(parsedArguments);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            CliBootstrap.exitWithUsage(USAGE);
            return;
        }

        try {
            List<Project> projects = execute(parsedArguments, seedSet, Clock.systemUTC());
            for (Project project : projects) {
                System.out.println(project.id() + " " + project.name() + " " + project.repositoryPath());
            }
        } catch (Exception e) {
            System.err.println("Could not seed projects: " + e.getMessage());
            System.exit(1);
        }
    }

    public static List<Project> execute(String[] args, Clock clock) throws Exception {
        ParsedArguments parsedArguments = CliBootstrap.parse(args);
        return execute(parsedArguments, parseSeedSet(parsedArguments), clock);
    }

    static List<Project> execute(ParsedArguments parsedArguments, Path workspace, Clock clock) throws Exception {
        return execute(parsedArguments, new ProjectSeedSet(defaultSeedPaths(workspace)), clock);
    }

    static List<Project> execute(ParsedArguments parsedArguments, ProjectSeedSet seedSet, Clock clock)
            throws Exception {
        List<ProjectSeed> seeds = seeds(seedSet.projectPaths());
        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            String timestamp = clock.instant().toString();
            List<Project> projects = new ArrayList<>();
            for (ProjectSeed seed : seeds) {
                projects.add(context.services().projectService().findOrCreate(
                        seed.name(), DESCRIPTION, timestamp, seed.repositoryPath().toString()));
            }
            return List.copyOf(projects);
        }
    }

    private static List<ProjectSeed> seeds(Map<String, Path> projectPaths) {
        List<ProjectSeed> seeds = new ArrayList<>();
        for (Map.Entry<String, Path> projectPath : projectPaths.entrySet()) {
            Path repositoryPath = projectPath.getValue().toAbsolutePath().normalize();
            if (!Files.isDirectory(repositoryPath)) {
                throw new IllegalArgumentException("Project directory does not exist: "
                        + projectPath.getKey() + "=" + repositoryPath);
            }
            seeds.add(new ProjectSeed(projectPath.getKey(), repositoryPath));
        }
        seeds.sort(Comparator.comparing(ProjectSeed::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(seeds);
    }

    private static ProjectSeedSet parseSeedSet(ParsedArguments parsedArguments) {
        List<String> remaining = parsedArguments.remainingArgs();
        Path workspace = defaultWorkspace();
        Map<String, Path> projectOverrides = new LinkedHashMap<>();
        int index = 0;
        while (index < remaining.size()) {
            String option = remaining.get(index);
            String value = readOptionValue(remaining, index, option);
            if ("--workspace".equals(option)) {
                workspace = Path.of(value);
            } else if ("--project".equals(option)) {
                ProjectSeed seed = parseProjectSeed(value);
                projectOverrides.put(seed.name(), seed.repositoryPath());
            } else {
                throw new IllegalArgumentException("Unknown option: " + option);
            }
            index += 2;
        }
        Map<String, Path> projectPaths = defaultSeedPaths(workspace);
        projectPaths.putAll(projectOverrides);
        return new ProjectSeedSet(projectPaths);
    }

    private static ProjectSeed parseProjectSeed(String value) {
        int separator = value.indexOf('=');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Expected --project value in the form <name>=<directory>.");
        }
        String name = value.substring(0, separator).trim();
        String path = value.substring(separator + 1).trim();
        if (name.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("Expected --project value in the form <name>=<directory>.");
        }
        return new ProjectSeed(name, Path.of(path));
    }

    private static Map<String, Path> defaultSeedPaths(Path workspace) {
        Path workspaceRoot = workspace.toAbsolutePath().normalize();
        Map<String, Path> projectPaths = new LinkedHashMap<>();
        for (String name : WORKSPACE_PROJECT_NAMES) {
            projectPaths.put(name, workspaceRoot.resolve(name));
        }
        projectPaths.put("dotfiles", defaultDotfiles());
        return projectPaths;
    }

    private static String readOptionValue(List<String> args, int index, String option) {
        int valueIndex = index + 1;
        if (valueIndex >= args.size() || args.get(valueIndex).isBlank()) {
            throw new IllegalArgumentException("Expected value after " + option + ".");
        }
        return args.get(valueIndex);
    }

    private static Path defaultWorkspace() {
        return Path.of(System.getProperty("user.home"), "eclipse-workspace");
    }

    private static Path defaultDotfiles() {
        return Path.of(System.getProperty("user.home"), "dotfiles");
    }

    record ProjectSeedSet(Map<String, Path> projectPaths) {
        ProjectSeedSet {
            projectPaths = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(projectPaths));
        }
    }

    private record ProjectSeed(String name, Path repositoryPath) {
    }
}
