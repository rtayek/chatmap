package chatmap.infrastructure.llm;

final class StructuredOutputException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    StructuredOutputException(String message) {
        super(message);
    }
}
