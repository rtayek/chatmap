package chatmap.infrastructure.command;

import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CommandRunner implements CommandExecutor {
    static final int MEMORY_LIMIT_BYTES = 4 * 1024 * 1024;
    private static final int PREFIX_BYTES = 64 * 1024;
    private static final byte[] TRUNCATION_NOTICE =
            "\n... [output truncated in memory; showing prefix and tail]\n".getBytes(StandardCharsets.UTF_8);

    public CommandRunner() {
        System.setProperty("jdk.lang.Process.allowAmbiguousCommands", "false");
    }

    @Override
    public CommandResult run(CommandRequest request) {
        Objects.requireNonNull(request, "request");

        long started = System.nanoTime();
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(request.command());
            if (request.workingDirectory() != null) {
                builder.directory(request.workingDirectory().toFile());
            }
            process = builder.start();
        } catch (IOException exception) {
            throw new CommandExecutionException("Could not start command " + request.command().getFirst(), exception);
        }

        try (ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<CapturedOutput> stdout = streamReaders.submit(() -> readUtf8(
                    process.getInputStream(), request.stdoutTee(), request.stdoutPath()));
            Future<CapturedOutput> stderr = streamReaders.submit(() -> readUtf8(
                    process.getErrorStream(), request.stderrTee(), request.stderrPath()));

            boolean timedOut = false;
            int exitCode = -1;
            try {
                writeStandardInput(process, request.standardInput());
                if (process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    exitCode = process.exitValue();
                } else {
                    timedOut = true;
                    terminate(process);
                }

                Duration duration = Duration.ofNanos(System.nanoTime() - started);
                CapturedOutput capturedStdout = stdout.get();
                CapturedOutput capturedStderr = stderr.get();
                return new CommandResult(exitCode, capturedStdout.text(), capturedStderr.text(), duration, timedOut,
                        capturedStdout.truncated(), capturedStderr.truncated(), request.stdoutPath(),
                        request.stderrPath());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                terminate(process);
                throw new CommandExecutionException("Interrupted while running command " + request.command().getFirst(), exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new CommandExecutionException("Could not read or persist process output for command "
                        + request.command().getFirst() + ": " + cause.getMessage(), exception);
            }
        }
    }

    private static void writeStandardInput(Process process, String standardInput) throws InterruptedException {
        try (var stdin = process.getOutputStream()) {
            stdin.write(standardInput.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            if (process.isAlive()) {
                throw new CommandExecutionException("Could not write process standard input", exception);
            }
        }
    }

    private static CapturedOutput readUtf8(InputStream inputStream, java.io.PrintStream teeStream, Path outputPath)
            throws IOException {
        BoundedOutput output = new BoundedOutput(MEMORY_LIMIT_BYTES, PREFIX_BYTES);
        byte[] buffer = new byte[8192];
        int bytesRead;
        try (OutputStream fileOutput = outputPath == null ? OutputStream.nullOutputStream()
                : Files.newOutputStream(outputPath)) {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                fileOutput.write(buffer, 0, bytesRead);
                if (teeStream != null) {
                    teeStream.write(buffer, 0, bytesRead);
                    teeStream.flush();
                    if (teeStream.checkError()) {
                        throw new IOException("Could not write process output tee");
                    }
                }
            }
        }
        return output.toCapturedOutput();
    }
    
    private static void terminate(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(1000, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record CapturedOutput(String text, boolean truncated) {
    }

    private static final class BoundedOutput {
        private final int memoryLimit;
        private final int prefixLimit;
        private final int tailLimit;
        private final ByteArrayOutputStream full = new ByteArrayOutputStream();
        private final ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        private final byte[] tail;
        private int tailStart;
        private int tailSize;
        private long totalBytes;

        BoundedOutput(int memoryLimit, int prefixLimit) {
            this.memoryLimit = memoryLimit;
            this.prefixLimit = prefixLimit;
            this.tailLimit = memoryLimit - prefixLimit - TRUNCATION_NOTICE.length;
            if (tailLimit <= 0) {
                throw new IllegalArgumentException("memory limit is too small for truncation notice");
            }
            this.tail = new byte[tailLimit];
        }

        void write(byte[] bytes, int offset, int length) {
            if (totalBytes + length <= memoryLimit) {
                full.write(bytes, offset, length);
            }
            totalBytes += length;
            if (prefix.size() < prefixLimit) {
                int prefixBytes = Math.min(length, prefixLimit - prefix.size());
                prefix.write(bytes, offset, prefixBytes);
            }
            appendTail(bytes, offset, length);
        }

        CapturedOutput toCapturedOutput() throws IOException {
            if (totalBytes <= memoryLimit) {
                return new CapturedOutput(decodeUtf8(full.toByteArray()), false);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(memoryLimit);
            output.write(prefix.toByteArray());
            output.write(TRUNCATION_NOTICE);
            output.write(tailBytes());
            return new CapturedOutput(decodeUtf8(output.toByteArray()), true);
        }

        private void appendTail(byte[] bytes, int offset, int length) {
            for (int i = 0; i < length; i++) {
                tail[(tailStart + tailSize) % tail.length] = bytes[offset + i];
                if (tailSize < tail.length) {
                    tailSize++;
                } else {
                    tailStart = (tailStart + 1) % tail.length;
                }
            }
        }

        private byte[] tailBytes() {
            byte[] bytes = new byte[tailSize];
            int firstPart = Math.min(tailSize, tail.length - tailStart);
            System.arraycopy(tail, tailStart, bytes, 0, firstPart);
            if (firstPart < tailSize) {
                System.arraycopy(tail, 0, bytes, firstPart, tailSize - firstPart);
            }
            return Arrays.copyOf(bytes, bytes.length);
        }

        private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        }
    }
}
