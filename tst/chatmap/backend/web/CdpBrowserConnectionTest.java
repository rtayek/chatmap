package chatmap.backend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives CdpBrowserConnection against a local HTTP server standing in for
 * Chrome's CDP endpoint, with a fake page factory so no real WebSocket is
 * needed. Verifies the tab-leak fix: a tab this connection creates is closed
 * when its page closes; a tab the user already had open is left alone.
 */
class CdpBrowserConnectionTest {

    private HttpServer server;
    private final List<String> requestedPaths = new ArrayList<>();
    private String listResponse = "[]";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            String body = respond(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String respond(String path) {
        if (path.equals("/json/list")) {
            return listResponse;
        }
        if (path.equals("/json/new")) {
            return "{\"id\":\"tab42\",\"url\":\"https://example.test/chat\","
                    + "\"webSocketDebuggerUrl\":\"ws://ignored\"}";
        }
        return "";
    }

    private CdpBrowserConnection connection() {
        String cdpUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new CdpBrowserConnection(cdpUrl, HttpClient.newHttpClient(),
                (webSocketUrl, onClose) -> new CdpPage(new NullTransport(), onClose));
    }

    @Test
    void closingAPageWeCreatedClosesItsTab() throws Exception {
        listResponse = "[]";

        CdpPage page = connection().openPage("https://example.test/chat").orElseThrow();
        page.close();

        assertTrue(requestedPaths.contains("/json/close/tab42"),
                "expected /json/close/tab42 in " + requestedPaths);
    }

    @Test
    void closingAPageTheUserAlreadyHadOpenLeavesItsTabAlone() throws Exception {
        listResponse = "[{\"id\":\"user1\",\"type\":\"page\",\"url\":\"https://example.test/chat\","
                + "\"webSocketDebuggerUrl\":\"ws://ignored\"}]";

        CdpPage page = connection().openPage("https://example.test/chat").orElseThrow();
        page.close();

        assertEquals(List.of(), requestedPaths.stream()
                .filter(path -> path.startsWith("/json/close")).toList());
    }

    /** Answers every CDP command with an empty success so navigate/bringToFront pass. */
    private static final class NullTransport implements CdpTransport {
        @Override
        public JsonObject send(String method, Map<String, ?> params) {
            return CdpPage.successfulValue("complete");
        }

        @Override
        public void close() {
        }
    }
}
