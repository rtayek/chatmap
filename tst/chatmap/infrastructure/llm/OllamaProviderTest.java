package chatmap.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;

final class OllamaProviderTest {

    @Test
    void postsChatRequestAndParsesAssistantMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"message\":{\"role\":\"assistant\",\"content\":\"Ollama answer\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            OllamaProvider provider = new OllamaProvider(HttpClient.newHttpClient(),
                    URI.create("http://localhost:" + server.getAddress().getPort()), Duration.ofSeconds(5));
            LlmResponse response = provider.execute(ModelTarget.ollamaGlm4,
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
}
