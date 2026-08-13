package chatmap.application.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.slf4j.Logger;

import chatmap.application.port.ai.AiBackend;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.PromptProfile;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.application.model.ImportedChat;
import chatmap.application.support.Log;

public final class PromptService {
    private static final Logger LOG = Log.of(PromptService.class);
    private final Map<String, AiBackend> backends;
    private final Map<String, String> backendLabels;
    private final ImportService importService;
    private final Clock clock;
    private final Path transcriptDirectory;

    public PromptService(
            Map<String, AiBackend> backends,
            ImportService importService,
            Clock clock,
            Path transcriptDirectory) {
        this(backends, backendIdsAsLabels(backends), importService, clock, transcriptDirectory);
    }

    public PromptService(
            Map<String, AiBackend> backends,
            Map<String, String> backendLabels,
            ImportService importService,
            Clock clock,
            Path transcriptDirectory
    ) {
        this.backends = Map.copyOf(Objects.requireNonNull(backends, "backends"));
        Map<String, String> labels = backendLabels != null ? backendLabels : backendIdsAsLabels(this.backends);
        if (!labels.keySet().containsAll(this.backends.keySet())) {
            throw new IllegalArgumentException("backendLabels must include every backend id");
        }
        this.backendLabels = Map.copyOf(labels);
        this.importService = importService;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transcriptDirectory = Objects.requireNonNull(transcriptDirectory, "transcriptDirectory")
                .toAbsolutePath()
                .normalize();
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

        return backends.get(backendName).listSessions();
    }

    public PromptResult submit(String backendName, String prompt) throws SQLException {
        return submit(backendName, prompt, PromptProfile.general, null);
    }

    public PromptResult submit(String backendName, String prompt, PromptProfile profile) throws SQLException {
        return submit(backendName, prompt, profile, null);
    }

    public PromptResult submit(String backendName, String prompt, String sessionId) throws SQLException {
        return submit(backendName, prompt, PromptProfile.general, sessionId);
    }

    /**
     * Runs the prompt and records the exchange. The database record is
     * authoritative: a failed write fails the whole run. The Markdown
     * transcript is best-effort debug output; when it cannot be written the
     * result carries a null transcript path.
     */
    public PromptResult submit(String backendName, String prompt, PromptProfile profile, String sessionId)
            throws SQLException {
        AiBackend backend = backends.get(backendName);
        if (backend == null) {
            throw new IllegalArgumentException("Unknown backend: " + backendName);
        }

        AiRequest request = (sessionId != null && !sessionId.isBlank())
                ? AiRequest.withSession(prompt, sessionId, profile)
                : AiRequest.withProfile(prompt, profile);
        Instant started = clock.instant();

        AiResponse response = backend.ask(request);
        String responseText = response.text();
        String backendId = response.backendId().value();

        if (importService != null) {
            recordInDatabase(backend, prompt, responseText, started);
        }
        Path transcriptPath = writeLocalTranscript(started, backendId, prompt, responseText);

        return new PromptResult(backendId, responseText, transcriptPath);
    }

    private void recordInDatabase(AiBackend backend, String prompt, String responseText, Instant started)
            throws SQLException {
        String now = started.toString();
        Source source = backend != null ? backend.source() : Source.plainText;
        String title = prompt.length() > 40 ? prompt.substring(0, 40) + "..." : prompt;
        Chat chat = new Chat(0L, null, source, title, now, now, now, false, chatmap.domain.ChatOrigin.generated);
        Message userMsg = new Message(0L, 0L, chatmap.domain.MessageRole.user, prompt, 0, now, null);
        Message assistantMsg = new Message(0L, 0L, chatmap.domain.MessageRole.assistant, responseText, 1, now, null);

        importService.persist(new ImportedChat(chat, List.of(userMsg, assistantMsg)));
    }

    private Path writeLocalTranscript(Instant started, String backendId, String prompt, String responseText) {
        try {
            Files.createDirectories(transcriptDirectory);
            String fn = "prompt-" + started.toEpochMilli() + ".md";
            Path file = transcriptDirectory.resolve(fn);
            String content = "# Prompt Execution Log\n\n- Backend: " + backendId
                    + "\n- Timestamp: " + started + "\n\n## USER:\n" + prompt
                    + "\n\n## ASSISTANT:\n" + responseText + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            // Best-effort debug output; the database record above is the one that matters.
            LOG.warn("Failed to write prompt transcript: {}", e.getMessage(), e);
            return null;
        }
    }

    private static Map<String, String> backendIdsAsLabels(Map<String, AiBackend> backends) {
        Objects.requireNonNull(backends, "backends");
        return backends.keySet().stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> id));
    }
}
