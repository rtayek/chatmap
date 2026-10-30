package chatmap.presentation.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;

/** Creates only synthetic MCP demonstration data under an explicit output path. */
public final class SyntheticMcpFixtureCli {

    private SyntheticMcpFixtureCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: SyntheticMcpFixtureCli <new-output-directory>");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Fixture output already exists: " + output);
        }
        Files.createDirectories(output);
        McpSyntheticFixture.Fixture fixture = McpSyntheticFixture.create(output);
        Path checklist = output.resolve("expected-checklist.json");
        Path manifest = output.resolve("fixture-manifest.json");
        Files.writeString(checklist, gson.toJson(checklist()));
        Files.writeString(manifest, gson.toJson(manifest(fixture, checklist)));
        System.out.println("fixtureHome=" + fixture.home());
        System.out.println("projectRoot=" + fixture.projectRoot());
        System.out.println("projectId=" + McpSyntheticFixture.selectedProjectId);
        System.out.println("manifest=" + manifest);
        System.out.println("checklist=" + checklist);
    }

    private static Map<String, Object> checklist() {
        Map<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("currentTask", "Expose read-only memory access through three bounded MCP tools.");
        checklist.put("storageDecision", "Keep SQLite for this bounded prototype.");
        checklist.put("deferred", "Semantic extraction.");
        checklist.put("requiredMessageIds", List.of(
                McpSyntheticFixture.decisionMessageId,
                McpSyntheticFixture.currentTaskMessageId,
                McpSyntheticFixture.deferredMessageId));
        checklist.put("requiredDocument", ".llm/working-context.md");
        return checklist;
    }

    private static Map<String, Object> manifest(McpSyntheticFixture.Fixture fixture, Path checklist)
            throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("fixtureType", "isolated synthetic data only");
        manifest.put("home", fixture.home().toString());
        manifest.put("database", fixture.database().toString());
        manifest.put("databaseSha256", sha256(fixture.database()));
        manifest.put("projectRoot", fixture.projectRoot().toString());
        manifest.put("workingContextSha256", sha256(fixture.workingContext()));
        manifest.put("checklistSha256", sha256(checklist));
        manifest.put("projectId", McpSyntheticFixture.selectedProjectId);
        return manifest;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static final com.google.gson.Gson gson = new GsonBuilder().setPrettyPrinting().create();
}
