package chatmap.infrastructure.provider.web;


import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChromeCdpLauncherTest {

    private static PrintStream discard() {
        return new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8);
    }

    /** A loopback server that answers /json/version, standing in for a running Chrome's CDP. */
    private static HttpServer fakeCdp() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/json/version", exchange -> {
            byte[] body = "{\"Browser\":\"Chrome/fake\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void probeUrlAppendsJsonVersion() {
        assertEquals("http://127.0.0.1:9222/json/version",
                ChromeCdpLauncher.versionProbeUrl("http://127.0.0.1:9222"));
        assertEquals("http://127.0.0.1:9222/json/version",
                ChromeCdpLauncher.versionProbeUrl("http://127.0.0.1:9222/"));
    }

    @Test
    void portOfParsesCdpUrlWithDefault() {
        assertEquals(9222, ChromeCdpLauncher.portOf("http://127.0.0.1:9222"));
        assertEquals(9333, ChromeCdpLauncher.portOf("http://localhost:9333"));
        assertEquals(9222, ChromeCdpLauncher.portOf("not a url"));
    }

    @Test
    void isCdpAvailableTrueWhenSomethingAnswers() throws Exception {
        HttpServer chrome = fakeCdp();
        try {
            String cdpUrl = "http://127.0.0.1:" + chrome.getAddress().getPort();
            assertTrue(new ChromeCdpLauncher().isCdpAvailable(cdpUrl));
        } finally {
            chrome.stop(0);
        }
    }

    @Test
    void isCdpAvailableFalseWhenNothingListening() {
        assertFalse(new ChromeCdpLauncher().isCdpAvailable("http://127.0.0.1:1"));
    }

    @Test
    void ensureChromeRunningSkipsLaunchWhenAlreadyUp() throws Exception {
        HttpServer chrome = fakeCdp();
        AtomicInteger launches = new AtomicInteger();
        ChromeCdpLauncher launcher = new ChromeCdpLauncher(Duration.ofMillis(200), Duration.ofMillis(50)) {
            @Override
            void launchChrome(int cdpPort) {
                launches.incrementAndGet();
            }
        };
        try {
            String cdpUrl = "http://127.0.0.1:" + chrome.getAddress().getPort();
            assertTrue(launcher.ensureChromeRunning(cdpUrl, discard()));
            assertEquals(0, launches.get(), "must not launch a second Chrome when one already answers");
        } finally {
            chrome.stop(0);
        }
    }

    @Test
    void ensureChromeRunningLaunchesWhenDownThenTimesOutGracefully() {
        AtomicInteger launches = new AtomicInteger();
        ChromeCdpLauncher launcher = new ChromeCdpLauncher(Duration.ofMillis(150), Duration.ofMillis(50)) {
            @Override
            void launchChrome(int cdpPort) {
                launches.incrementAndGet(); // pretend to launch, but nothing ever comes up
            }
        };
        assertFalse(launcher.ensureChromeRunning("http://127.0.0.1:1", discard()));
        assertEquals(1, launches.get(), "should attempt exactly one launch when nothing is up");
    }

    @Test
    void launchFailureIsReportedNotThrown() {
        ChromeCdpLauncher launcher = new ChromeCdpLauncher(Duration.ofMillis(100), Duration.ofMillis(50)) {
            @Override
            void launchChrome(int cdpPort) throws java.io.IOException {
                throw new java.io.IOException("boom");
            }
        };
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        boolean up = launcher.ensureChromeRunning("http://127.0.0.1:1", new PrintStream(captured, false, StandardCharsets.UTF_8));
        assertFalse(up);
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("Could not launch Chrome automatically"),
                captured.toString(StandardCharsets.UTF_8));
    }
}
