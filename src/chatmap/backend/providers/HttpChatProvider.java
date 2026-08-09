package chatmap.backend.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import chatmap.importer.ImportedChat;
import chatmap.importer.MarkdownImporter;

/**
 * A {@link ChatProvider} that fetches the provider's latest conversation over
 * HTTP as a role-prefixed Markdown transcript and parses it with
 * {@link MarkdownImporter}.
 *
 * The endpoint is deliberately generic so any provider (or a local export
 * proxy) can be plugged in via configuration rather than hard-coded here.
 * {@link #fromEnv()} builds one from environment variables:
 * <ul>
 *   <li>{@code CHATMAP_PROVIDER_URL}  - endpoint returning the latest transcript (required)</li>
 *   <li>{@code CHATMAP_PROVIDER_NAME} - display name (optional; defaults to the URL host)</li>
 *   <li>{@code CHATMAP_PROVIDER_TOKEN} - bearer token sent as Authorization (optional)</li>
 *   <li>{@code CHATMAP_PROVIDER_LAUNCH_CMD} - shell command to start the local provider
 *       server when the URL does not answer (optional; no launch when unset)</li>
 * </ul>
 *
 * When a launch command is configured and the first request finds nothing
 * listening, the command is run once and the request is retried with a short
 * backoff (the local server plus its browser take a few seconds to come up)
 * before giving up.
 *
 * With nothing configured, {@link #fromEnv()} still enables the provider using
 * built-in defaults: the local MyClaw {@code claude-web-server} URL, and — only
 * if a sibling MyClaw checkout's launch script is actually present — that script
 * as the launch command. So a zero-config {@code summarizeChat} still attempts
 * the live chat first, on a machine laid out that way, rather than skipping it.
 */
public final class HttpChatProvider implements ChatProvider {

    /** MyClaw's claude-web-server default endpoint; used when CHATMAP_PROVIDER_URL is unset. */
    static final String DEFAULT_URL = "http://127.0.0.1:8722/latest-chat";

    /** Best-effort default: a sibling MyClaw checkout's launch script, relative to ChatMap's cwd. */
    static final String DEFAULT_LAUNCH_SCRIPT = "../myclaw/claude-web-latest-server.sh";

    /** Display name used when CHATMAP_PROVIDER_NAME is unset. */
    static final String DEFAULT_NAME = "Claude (web)";

    /** Starts the local provider server. A seam so tests need not spawn a process. */
    @FunctionalInterface
    public interface ServerLauncher {
        void launch() throws IOException;
    }

    private final String name;
    private final URI endpoint;
    private final String bearerToken; // may be null
    private final HttpClient httpClient;
    private final Duration timeout;
    private final ServerLauncher launcher; // may be null: no auto-launch
    private final Duration launchWait;     // total time to wait for the server after launching
    private final Duration retryInterval;  // initial poll interval (backs off, capped)

    public HttpChatProvider(String name, URI endpoint, String bearerToken,
            HttpClient httpClient, Duration timeout) {
        this(name, endpoint, bearerToken, httpClient, timeout, null,
                Duration.ofSeconds(20), Duration.ofSeconds(1));
    }

    public HttpChatProvider(String name, URI endpoint, String bearerToken,
            HttpClient httpClient, Duration timeout, ServerLauncher launcher,
            Duration launchWait, Duration retryInterval) {
        this.name = Objects.requireNonNull(name, "name");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.bearerToken = bearerToken;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.launcher = launcher;
        this.launchWait = Objects.requireNonNull(launchWait, "launchWait");
        this.retryInterval = Objects.requireNonNull(retryInterval, "retryInterval");
    }

    /**
     * Builds a provider from environment variables, falling back to built-in
     * defaults when they are unset (so the provider is enabled by default, not
     * disabled). Always returns a provider.
     */
    public static Optional<HttpChatProvider> fromEnv() {
        String url = System.getenv("CHATMAP_PROVIDER_URL");
        if (url == null || url.isBlank()) {
            url = DEFAULT_URL; // zero-config: point at MyClaw's local claude-web-server
        }
        URI endpoint = URI.create(url.strip());

        String name = System.getenv("CHATMAP_PROVIDER_NAME");
        if (name == null || name.isBlank()) {
            name = DEFAULT_NAME;
        }

        String token = System.getenv("CHATMAP_PROVIDER_TOKEN");

        return Optional.of(new HttpChatProvider(name, endpoint, token,
                HttpClient.newHttpClient(), Duration.ofSeconds(30),
                resolveLauncher(), Duration.ofSeconds(20), Duration.ofSeconds(1)));
    }

    /**
     * The launcher used by {@link #fromEnv()}: an explicit CHATMAP_PROVIDER_LAUNCH_CMD
     * is trusted as-is; otherwise the sibling MyClaw script is used only if it
     * actually exists on disk. A missing default script means "no launcher" — the
     * unreachable-with-no-launcher path then throws and the caller falls back to the
     * most recent stored chat, exactly as before. We check existence up front so a
     * wrong guess never produces a failed launch attempt.
     */
    static ServerLauncher resolveLauncher() {
        String launchCmd = System.getenv("CHATMAP_PROVIDER_LAUNCH_CMD");
        if (launchCmd != null && !launchCmd.isBlank()) {
            return shellLauncher(launchCmd.strip());
        }
        return defaultScriptLauncher(Path.of(DEFAULT_LAUNCH_SCRIPT)).orElse(null);
    }

    /**
     * The default launcher, gated on the script actually existing. A missing
     * script yields empty (== "no launcher"), so a wrong guess about directory
     * layout never turns into a failed launch attempt.
     */
    static Optional<ServerLauncher> defaultScriptLauncher(Path script) {
        if (Files.isRegularFile(script)) {
            // Forward slashes so `sh` is happy even when Path renders backslashes on Windows.
            return Optional.of(shellLauncher("sh " + script.toString().replace('\\', '/')));
        }
        return Optional.empty();
    }

    /** Runs a command through the OS shell as a detached background process. */
    static ServerLauncher shellLauncher(String command) {
        return () -> {
            ProcessBuilder pb = isWindows()
                    ? new ProcessBuilder("cmd", "/c", command)
                    : new ProcessBuilder("sh", "-c", command);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
        };
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<ImportedChat> latestChat() throws Exception {
        Optional<HttpResponse<String>> firstResponse = tryFetch();
        if (firstResponse.isPresent()) {
            return handle(firstResponse.get()); // server already up
        }

        // Nothing listening. Without a launch command this is just "unavailable".
        if (launcher == null) {
            throw new IOException(name + " endpoint " + endpoint + " is not reachable");
        }

        System.err.println("Provider endpoint " + endpoint
                + " not answering; launching local server and retrying...");
        launcher.launch();
        return handle(fetchWithBackoff());
    }

    /** A single request attempt; empty when the endpoint is unreachable (nothing listening). */
    private Optional<HttpResponse<String>> tryFetch() throws InterruptedException {
        try {
            return Optional.of(send());
        } catch (IOException unreachable) {
            return Optional.empty();
        }
    }

    /** Polls the endpoint after a launch, backing off, until it answers or the deadline passes. */
    private HttpResponse<String> fetchWithBackoff() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + launchWait.toNanos();
        long sleepMillis = Math.max(1, retryInterval.toMillis());
        IOException lastFailure = null;
        while (true) {
            try {
                return send();
            } catch (IOException notUpYet) {
                lastFailure = notUpYet;
            }
            if (System.nanoTime() >= deadline) {
                if (lastFailure == null) {
                    throw new IOException(name + " did not come up within " + launchWait);
                }
                throw lastFailure;
            }
            Thread.sleep(sleepMillis);
            sleepMillis = Math.min(sleepMillis * 2, 2000); // gentle backoff, capped at 2s
        }
    }

    private HttpResponse<String> send() throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", "text/markdown, text/plain")
                .GET();
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Optional<ImportedChat> handle(HttpResponse<String> response) throws IOException {
        if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
            return Optional.empty(); // provider reachable but has no live chat
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException(name + " returned HTTP " + response.statusCode());
        }

        ImportedChat imported = new MarkdownImporter()
                .importMarkdown(response.body(), name + " live chat", Instant.now().toString());
        return Optional.of(imported);
    }
}
