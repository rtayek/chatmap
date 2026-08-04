package chatmap.backend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs the local `claude` CLI as a subprocess and returns its output as
 * plain text. This is the one place ChatMap talks to an AI backend; the
 * import/store/search/export pipeline never needs it and does not depend
 * on it.
 *
 * Adapted from MyClaw's CommandRunner (myclaw.execution), simplified for
 * ChatMap's single purpose: send a prompt, get text back.
 */
public final class ClaudeCliClient {

    private final Duration timeout;

    public ClaudeCliClient() {
        this(Duration.ofMinutes(3));
    }

    public ClaudeCliClient(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** Sends prompt to the claude CLI in print mode and returns its standard output, trimmed. */
    public String ask(String prompt) throws IOException {
        Objects.requireNonNull(prompt, "prompt");

        Process process;
        try {
            process = new ProcessBuilder("claude", "-p", prompt).start();
        } catch (IOException e) {
            throw new IOException("Could not start claude CLI: " + e.getMessage(), e);
        }

        ExecutorService streamReaders = Executors.newFixedThreadPool(2);
        Future<String> stdout = streamReaders.submit(() -> readUtf8(process.getInputStream()));
        Future<String> stderr = streamReaders.submit(() -> readUtf8(process.getErrorStream()));

        try {
            process.getOutputStream().close();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                terminate(process);
                throw new IOException("claude CLI timed out after " + timeout);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("claude CLI exited with status " + exitCode + ": " + stderr.get().strip());
            }
            return stdout.get().strip();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminate(process);
            throw new IOException("Interrupted while running claude CLI", e);
        } catch (ExecutionException e) {
            throw new IOException("Could not read claude CLI output", e);
        } finally {
            streamReaders.shutdownNow();
        }
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        inputStream.transferTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void terminate(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
