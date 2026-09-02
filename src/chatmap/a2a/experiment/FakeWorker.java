package chatmap.a2a.experiment;

final class FakeWorker {
    Result execute(String request) {
        String normalizedRequest = request == null ? "" : request.trim();

        if (normalizedRequest.startsWith("complete:")) {
            String text = normalizedRequest.substring("complete:".length()).trim();
            if (!text.isEmpty()) {
                return new Result(Status.COMPLETED, text);
            }
        }

        if (normalizedRequest.equals("input-required")) {
            return new Result(Status.INPUT_REQUIRED, "Please provide text after complete:");
        }

        if (normalizedRequest.equals("fail")) {
            return new Result(Status.FAILED, "The fake worker failed as requested");
        }

        return new Result(Status.FAILED, "Unsupported request: " + normalizedRequest);
    }

    enum Status {
        COMPLETED,
        INPUT_REQUIRED,
        FAILED
    }

    record Result(Status status, String text) {
    }
}
