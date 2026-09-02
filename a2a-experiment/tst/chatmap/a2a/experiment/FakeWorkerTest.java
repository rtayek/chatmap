package chatmap.a2a.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FakeWorkerTest {
    @Test
    void completesWithRequestedArtifactText() {
        FakeWorker.Result result = worker.execute("complete: durable result");

        assertEquals(FakeWorker.Status.COMPLETED, result.status());
        assertEquals("durable result", result.text());
    }

    @Test
    void requestsAdditionalInput() {
        FakeWorker.Result result = worker.execute("input-required");

        assertEquals(FakeWorker.Status.INPUT_REQUIRED, result.status());
        assertEquals("Please provide text after complete:", result.text());
    }

    @Test
    void failsWhenRequested() {
        FakeWorker.Result result = worker.execute("fail");

        assertEquals(FakeWorker.Status.FAILED, result.status());
        assertEquals("The fake worker failed as requested", result.text());
    }

    @Test
    void rejectsUnsupportedRequests() {
        FakeWorker.Result result = worker.execute("surprise");

        assertEquals(FakeWorker.Status.FAILED, result.status());
        assertEquals("Unsupported request: surprise", result.text());
    }

    private final FakeWorker worker = new FakeWorker();
}
