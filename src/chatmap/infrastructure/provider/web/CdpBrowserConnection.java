package chatmap.infrastructure.provider.web;


import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class CdpBrowserConnection {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(4);

    /**
     * One {@link HttpClient} per JVM, not per connection. A full import run opens and closes
     * dozens of {@code CdpBrowserConnection}s in quick succession (one per fetched conversation);
     * each fresh client spins up its own selector/executor threads, and that churn was a real
     * source of intermittent CDP command timeouts under rapid-fire use.
     */
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newHttpClient();

    /** Builds the page for a target; test seam so tests don't need a live WebSocket. */
    @FunctionalInterface
    interface PageFactory {
        CdpPage create(String webSocketUrl, Runnable onClose);
    }

    private final String cdpUrl;
    private final HttpClient httpClient;
    private final PageFactory pageFactory;

    public CdpBrowserConnection(String cdpUrl) {
        this(cdpUrl, SHARED_HTTP_CLIENT);
    }

    public CdpBrowserConnection(String cdpUrl, HttpClient httpClient) {
        this(cdpUrl, httpClient, null);
    }

    CdpBrowserConnection(String cdpUrl, HttpClient httpClient, PageFactory pageFactory) {
        this.cdpUrl = stripTrailingSlash(cdpUrl);
        this.httpClient = httpClient;
        this.pageFactory = pageFactory != null ? pageFactory
                : (webSocketUrl, onClose) -> new CdpPage(webSocketUrl, onClose, httpClient);
    }

    /**
     * Finds an already-open page for the URL, or creates a new browser tab. A tab
     * this call creates is closed again when the returned page is closed; a tab
     * the user already had open is left alone.
     */
    public Optional<CdpPage> openPage(String url) throws IOException, InterruptedException {
        Optional<Target> existing = listPageTargets().stream()
                .filter(target -> target.url().contains(url))
                .findFirst();
        boolean createdHere = existing.isEmpty();
        Target target = existing.orElseGet(() -> createPageTarget(url));
        Runnable onClose = createdHere && target.id() != null
                ? () -> closeTarget(target.id())
                : () -> { };
        CdpPage page = pageFactory.create(target.webSocketDebuggerUrl(), onClose);
        page.bringToFront();
        if (createdHere) {
            page.navigate(url);
        }
        return Optional.of(page);
    }

    /**
     * Best-effort close of a browser tab this connection created. Never throws.
     *
     * Chrome's {@code /json/close} returns before the target is actually torn down, so without
     * confirmation a call to {@link #openPage} made right after this one can still see the
     * closing tab in {@code /json/list} and wrongly reuse it (skipping {@code navigate()} since
     * {@code createdHere} is false) — landing on stale, mid-teardown DOM instead of a fresh page.
     * A full import run closes and reopens a tab per fetched conversation, so this race fired on
     * roughly every other fetch. Polling until the target is confirmed gone closes that window.
     */
    private void closeTarget(String targetId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(cdpUrl + "/json/close/" + targetId))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            awaitTargetGone(targetId);
        } catch (IOException | RuntimeException ignored) {
            // Not worth failing the caller over a leftover tab.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitTargetGone(String targetId) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            boolean stillListed = listPageTargets().stream().anyMatch(target -> targetId.equals(target.id()));
            if (!stillListed) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private List<Target> listPageTargets() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(cdpUrl + "/json/list"))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("CDP target list failed: HTTP " + response.statusCode());
        }
        List<Target> targets = new ArrayList<>();
        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonArray()) {
            return targets;
        }
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String type = string(object, "type");
            String targetUrl = string(object, "url");
            String webSocketUrl = string(object, "webSocketDebuggerUrl");
            if ("page".equals(type) && targetUrl != null && webSocketUrl != null) {
                targets.add(new Target(string(object, "id"), targetUrl, webSocketUrl));
            }
        }
        return targets;
    }

    private Target createPageTarget(String url) {
        String encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
        String endpoint = cdpUrl + "/json/new?" + encodedUrl;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(HTTP_TIMEOUT)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("CDP target creation failed: HTTP " + response.statusCode());
            }
            JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
            String targetUrl = string(object, "url");
            String webSocketUrl = string(object, "webSocketDebuggerUrl");
            if (webSocketUrl == null) {
                throw new IOException("CDP target did not include a websocket URL");
            }
            return new Target(string(object, "id"), targetUrl == null ? url : targetUrl, webSocketUrl);
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not create CDP page target", failure);
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record Target(String id, String url, String webSocketDebuggerUrl) {}
}
