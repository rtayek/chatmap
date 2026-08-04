package chatmap.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import chatmap.importer.ImportedChat;

class HttpChatProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** Starts a loopback server on the given port that replies with the given status/body. */
    private HttpServer serve(int port, int status, String body) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        s.createContext("/latest-chat", exchange -> {
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            if (status == 204 || bytes.length == 0) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.getResponseHeaders().set("Content-Type", "text/markdown");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        s.start();
        return s;
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private HttpChatProvider provider(int port, HttpChatProvider.ServerLauncher launcher) {
        return new HttpChatProvider("TestProvider",
                URI.create("http://127.0.0.1:" + port + "/latest-chat"),
                null, HttpClient.newHttpClient(), Duration.ofSeconds(2),
                launcher, Duration.ofSeconds(3), Duration.ofMillis(100));
    }

    @Test
    void fetchesTranscriptWhenEndpointAlreadyUp() throws Exception {
        int port = freePort();
        server = serve(port, 200, "USER: hi\n\nASSISTANT: hello");

        Optional<ImportedChat> chat = provider(port, null).latestChat();

        assertTrue(chat.isPresent());
        assertEquals(2, chat.get().messages().size());
    }

    @Test
    void returnsEmptyOn204() throws Exception {
        int port = freePort();
        server = serve(port, 204, null);

        assertTrue(provider(port, null).latestChat().isEmpty());
    }

    @Test
    void throwsWhenUnreachableAndNoLauncher() throws Exception {
        int deadPort = freePort(); // nothing is listening here
        assertThrows(Exception.class, () -> provider(deadPort, null).latestChat());
    }

    @Test
    void launchesServerThenRetriesUntilItAnswers() throws Exception {
        int port = freePort();
        AtomicInteger launches = new AtomicInteger();
        AtomicReference<HttpServer> launched = new AtomicReference<>();

        HttpChatProvider.ServerLauncher launcher = () -> {
            launches.incrementAndGet();
            try {
                launched.set(serve(port, 200, "USER: launched\n\nASSISTANT: hi there"));
            } catch (Exception e) {
                throw new java.io.IOException(e);
            }
        };

        try {
            Optional<ImportedChat> chat = provider(port, launcher).latestChat();

            assertTrue(chat.isPresent(), "should fetch after launching the server");
            assertEquals(2, chat.get().messages().size());
            assertEquals(1, launches.get(), "server should be launched exactly once");
        } finally {
            if (launched.get() != null) {
                launched.get().stop(0);
            }
        }
    }

    @Test
    void throwsWhenLaunchDoesNotBringServerUp() throws Exception {
        int deadPort = freePort();
        AtomicInteger launches = new AtomicInteger();
        HttpChatProvider.ServerLauncher noOpLauncher = launches::incrementAndGet; // never actually serves

        assertThrows(Exception.class, () -> provider(deadPort, noOpLauncher).latestChat());
        assertEquals(1, launches.get(), "launch should be attempted once");
    }
}
