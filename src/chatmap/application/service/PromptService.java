package chatmap.application.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;

import chatmap.application.port.ai.AiCapability;
import chatmap.application.port.ai.AiBackendUnsupportedRequestException;
import chatmap.application.port.ai.AiProvider;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.PromptProfile;
import chatmap.application.port.ai.ProviderId;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.application.model.ImportedChat;
import chatmap.application.support.Log;

public final class PromptService {
    private static final Logger LOG = Log.of(PromptService.class);
    private final Map<ProviderId, AiProvider> providers;
    private final ImportService importService;
    private final Clock clock;
    private final Path transcriptDirectory;

    public PromptService(
            Map<ProviderId, AiProvider> providers,
            ImportService importService,
            Clock clock,
            Path transcriptDirectory) {
        this.providers = validateProviders(providers);
        this.importService = importService;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transcriptDirectory = Objects.requireNonNull(transcriptDirectory, "transcriptDirectory")
                .toAbsolutePath()
                .normalize();
    }

    public boolean hasBackend(String backendName) {
        return Arrays.stream(ModelTarget.values()).anyMatch(target -> target.id().equals(backendName));
    }

    public List<BackendDescriptor> backends() {
        return Arrays.stream(ModelTarget.values())
                .map(target -> new BackendDescriptor(target.id(), target.displayName()))
                .toList();
    }

    public List<String> listSessions(String backendName) {
        ModelTarget target = ModelTarget.require(backendName);
        return providerFor(target).listSessions(target);
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
        ModelTarget target = ModelTarget.require(backendName);
        AiProvider provider = providerFor(target);

        AiRequest request = (sessionId != null && !sessionId.isBlank())
                ? AiRequest.withSession(prompt, sessionId, profile)
                : AiRequest.withProfile(prompt, profile);
        validateCapabilities(target, provider, request);
        Instant started = clock.instant();

        AiResponse response = provider.execute(target, request);
        String responseText = response.text();
        String backendId = response.backendId().value();
        String effectiveSessionId = response.sessionId().orElse(sessionId);

        if (importService != null) {
            recordInDatabase(target, prompt, responseText, started, effectiveSessionId);
        }
        Path transcriptPath = writeLocalTranscript(started, backendId, prompt, responseText);

        return new PromptResult(backendId, responseText, transcriptPath,
                response.providerId().name(), target.id(), response.providerModelName(), effectiveSessionId);
    }

    /**
     * Records the exchange. When {@code sessionId} is present, appends to the
     * chat identified by {@code (providerId, targetId, sessionId)} so repeated
     * turns in one provider session extend a single chat instead of each
     * becoming its own; with no session id, every call still creates a new
     * chat, since there is no provider session to key on.
     */
    private void recordInDatabase(ModelTarget target, String prompt, String responseText, Instant started,
            String sessionId) throws SQLException {
        String now = started.toString();
        String title = prompt.length() > 40 ? prompt.substring(0, 40) + "..." : prompt;
        Chat chat = Chat.builder()
                .id(0L)
                .source(target.source())
                .title(title)
                .createdAt(now)
                .updatedAt(now)
                .importedAt(now)
                .archived(false)
                .originatedBy(chatmap.domain.ChatOrigin.generated)
                .providerId(target.providerId().name())
                .modelTargetId(target.id())
                .providerModelName(target.providerModelName())
                .providerSessionId(sessionId)
                .build();
        Message userMsg = new Message(0L, 0L, chatmap.domain.MessageRole.user, prompt, 0, now, null);
        Message assistantMsg = new Message(0L, 0L, chatmap.domain.MessageRole.assistant, responseText, 1, now, null);
        List<Message> messages = List.of(userMsg, assistantMsg);

        if (sessionId != null && !sessionId.isBlank()) {
            importService.appendToConversation(chat, messages);
        } else {
            importService.persist(new ImportedChat(chat, messages));
        }
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

    private AiProvider providerFor(ModelTarget target) {
        AiProvider provider = providers.get(target.providerId());
        if (provider == null) {
            throw new IllegalStateException("No AI provider configured for " + target.providerId());
        }
        return provider;
    }

    private static Map<ProviderId, AiProvider> validateProviders(Map<ProviderId, AiProvider> configured) {
        Objects.requireNonNull(configured, "providers");
        EnumMap<ProviderId, AiProvider> copy = new EnumMap<>(ProviderId.class);
        copy.putAll(configured);
        for (ModelTarget target : ModelTarget.values()) {
            if (!copy.containsKey(target.providerId())) {
                throw new IllegalStateException("No AI provider configured for target "
                        + target.id() + " (" + target.providerId() + ")");
            }
        }
        return Map.copyOf(copy);
    }

    private static void validateCapabilities(ModelTarget target, AiProvider provider, AiRequest request) {
        Set<AiCapability> required = EnumSet.noneOf(AiCapability.class);
        if (request.systemPrompt().isPresent()) {
            required.add(AiCapability.systemPrompt);
        }
        if (request.sessionId().isPresent()) {
            required.add(AiCapability.sessions);
        }
        if (request.permissionMode() == chatmap.application.port.ai.PermissionMode.unrestricted) {
            required.add(AiCapability.fileEditing);
        }
        if (request.outputFormat() == chatmap.application.port.ai.OutputFormat.streamJson) {
            required.add(AiCapability.streamJson);
        }
        Set<AiCapability> supported = provider.capabilities(target);
        for (AiCapability capability : required) {
            if (!supported.contains(capability)) {
                throw new AiBackendUnsupportedRequestException(
                        target.displayName() + " does not support requested capability: " + capability,
                        new BackendId(target.displayName()));
            }
        }
    }
}
