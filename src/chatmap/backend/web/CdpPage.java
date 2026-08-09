package chatmap.backend.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class CdpPage implements AutoCloseable {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(8);
    private static final long POLL_INTERVAL_MILLIS = 100;
    private static final Gson GSON = new Gson();

    private final CdpTransport transport;

    CdpPage(String webSocketUrl) {
        this(new CdpSocket(webSocketUrl));
    }

    CdpPage(CdpTransport transport) {
        this.transport = transport;
    }

    void navigate(String url) {
        transport.send("Page.navigate", Map.of("url", url));
        waitForReadyState(Duration.ofSeconds(8));
    }

    void bringToFront() {
        transport.send("Page.bringToFront", Map.of());
    }

    String url() {
        Object value = evaluate("() => location.href");
        return value == null ? "" : value.toString();
    }

    void waitForSelector(String selector, long timeoutMillis) {
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (System.nanoTime() < deadline) {
            if (locator(selector).count() > 0) {
                return;
            }
            sleep(POLL_INTERVAL_MILLIS);
        }
        throw new IllegalStateException("Selector not found: " + selector);
    }

    void waitForTimeout(long timeoutMillis) {
        sleep(timeoutMillis);
    }

    Object evaluate(String script) {
        JsonObject response = transport.send("Runtime.evaluate", Map.of(
                "expression", expression(script),
                "returnByValue", true,
                "awaitPromise", true));
        if (response.has("error")) {
            throw new IllegalStateException("CDP command failed: Runtime.evaluate");
        }
        JsonObject result = response.getAsJsonObject("result");
        if (result == null || result.has("exceptionDetails")) {
            throw new IllegalStateException("CDP evaluation failed");
        }
        JsonObject remoteObject = result.getAsJsonObject("result");
        if (remoteObject == null || !remoteObject.has("value")) {
            return null;
        }
        return toJavaValue(remoteObject.get("value"));
    }

    CdpLocator locator(String selector) {
        return new CdpLocator(this, selector);
    }

    @Override
    public void close() {
        transport.close();
    }

    private void waitForReadyState(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Object readyState = evaluate("() => document.readyState");
            if (!"loading".equals(readyState)) {
                return;
            }
            sleep(POLL_INTERVAL_MILLIS);
        }
    }

    private static Object toJavaValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
            return primitive.getAsString();
        }
        return GSON.toJson(value);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for browser", interrupted);
        }
    }

    static String jsString(String value) {
        return GSON.toJson(value);
    }

    static String expression(String script) {
        String trimmed = script.stripLeading();
        return trimmed.startsWith("() =>") ? "(" + script + ")()" : script;
    }

    static final class CdpLocator {

        private final CdpPage page;
        private final String selector;
        private final Optional<Integer> index;

        CdpLocator(CdpPage page, String selector) {
            this(page, selector, Optional.empty());
        }

        private CdpLocator(CdpPage page, String selector, Optional<Integer> index) {
            this.page = page;
            this.selector = selector;
            this.index = index;
        }

        int count() {
            Object count = page.evaluate("() => document.querySelectorAll("
                    + jsString(selector) + ").length");
            return count == null ? 0 : ((Number) count).intValue();
        }

        CdpLocator nth(int elementIndex) {
            return new CdpLocator(page, selector, Optional.of(elementIndex));
        }

        CdpLocator first() {
            return nth(0);
        }

        String getAttribute(String name) {
            Object value = page.evaluate("() => {"
                    + "const e=document.querySelectorAll(" + jsString(selector) + ")["
                    + requiredIndex() + "];"
                    + "return e ? e.getAttribute(" + jsString(name) + ") : null;"
                    + "}");
            return value == null ? null : value.toString();
        }

        String innerText() {
            Object value = page.evaluate("() => {"
                    + "const e=document.querySelectorAll(" + jsString(selector) + ")["
                    + requiredIndex() + "];"
                    + "return e ? (e.innerText || '') : null;"
                    + "}");
            return value == null ? null : value.toString();
        }

        void click(long timeoutMillis) {
            long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
            while (System.nanoTime() < deadline) {
                Object clicked = page.evaluate("() => {"
                        + "const e=document.querySelectorAll(" + jsString(selector) + ")["
                        + requiredIndex() + "];"
                        + "if (!e) return false;"
                        + "e.click();"
                        + "return true;"
                        + "}");
                if (Boolean.TRUE.equals(clicked)) {
                    return;
                }
                sleep(POLL_INTERVAL_MILLIS);
            }
            throw new IllegalStateException("Could not click selector: " + selector);
        }

        private int requiredIndex() {
            return index.orElseThrow(() -> new IllegalStateException("Locator index not selected"));
        }
    }

    static JsonObject successfulValue(Object value) {
        JsonObject response = new JsonObject();
        JsonObject result = new JsonObject();
        JsonObject remoteObject = new JsonObject();
        remoteObject.add("value", GSON.toJsonTree(value));
        result.add("result", remoteObject);
        response.add("result", result);
        return response;
    }

    static JsonObject commandError(String message) {
        JsonObject response = new JsonObject();
        JsonObject error = new JsonObject();
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private static final class CdpSocket implements WebSocket.Listener, CdpTransport {

        private final AtomicLong nextId = new AtomicLong(1);
        private final Map<Long, java.util.concurrent.CompletableFuture<JsonObject>> pending =
                new ConcurrentHashMap<>();
        private final StringBuilder text = new StringBuilder();
        private final WebSocket webSocket;

        CdpSocket(String webSocketUrl) {
            try {
                webSocket = HttpClient.newHttpClient()
                        .newWebSocketBuilder()
                        .connectTimeout(COMMAND_TIMEOUT)
                        .buildAsync(URI.create(webSocketUrl), this)
                        .get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while connecting to CDP", interrupted);
            } catch (ExecutionException | TimeoutException failure) {
                throw new IllegalStateException("Could not connect to CDP page websocket", failure);
            }
        }

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public JsonObject send(String method, Map<String, ?> params) {
            long id = nextId.getAndIncrement();
            java.util.concurrent.CompletableFuture<JsonObject> result = new java.util.concurrent.CompletableFuture<>();
            pending.put(id, result);
            JsonObject request = new JsonObject();
            request.addProperty("id", id);
            request.addProperty("method", method);
            request.add("params", GSON.toJsonTree(params));
            webSocket.sendText(GSON.toJson(request), true).join();
            try {
                JsonObject response = result.get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                if (response.has("error")) {
                    throw new IllegalStateException("CDP command failed: " + method);
                }
                return response;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for CDP command", interrupted);
            } catch (ExecutionException | TimeoutException failure) {
                throw new IllegalStateException("Timed out waiting for CDP command: " + method, failure);
            } finally {
                pending.remove(id);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                JsonObject message = JsonParser.parseString(text.toString()).getAsJsonObject();
                text.setLength(0);
                JsonElement idElement = message.get("id");
                if (idElement != null) {
                    java.util.concurrent.CompletableFuture<JsonObject> future =
                            pending.get(idElement.getAsLong());
                    if (future != null) {
                        future.complete(message);
                    }
                }
            }
            socket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            List<java.util.concurrent.CompletableFuture<JsonObject>> futures =
                    new ArrayList<>(pending.values());
            futures.forEach(future -> future.completeExceptionally(error));
        }

        @Override
        public void close() {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            } catch (Exception ignored) {
            }
        }
    }
}
