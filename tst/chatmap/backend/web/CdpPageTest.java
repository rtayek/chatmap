package chatmap.backend.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class CdpPageTest {

    @Test
    void quotesJavaScriptStringsDeterministically() {
        assertEquals("\"a\\\\b \\\"quoted\\\"\"", CdpPage.jsString("a\\b \"quoted\""));
    }

    @Test
    void invokesFunctionSnippetsForCdpRuntimeEvaluation() {
        assertEquals("(() => document.readyState)()", CdpPage.expression("() => document.readyState"));
        assertEquals("document.title", CdpPage.expression("document.title"));
    }

    @Test
    void evaluateSendsRuntimeEvaluateWithSafeOptions() {
        FakeTransport transport = new FakeTransport(CdpPage.successfulValue("complete"));
        CdpPage page = new CdpPage(transport);

        assertEquals("complete", page.evaluate("() => document.readyState"));

        assertEquals("Runtime.evaluate", transport.calls().get(0).method());
        assertEquals("(() => document.readyState)()", transport.calls().get(0).params().get("expression"));
        assertEquals(Boolean.TRUE, transport.calls().get(0).params().get("returnByValue"));
        assertEquals(Boolean.TRUE, transport.calls().get(0).params().get("awaitPromise"));
    }

    @Test
    void evaluateThrowsOnCdpCommandError() {
        FakeTransport transport = new FakeTransport(CdpPage.commandError("bad selector"));
        CdpPage page = new CdpPage(transport);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> page.evaluate("document.title"));

        assertTrue(thrown.getMessage().contains("CDP command failed"));
    }

    @Test
    void locatorUsesIndexedSelectorOperations() {
        FakeTransport transport = new FakeTransport(
                CdpPage.successfulValue(2),
                CdpPage.successfulValue("href-value"),
                CdpPage.successfulValue("text-value"),
                CdpPage.successfulValue(true));
        CdpPage page = new CdpPage(transport);
        CdpPage.CdpLocator second = page.locator("a[href*='/c/']").nth(1);

        assertEquals(2, page.locator("a[href*='/c/']").count());
        assertEquals("href-value", second.getAttribute("href"));
        assertEquals("text-value", second.innerText());
        second.click(1);

        List<String> expressions = transport.calls().stream()
                .map(call -> call.params().get("expression").toString())
                .toList();
        assertTrue(expressions.get(0).contains("querySelectorAll("));
        assertTrue(expressions.get(0).contains(".length"));
        assertTrue(expressions.get(1).contains("querySelectorAll("));
        assertTrue(expressions.get(1).contains("[1]"));
        assertTrue(expressions.get(1).contains("getAttribute(\"href\")"));
        assertTrue(expressions.get(2).contains("innerText"));
        assertTrue(expressions.get(3).contains("click()"));
    }

    @Test
    void closeClosesTransport() {
        FakeTransport transport = new FakeTransport();
        new CdpPage(transport).close();

        assertTrue(transport.closed());
    }

    @Test
    void closeRunsOnCloseAfterTheTransportCloses() {
        FakeTransport transport = new FakeTransport();
        boolean[] onCloseRanAfterTransportClosed = {false};

        new CdpPage(transport, () -> onCloseRanAfterTransportClosed[0] = transport.closed()).close();

        assertTrue(onCloseRanAfterTransportClosed[0]);
    }

    @Test
    void closeSwallowsAnExceptionFromOnClose() {
        FakeTransport transport = new FakeTransport();
        CdpPage page = new CdpPage(transport, () -> {
            throw new IllegalStateException("simulated close-hook failure");
        });

        assertDoesNotThrow(page::close);
        assertTrue(transport.closed(), "the transport must still close even if the hook throws");
    }

    private static final class FakeTransport implements CdpTransport {
        private final List<JsonObject> responses;
        private final List<Call> calls = new ArrayList<>();
        private boolean closed;
        private int responseIndex;

        FakeTransport(JsonObject... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public JsonObject send(String method, Map<String, ?> params) {
            calls.add(new Call(method, params));
            if (responseIndex >= responses.size()) {
                return CdpPage.successfulValue(null);
            }
            return responses.get(responseIndex++);
        }

        @Override
        public void close() {
            closed = true;
        }

        List<Call> calls() {
            return calls;
        }

        boolean closed() {
            return closed;
        }
    }

    private record Call(String method, Map<String, ?> params) {
    }
}
