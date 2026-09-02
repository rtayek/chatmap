package chatmap.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

import chatmap.application.port.llm.LlmBackendExecutionException;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;

final class OllamaProviderTest {

    @Test
    void postsChatRequestAndParsesAssistantMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(200,
                "{\"message\":{\"role\":\"assistant\",\"content\":\"Ollama answer\"}}", requestBody);
        try {
            LlmResponse response = providerFor(server).execute(ModelTarget.ollamaGlm4,
                    LlmRequest.withSystemPrompt("What is Java?", "Answer concisely."));

            assertEquals("Ollama answer", response.text());
            assertEquals(java.util.Optional.of("glm4:9b"), response.providerModelName());
            assertTrue(requestBody.get().contains("\"model\":\"glm4:9b\""));
            assertTrue(requestBody.get().contains("\"role\":\"system\""));
            assertTrue(requestBody.get().contains("\"role\":\"user\""));
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "{}",
            "{\"message\":null}",
            "{\"message\":{}}",
            "{\"message\":{\"content\":null}}",
            "{\"message\":{\"content\":{}}}",
            "{\"message\":{\"content\":[]}}",
            "{\"message\":{\"content\":42}}",
            "{\"message\":{\"content\":true}}",
            "{\"message\":{\"content\":\"   \"}}"
    })
    void rejectsInvalidSuccessfulResponse(String responseBody) throws Exception {
        HttpServer server = startServer(200, responseBody, new AtomicReference<>());
        try {
            LlmBackendExecutionException exception = assertThrows(LlmBackendExecutionException.class,
                    () -> providerFor(server).execute(ModelTarget.ollamaGlm4, LlmRequest.of("Hello")));

            assertTrue(exception.getMessage().contains("Ollama returned invalid chat JSON"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsNonSuccessfulHttpResponse() throws Exception {
        HttpServer server = startServer(503, "Ollama unavailable", new AtomicReference<>());
        try {
            LlmBackendExecutionException exception = assertThrows(LlmBackendExecutionException.class,
                    () -> providerFor(server).execute(ModelTarget.ollamaGlm4, LlmRequest.of("Hello")));

            assertTrue(exception.getMessage().contains("HTTP 503"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer(int status, String responseBody, AtomicReference<String> requestBody)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static OllamaProvider providerFor(HttpServer server) {
        return new OllamaProvider(HttpClient.newHttpClient(),
                URI.create("http://localhost:" + server.getAddress().getPort()), Duration.ofSeconds(5));
    }
}
