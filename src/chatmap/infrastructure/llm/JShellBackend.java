package chatmap.infrastructure.llm;

import chatmap.application.port.llm.LlmBackend;
import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmBackendStartupException;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.ModelTarget;

import chatmap.domain.Source;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * JShell REPL Backend embedding jdk.jshell to execute Java code snippets.
 * Acts as a programmable execution harness for LLMs.
 */
public class JShellBackend implements LlmBackend, LlmProvider {

    public static final BackendId BACKEND_ID = new BackendId("JShell Harness");

    private static final String DEFAULT_SYSTEM_PROMPT = """
            [JShell Harness Environment]
            You are operating inside a Java 21 JShell (REPL) environment for ChatMap.
            Write valid Java statements and expressions directly.
            Available ChatMap packages:
              - chatmap.domain.*
              - chatmap.infrastructure.persistence.sqlite.*
              - chatmap.application.service.*
            """;

    private final Source source;

    public JShellBackend() {
        this(Source.jshellHarness);
    }

    public JShellBackend(Source source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public BackendId backendId() {
        return BACKEND_ID;
    }

    @Override
    public Source source() {
        return source;
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        return execute(ModelTarget.jshell, request);
    }

    @Override
    public Set<LlmCapability> capabilities(ModelTarget target) {
        return Set.of();
    }

    @Override
    public LlmResponse execute(ModelTarget target, LlmRequest request) {
        Objects.requireNonNull(request, "request");
        Instant start = Instant.now();

        String rawPrompt = request.effectivePrompt();
        String codeToRun = extractCode(rawPrompt);

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();

        StringBuilder resultBuilder = new StringBuilder();

        try (PrintStream outPrint = new PrintStream(outStream, true, StandardCharsets.UTF_8);
             PrintStream errPrint = new PrintStream(errStream, true, StandardCharsets.UTF_8);
             JShell jshell = JShell.builder()
                     .out(outPrint)
                     .err(errPrint)
                     .build()) {

            String classPath = System.getProperty("java.class.path");
            if (classPath != null && !classPath.isBlank()) {
                for (String pathEntry : classPath.split(File.pathSeparator)) {
                    if (!pathEntry.isBlank()) {
                        jshell.addToClasspath(pathEntry);
                    }
                }
            }

            jshell.eval("import chatmap.domain.*;");
            jshell.eval("import chatmap.infrastructure.persistence.sqlite.*;");
            jshell.eval("import chatmap.application.service.*;");
            jshell.eval("import java.util.*;");
            jshell.eval("import java.io.*;");
            jshell.eval("import java.nio.file.*;");

            List<SnippetEvent> events = jshell.eval(codeToRun);

            String stdout = outStream.toString(StandardCharsets.UTF_8);
            String stderr = errStream.toString(StandardCharsets.UTF_8);

            if (!stdout.isEmpty()) {
                resultBuilder.append(stdout);
            }

            for (SnippetEvent event : events) {
                if (event.exception() != null) {
                    if (resultBuilder.length() > 0 && resultBuilder.charAt(resultBuilder.length() - 1) != '\n') {
                        resultBuilder.append('\n');
                    }
                    resultBuilder.append("Exception: ").append(event.exception().getMessage());
                } else if (event.status() == Snippet.Status.REJECTED) {
                    jshell.diagnostics(event.snippet()).forEach(diag -> {
                        if (resultBuilder.length() > 0 && resultBuilder.charAt(resultBuilder.length() - 1) != '\n') {
                            resultBuilder.append('\n');
                        }
                        resultBuilder.append("Compile Error: ").append(diag.getMessage(Locale.ROOT));
                    });
                } else if (event.value() != null && !event.value().isEmpty() && stdout.isEmpty()) {
                    if (resultBuilder.length() > 0 && resultBuilder.charAt(resultBuilder.length() - 1) != '\n') {
                        resultBuilder.append('\n');
                    }
                    resultBuilder.append(event.value());
                }
            }

            if (resultBuilder.length() == 0 && !stderr.isEmpty()) {
                resultBuilder.append(stderr);
            }
        } catch (RuntimeException e) {
            throw new LlmBackendStartupException(
                    "JShell execution failed: " + e.getMessage(), BACKEND_ID, e);
        }

        Duration duration = Duration.between(start, Instant.now());
        return new LlmResponse(resultBuilder.toString().trim(), BACKEND_ID, duration, target,
                request.sessionId().orElse(null));
    }

    public static String buildSystemPrompt() {
        return DEFAULT_SYSTEM_PROMPT;
    }

    private static String extractCode(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        if (prompt.contains("```")) {
            int start = prompt.indexOf("```");
            int end = prompt.lastIndexOf("```");
            if (start < end) {
                String codeBlock = prompt.substring(start + 3, end).trim();
                if (codeBlock.startsWith("java")) {
                    codeBlock = codeBlock.substring(4).trim();
                }
                return codeBlock;
            }
        }
        return prompt;
    }
}
