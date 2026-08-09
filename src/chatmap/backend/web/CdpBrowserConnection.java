package chatmap.backend.web;

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

final class CdpBrowserConnection {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(4);

    private final String cdpUrl;
    private final HttpClient httpClient;

    CdpBrowserConnection(String cdpUrl) {
        this(cdpUrl, HttpClient.newHttpClient());
    }

    CdpBrowserConnection(String cdpUrl, HttpClient httpClient) {
        this.cdpUrl = stripTrailingSlash(cdpUrl);
        this.httpClient = httpClient;
    }

    Optional<CdpPage> openPage(String url) throws IOException, InterruptedException {
        Optional<Target> existing = listPageTargets().stream()
                .filter(target -> target.url().contains(url))
                .findFirst();
        Target target = existing.orElseGet(() -> createPageTarget(url));
        CdpPage page = new CdpPage(target.webSocketDebuggerUrl());
        page.bringToFront();
        if (existing.isEmpty()) {
            page.navigate(url);
        }
        return Optional.of(page);
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
                targets.add(new Target(targetUrl, webSocketUrl));
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
            return new Target(targetUrl == null ? url : targetUrl, webSocketUrl);
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

    private record Target(String url, String webSocketDebuggerUrl) {}
}
