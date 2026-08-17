package chatmap.application.port.llm;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A prompt plus the small set of provider-agnostic capabilities a backend's
 * {@code commandFor()} may interpret in its own CLI's flag names --
 * {@code workingDirectory}, {@code permissionMode}, and {@code outputFormat}
 * exist so callers with unusual needs (e.g. running inside an isolated
 * worktree, skipping permission prompts) go through {@link LlmProvider}
 * instead of building a command themselves.
 */
public record LlmRequest(
        String prompt,
        Optional<String> systemPrompt,
        PromptProfile profile,
        Optional<String> sessionId,
        Optional<Path> workingDirectory,
        PermissionMode permissionMode,
        OutputFormat outputFormat,
        Optional<Path> standardOutputPath,
        Optional<Path> standardErrorPath
) {
    public LlmRequest {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(permissionMode, "permissionMode");
        Objects.requireNonNull(outputFormat, "outputFormat");
        Objects.requireNonNull(standardOutputPath, "standardOutputPath");
        Objects.requireNonNull(standardErrorPath, "standardErrorPath");
    }

    public LlmRequest(String prompt, Optional<String> systemPrompt, PromptProfile profile, Optional<String> sessionId) {
        this(prompt, systemPrompt, profile, sessionId, Optional.empty(), PermissionMode.standard, OutputFormat.text,
                Optional.empty(), Optional.empty());
    }

    public LlmRequest(String prompt, Optional<String> systemPrompt, PromptProfile profile) {
        this(prompt, systemPrompt, profile, Optional.empty());
    }

    public LlmRequest(String prompt, Optional<String> systemPrompt) {
        this(prompt, systemPrompt, PromptProfile.general, Optional.empty());
    }

    public static LlmRequest of(String prompt) {
        return new LlmRequest(prompt, Optional.empty(), PromptProfile.general, Optional.empty());
    }

    public static LlmRequest withProfile(String prompt, PromptProfile profile) {
        return new LlmRequest(prompt, Optional.empty(), profile, Optional.empty());
    }

    public static LlmRequest withSession(String prompt, String sessionId) {
        return new LlmRequest(prompt, Optional.empty(), PromptProfile.general, Optional.ofNullable(sessionId));
    }

    public static LlmRequest withSession(String prompt, String sessionId, PromptProfile profile) {
        return new LlmRequest(prompt, Optional.empty(), profile, Optional.ofNullable(sessionId));
    }

    public static LlmRequest withSystemPrompt(String prompt, String systemPrompt) {
        return new LlmRequest(prompt, Optional.of(systemPrompt), PromptProfile.general, Optional.empty());
    }

    public String effectivePrompt() {
        return profile.applyTo(prompt);
    }

    /** Copy requesting the backend's CLI be invoked inside {@code workingDirectory}. */
    public LlmRequest withWorkingDirectory(Path workingDirectory) {
        return new LlmRequest(prompt, systemPrompt, profile, sessionId, Optional.ofNullable(workingDirectory),
                permissionMode, outputFormat, standardOutputPath, standardErrorPath);
    }

    /** Copy requesting a different permission mode from the backend. */
    public LlmRequest withPermissionMode(PermissionMode permissionMode) {
        return new LlmRequest(prompt, systemPrompt, profile, sessionId, workingDirectory,
                Objects.requireNonNull(permissionMode, "permissionMode"), outputFormat,
                standardOutputPath, standardErrorPath);
    }

    /** Copy requesting a different output format from the backend. */
    public LlmRequest withOutputFormat(OutputFormat outputFormat) {
        return new LlmRequest(prompt, systemPrompt, profile, sessionId, workingDirectory,
                permissionMode, Objects.requireNonNull(outputFormat, "outputFormat"),
                standardOutputPath, standardErrorPath);
    }

    /** Copy requesting complete stdout/stderr be written to durable paths by command-backed providers. */
    public LlmRequest withOutputPaths(Path standardOutputPath, Path standardErrorPath) {
        return new LlmRequest(prompt, systemPrompt, profile, sessionId, workingDirectory,
                permissionMode, outputFormat, Optional.ofNullable(standardOutputPath),
                Optional.ofNullable(standardErrorPath));
    }
}
