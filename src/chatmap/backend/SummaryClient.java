package chatmap.backend;

import java.io.IOException;

@FunctionalInterface
public interface SummaryClient {
    String ask(String prompt) throws IOException;
}
