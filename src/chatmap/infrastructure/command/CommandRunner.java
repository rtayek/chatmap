package chatmap.infrastructure.command;

import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;

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

public final class CommandRunner implements CommandExecutor {
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
            Future<String> stdout = streamReaders.submit(() -> readUtf8(process.getInputStream(), request.stdoutTee()));
            Future<String> stderr = streamReaders.submit(() -> readUtf8(process.getErrorStream(), request.stderrTee()));

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
                return new CommandResult(exitCode, stdout.get(), stderr.get(), duration, timedOut);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                terminate(process);
                throw new CommandExecutionException("Interrupted while running command " + request.command().getFirst(), exception);
            } catch (ExecutionException exception) {
                throw new CommandExecutionException("Could not read process output for command " + request.command().getFirst(), exception);
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

    private static String readUtf8(InputStream inputStream, java.io.PrintStream teeStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        int totalInMemory = 0;
        int memoryLimit = 4 * 1024 * 1024; // 4MB maximum string length in memory
        boolean truncated = false;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            if (totalInMemory < memoryLimit) {
                int toWrite = Math.min(bytesRead, memoryLimit - totalInMemory);
                output.write(buffer, 0, toWrite);
                totalInMemory += toWrite;
                if (totalInMemory >= memoryLimit) {
                    truncated = true;
                }
            }
            if (teeStream != null) {
                teeStream.write(buffer, 0, bytesRead);
                teeStream.flush();
            }
        }
        if (truncated) {
            output.write("\n... [output truncated in memory to prevent OutOfMemoryError]\n".getBytes(StandardCharsets.UTF_8));
        }
        return output.toString(StandardCharsets.UTF_8);
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
}
