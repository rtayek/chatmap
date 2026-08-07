package chatmap.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import chatmap.backend.AiBackend;
import chatmap.backend.AiBackendException;
import chatmap.backend.AiRequest;
import chatmap.backend.AiResponse;
import chatmap.backend.CommandBackedAiBackend;
import chatmap.backend.CommandBackedRun;
import chatmap.backend.CommandRequest;
import chatmap.backend.CommandResult;
import chatmap.backend.CommandRunner;
import chatmap.backend.PromptProfile;
import chatmap.config.ChatMapPaths;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

public final class PromptService {
    private final Map<String, AiBackend> backends;
    private final Map<String, String> backendLabels;
    private final ImportService importService;
    private final Clock clock;

    public PromptService(Map<String, AiBackend> backends, ImportService importService, Clock clock) {
        this(backends, backendIdsAsLabels(backends), importService, clock);
    }

    public PromptService(
            Map<String, AiBackend> backends,
            Map<String, String> backendLabels,
            ImportService importService,
            Clock clock
    ) {
        this.backends = Map.copyOf(Objects.requireNonNull(backends, "backends"));
        this.backendLabels = Map.copyOf(Objects.requireNonNull(backendLabels, "backendLabels"));
        if (!this.backendLabels.keySet().containsAll(this.backends.keySet())) {
            throw new IllegalArgumentException("backendLabels must include every backend id");
        }
        this.importService = importService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean hasBackend(String backendName) {
        return backends.containsKey(backendName);
    }

    public List<BackendDescriptor> backends() {
        return new TreeMap<>(backendLabels).entrySet().stream()
                .filter(entry -> backends.containsKey(entry.getKey()))
                .map(entry -> new BackendDescriptor(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<String> listSessions(String backendName) {
        if (!hasBackend(backendName)) {
            throw new IllegalArgumentException("Unknown backend: " + backendName);
        }

        List<String> sessions = new ArrayList<>();
        String binaryName = switch (backendName.toLowerCase(java.util.Locale.ROOT)) {
            case "claude" -> "claude";
            case "codex" -> "codex";
            case "agy" -> "agy";
            default -> null;
        };

        if (binaryName != null) {
            try {
                CommandResult result = new CommandRunner().run(
                        new CommandRequest(List.of(binaryName, "--list-sessions"), "", Duration.ofSeconds(10))
                );
                if (result.exitCode() == 0 && !result.standardOutput().isBlank()) {
                    for (String line : result.standardOutput().split("\\r?\\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !sessions.contains(trimmed)) {
                            sessions.add(trimmed);
                        }
                    }
                }
            } catch (Exception ignored) {
                // CLI session discovery optional
            }
        }

        return List.copyOf(sessions);
    }

    public PromptResult submit(String backendName, String prompt) {
        return submit(backendName, prompt, PromptProfile.GENERAL, null);
    }

    public PromptResult submit(String backendName, String prompt, PromptProfile profile) {
        return submit(backendName, prompt, profile, null);
    }

    public PromptResult submit(String backendName, String prompt, String sessionId) {
        return submit(backendName, prompt, PromptProfile.GENERAL, sessionId);
    }

    public PromptResult submit(String backendName, String prompt, PromptProfile profile, String sessionId) {
        AiBackend backend = backends.get(backendName);
        if (backend == null) {
            throw new IllegalArgumentException("Unknown backend: " + backendName);
        }

        AiRequest request = (sessionId != null && !sessionId.isBlank())
                ? AiRequest.withSession(prompt, sessionId, profile)
                : AiRequest.withProfile(prompt, profile);
        Instant started = clock.instant();

        try {
            String responseText;
            String backendId;
            if (backend instanceof CommandBackedAiBackend commandBackedBackend) {
                CommandBackedRun run = commandBackedBackend.askWithResult(request);
                responseText = run.response().text();
                backendId = run.response().backendId().value();
            } else {
                AiResponse response = backend.ask(request);
                responseText = response.text();
                backendId = response.backendId().value();
            }

            Path transcriptPath = writeLocalTranscript(started, backendId, prompt, responseText);
            if (importService != null) {
                recordInDatabase(backendId, prompt, responseText, started);
            }

            return new PromptResult(backendId, responseText, transcriptPath);
        } catch (AiBackendException exception) {
            throw exception;
        }
    }

    private void recordInDatabase(String backendId, String prompt, String responseText, Instant started) {
        try {
            String now = started.toString();
            Source source = parseSource(backendId);
            String title = prompt.length() > 40 ? prompt.substring(0, 40) + "..." : prompt;
            Chat chat = new Chat(0L, null, source, title, now, now, now, false);
            Message userMsg = new Message(0L, 0L, "user", prompt, 0, now, null);
            Message assistantMsg = new Message(0L, 0L, "assistant", responseText, 1, now, null);

            importService.persist(new ImportedChat(chat, List.of(userMsg, assistantMsg)));
        } catch (SQLException e) {
            // Don't fail the prompt run over a recording failure, but don't hide it either.
            System.err.println("[ChatMap] Failed to record prompt in database: " + e.getMessage());
        }
    }

    private static Source parseSource(String backendId) {
        String lower = backendId.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("claude")) {
            return Source.claudeCode;
        }
        if (lower.contains("codex")) {
            return Source.codexCli;
        }
        if (lower.contains("gemini")) {
            return Source.geminiCli;
        }
        return Source.plainText;
    }

    private static Path writeLocalTranscript(Instant started, String backendId, String prompt, String responseText) {
        try {
            Path dir = ChatMapPaths.homeDirectory().resolve("transcripts");
            Files.createDirectories(dir);
            String fn = "prompt-" + started.toEpochMilli() + ".md";
            Path file = dir.resolve(fn);
            String content = "# Prompt Execution Log\n\n- Backend: " + backendId
                    + "\n- Timestamp: " + started + "\n\n## USER:\n" + prompt
                    + "\n\n## ASSISTANT:\n" + responseText + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            System.err.println("[ChatMap] Failed to write prompt transcript: " + e.getMessage());
            return Path.of("transcript.md");
        }
    }

    private static Map<String, String> backendIdsAsLabels(Map<String, AiBackend> backends) {
        Objects.requireNonNull(backends, "backends");
        return backends.keySet().stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> id));
    }
}
