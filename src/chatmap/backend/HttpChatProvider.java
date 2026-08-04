package chatmap.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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
 * </ul>
 */
public final class HttpChatProvider implements ChatProvider {

    private final String name;
    private final URI endpoint;
    private final String bearerToken; // may be null
    private final HttpClient httpClient;
    private final Duration timeout;

    public HttpChatProvider(String name, URI endpoint, String bearerToken,
            HttpClient httpClient, Duration timeout) {
        this.name = Objects.requireNonNull(name, "name");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.bearerToken = bearerToken;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** Builds a provider from environment variables, or empty when none is configured. */
    public static Optional<HttpChatProvider> fromEnv() {
        String url = System.getenv("CHATMAP_PROVIDER_URL");
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI endpoint = URI.create(url.strip());
        String name = System.getenv("CHATMAP_PROVIDER_NAME");
        if (name == null || name.isBlank()) {
            name = endpoint.getHost() != null ? endpoint.getHost() : url;
        }
        String token = System.getenv("CHATMAP_PROVIDER_TOKEN");
        return Optional.of(new HttpChatProvider(name, endpoint, token,
                HttpClient.newHttpClient(), Duration.ofSeconds(30)));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<ImportedChat> latestChat() throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Accept", "text/markdown, text/plain")
                .GET();
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<String> response =
                httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
            return Optional.empty(); // provider reachable but has no live chat
        }
        if (response.statusCode() / 100 != 2) {
            throw new java.io.IOException(name + " returned HTTP " + response.statusCode());
        }

        ImportedChat imported = new MarkdownImporter()
                .importMarkdown(response.body(), name + " live chat", Instant.now().toString());
        return Optional.of(imported);
    }
}
