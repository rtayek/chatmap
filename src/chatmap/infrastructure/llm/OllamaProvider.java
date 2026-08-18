package chatmap.infrastructure.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.LlmBackendExecutionException;
import chatmap.application.port.llm.LlmBackendStartupException;
import chatmap.application.port.llm.LlmBackendUnsupportedRequestException;
import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.OutputFormat;
import chatmap.application.port.command.CommandResult;

/** HTTP provider for a local Ollama server. */
public final class OllamaProvider implements LlmProvider {
    private static final URI DEFAULT_BASE_URI = URI.create("http://localhost:11434");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);

    private final HttpClient httpClient;
    private final URI chatUri;
    private final Duration timeout;

    public OllamaProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create(System.getProperty("chatmap.ollama.url", DEFAULT_BASE_URI.toString())),
                DEFAULT_TIMEOUT);
    }

    OllamaProvider(HttpClient httpClient, URI baseUri, Duration timeout) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.chatUri = java.util.Objects.requireNonNull(baseUri, "baseUri").resolve("/api/chat");
        this.timeout = java.util.Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public LlmResponse execute(ModelTarget target, LlmRequest request) {
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(request, "request");
        validateRequest(target, request);

        long started = System.nanoTime();
        HttpRequest httpRequest = HttpRequest.newBuilder(chatUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(target, request)))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new LlmBackendStartupException("Could not reach Ollama at " + chatUri,
                    backendId(target), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmBackendStartupException("Interrupted while contacting Ollama at " + chatUri,
                    backendId(target), exception);
        }

        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        CommandResult commandResult = new CommandResult(response.statusCode(), response.body(), "", duration, false);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmBackendExecutionException(
                    target.displayName() + " Ollama request returned HTTP " + response.statusCode(),
                    backendId(target), commandResult);
        }
        try {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject message = body.getAsJsonObject("message");
            String text = message == null || !message.has("content") ? "" : message.get("content").getAsString();
            if (text.isBlank()) {
                throw new JsonParseException("response message content was blank");
            }
            return new LlmResponse(text, backendId(target), duration, target, null);
        } catch (JsonParseException | IllegalStateException | ClassCastException parseFailure) {
            throw new LlmBackendExecutionException("Ollama returned invalid chat JSON: " + parseFailure.getMessage(),
                    backendId(target), commandResult);
        }
    }

    @Override
    public Set<LlmCapability> capabilities(ModelTarget target) {
        return Set.of(LlmCapability.systemPrompt);
    }

    @Override
    public List<String> listSessions(ModelTarget target) {
        return List.of();
    }

    private static String requestBody(ModelTarget target, LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", target.providerModelName());
        body.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        request.systemPrompt().ifPresent(system -> messages.add(message("system", system)));
        messages.add(message("user", request.effectivePrompt()));
        body.add("messages", messages);
        return body.toString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static void validateRequest(ModelTarget target, LlmRequest request) {
        BackendId backendId = backendId(target);
        if (request.sessionId().isPresent()) {
            throw new LlmBackendUnsupportedRequestException(
                    target.displayName() + " Ollama HTTP provider does not support resumable sessions", backendId);
        }
        if (request.outputFormat() == OutputFormat.streamJson) {
            throw new LlmBackendUnsupportedRequestException(
                    target.displayName() + " Ollama HTTP provider does not support stream-json output", backendId);
        }
        if (request.permissionMode() == chatmap.application.port.llm.PermissionMode.unrestricted) {
            throw new LlmBackendUnsupportedRequestException(
                    target.displayName() + " Ollama HTTP provider does not edit files", backendId);
        }
    }

    private static BackendId backendId(ModelTarget target) {
        return new BackendId(target.displayName());
    }
}
