package chatmap.application.port.document;

import java.io.IOException;

/** Reads the one configured project document exposed by the read-only adapter. */
public interface ProjectDocumentReader {

    DocumentResult readWorkingContext() throws IOException;

    record DocumentResult(Status status, String relativePath, String text, String hash, String detail) {
        public enum Status {
            available,
            unavailable,
            missing,
            invalidConfiguration
        }
    }
}
