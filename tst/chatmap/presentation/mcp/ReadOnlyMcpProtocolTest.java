package chatmap.presentation.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

class ReadOnlyMcpProtocolTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void initializesListsOnlyReadToolsCallsEachAndExitsOnEof(@TempDir Path directory) throws Exception {
        McpSyntheticFixture.Fixture fixture = McpSyntheticFixture.create(directory);
        ServerParameters parameters = ServerParameters.builder(javaCommand())
                .args("-cp", System.getProperty("java.class.path"), ReadOnlyMcpServerCli.class.getName(),
                        "--home", fixture.home().toString(),
                        "--project-id", Long.toString(McpSyntheticFixture.selectedProjectId),
                        "--project-root", fixture.projectRoot().toString())
                .build();
        StdioClientTransport transport = new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
        List<String> standardError = new ArrayList<>();
        transport.setStdErrorHandler(standardError::add);
        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();
            var tools = client.listTools().tools();

            assertEquals(List.of("readChatMessages", "readWorkingContext", "searchChats"), tools.stream()
                    .map(tool -> tool.name()).sorted().toList());
            assertTrue(tools.stream().allMatch(tool -> Boolean.TRUE.equals(tool.annotations().readOnlyHint())));
            assertTrue(tools.stream().allMatch(tool -> Boolean.FALSE.equals(tool.annotations().destructiveHint())));

            var search = client.callTool(new CallToolRequest("searchChats", Map.of("query", "SQLite")));
            var messages = client.callTool(new CallToolRequest(
                    "readChatMessages", Map.of("chatId", McpSyntheticFixture.historyChatId)));
            var context = client.callTool(new CallToolRequest("readWorkingContext", Map.of()));

            assertFalse(Boolean.TRUE.equals(search.isError()));
            assertFalse(Boolean.TRUE.equals(messages.isError()));
            assertFalse(Boolean.TRUE.equals(context.isError()));
            assertEquals("ok", resultMap(search.structuredContent()).get("outcome"));
            assertEquals("ok", resultMap(messages.structuredContent()).get("outcome"));
            assertEquals("ok", resultMap(context.structuredContent()).get("outcome"));
        }
        transport.awaitForExit();

        assertTrue(standardError.stream().noneMatch(line -> line.contains("Could not start")),
                () -> String.join(System.lineSeparator(), standardError));
    }

    @Test
    void cliRequiresExplicitHomeAndProject() {
        assertThrows(IllegalArgumentException.class, () -> ReadOnlyMcpServerCli.parse(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ReadOnlyMcpServerCli.parse(List.of("--home", "somewhere")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
