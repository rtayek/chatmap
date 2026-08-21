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

import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmBackendUnsupportedRequestException;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.PromptProfile;
import chatmap.application.port.llm.Channel;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.application.model.ImportedChat;
import chatmap.application.support.Log;

public final class PromptService {
    private static final Logger LOG = Log.of(PromptService.class);
    private final Map<Channel, LlmProvider> providers;
    private final ImportService importService;
    private final Clock clock;
    private final Path transcriptDirectory;

    public PromptService(
            Map<Channel, LlmProvider> providers,
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
        if (importService != null) {
            try {
                return importService.listPromptSessions(target);
            } catch (SQLException failure) {
                throw new IllegalStateException("Could not list known sessions for " + target.displayName(), failure);
            }
        }
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
        return submit(backendName, prompt, profile, sessionId, null);
    }

    public PromptResult submitForProject(String backendName, String prompt, long projectId) throws SQLException {
        return submit(backendName, prompt, PromptProfile.general, null, projectId);
    }

    public PromptResult submitForProject(String backendName, String prompt, Long projectId, String sessionId)
            throws SQLException {
        return submit(backendName, prompt, PromptProfile.general, sessionId, projectId);
    }

    private PromptResult submit(String backendName, String prompt, PromptProfile profile, String sessionId,
            Long projectId) throws SQLException {
        ModelTarget target = ModelTarget.require(backendName);
        LlmProvider provider = providerFor(target);

        LlmRequest request = (sessionId != null && !sessionId.isBlank())
                ? LlmRequest.withSession(prompt, sessionId, profile)
                : LlmRequest.withProfile(prompt, profile);
        validateCapabilities(target, provider, request);
        Instant started = clock.instant();

        LlmResponse response = provider.execute(target, request);
        String responseText = response.text();
        String backendId = response.backendId().value();
        String effectiveSessionId = response.sessionId().orElse(sessionId);

        long chatId = 0;
        if (importService != null) {
            chatId = recordInDatabase(target, prompt, responseText, started, effectiveSessionId, projectId);
        }
        Path transcriptPath = writeLocalTranscript(started, backendId, prompt, responseText);

        return new PromptResult(backendId, responseText, transcriptPath,
                response.channel().name(), target.id(), response.providerModelName(), effectiveSessionId, chatId);
    }

    /**
     * Records the exchange. When {@code sessionId} is present, appends to the
     * chat identified by {@code (providerId, targetId, sessionId)} so repeated
     * turns in one provider session extend a single chat instead of each
     * becoming its own; with no session id, every call still creates a new
     * chat, since there is no provider session to key on.
     */
    private long recordInDatabase(ModelTarget target, String prompt, String responseText, Instant started,
            String sessionId, Long projectId) throws SQLException {
        String now = started.toString();
        String title = prompt.length() > 40 ? prompt.substring(0, 40) + "..." : prompt;
        Chat chat = Chat.builder()
                .id(0L)
                .projectId(projectId)
                .source(target.source())
                .title(title)
                .createdAt(now)
                .updatedAt(now)
                .importedAt(now)
                .archived(false)
                .originatedBy(chatmap.domain.ChatOrigin.generated)
                .channelId(target.channel().name())
                .modelTargetId(target.id())
                .providerModelName(target.providerModelName().orElse(null))
                .providerSessionId(sessionId)
                .build();
        Message userMsg = new Message(0L, 0L, chatmap.domain.MessageRole.user, prompt, 0, now, null);
        Message assistantMsg = new Message(0L, 0L, chatmap.domain.MessageRole.assistant, responseText, 1, now, null);
        List<Message> messages = List.of(userMsg, assistantMsg);

        ImportService.PersistResult result = (sessionId != null && !sessionId.isBlank())
                ? importService.appendToConversation(chat, messages)
                : importService.persist(new ImportedChat(chat, messages));
        return result.chat().id();
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

    private LlmProvider providerFor(ModelTarget target) {
        LlmProvider provider = providers.get(target.providerId());
        if (provider == null) {
            throw new IllegalStateException("No LLM provider configured for " + target.providerId());
        }
        return provider;
    }

    private static Map<Channel, LlmProvider> validateProviders(Map<Channel, LlmProvider> configured) {
        Objects.requireNonNull(configured, "providers");
        EnumMap<Channel, LlmProvider> copy = new EnumMap<>(Channel.class);
        copy.putAll(configured);
        for (ModelTarget target : ModelTarget.values()) {
            if (!copy.containsKey(target.providerId())) {
                throw new IllegalStateException("No LLM provider configured for target "
                        + target.id() + " (" + target.providerId() + ")");
            }
        }
        return Map.copyOf(copy);
    }

    private static void validateCapabilities(ModelTarget target, LlmProvider provider, LlmRequest request) {
        Set<LlmCapability> required = EnumSet.noneOf(LlmCapability.class);
        if (request.systemPrompt().isPresent()) {
            required.add(LlmCapability.systemPrompt);
        }
        if (request.sessionId().isPresent()) {
            required.add(LlmCapability.sessions);
        }
        if (request.permissionMode() == chatmap.application.port.llm.PermissionMode.unrestricted) {
            required.add(LlmCapability.fileEditing);
        }
        if (request.outputFormat() == chatmap.application.port.llm.OutputFormat.streamJson) {
            required.add(LlmCapability.streamJson);
        }
        Set<LlmCapability> supported = provider.capabilities(target);
        for (LlmCapability capability : required) {
            if (!supported.contains(capability)) {
                throw new LlmBackendUnsupportedRequestException(
                        target.displayName() + " does not support requested capability: " + capability,
                        new BackendId(target.displayName()));
            }
        }
    }
}
