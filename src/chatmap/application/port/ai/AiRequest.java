package chatmap.application.port.ai;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A prompt plus the small set of provider-agnostic capabilities a backend's
 * {@code commandFor()} may interpret in its own CLI's flag names --
 * {@code workingDirectory}, {@code permissionMode}, and {@code outputFormat}
 * exist so callers with unusual needs (e.g. running inside an isolated
 * worktree, skipping permission prompts) go through {@link AiBackend}
 * instead of building a command themselves.
 */
public record AiRequest(
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
    public AiRequest {
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

    public AiRequest(String prompt, Optional<String> systemPrompt, PromptProfile profile, Optional<String> sessionId) {
        this(prompt, systemPrompt, profile, sessionId, Optional.empty(), PermissionMode.standard, OutputFormat.text,
                Optional.empty(), Optional.empty());
    }

    public AiRequest(String prompt, Optional<String> systemPrompt, PromptProfile profile) {
        this(prompt, systemPrompt, profile, Optional.empty());
    }

    public AiRequest(String prompt, Optional<String> systemPrompt) {
        this(prompt, systemPrompt, PromptProfile.general, Optional.empty());
    }

    public static AiRequest of(String prompt) {
        return new AiRequest(prompt, Optional.empty(), PromptProfile.general, Optional.empty());
    }

    public static AiRequest withProfile(String prompt, PromptProfile profile) {
        return new AiRequest(prompt, Optional.empty(), profile, Optional.empty());
    }

    public static AiRequest withSession(String prompt, String sessionId) {
        return new AiRequest(prompt, Optional.empty(), PromptProfile.general, Optional.ofNullable(sessionId));
    }

    public static AiRequest withSession(String prompt, String sessionId, PromptProfile profile) {
        return new AiRequest(prompt, Optional.empty(), profile, Optional.ofNullable(sessionId));
    }

    public static AiRequest withSystemPrompt(String prompt, String systemPrompt) {
        return new AiRequest(prompt, Optional.of(systemPrompt), PromptProfile.general, Optional.empty());
    }

    public String effectivePrompt() {
        return profile.applyTo(prompt);
    }

    /** Copy requesting the backend's CLI be invoked inside {@code workingDirectory}. */
    public AiRequest withWorkingDirectory(Path workingDirectory) {
        return new AiRequest(prompt, systemPrompt, profile, sessionId, Optional.ofNullable(workingDirectory),
                permissionMode, outputFormat, standardOutputPath, standardErrorPath);
    }

    /** Copy requesting a different permission mode from the backend. */
    public AiRequest withPermissionMode(PermissionMode permissionMode) {
        return new AiRequest(prompt, systemPrompt, profile, sessionId, workingDirectory,
                Objects.requireNonNull(permissionMode, "permissionMode"), outputFormat,
                standardOutputPath, standardErrorPath);
    }

    /** Copy requesting a different output format from the backend. */
    public AiRequest withOutputFormat(OutputFormat outputFormat) {
        return new AiRequest(prompt, systemPrompt, profile, sessionId, workingDirectory,
                permissionMode, Objects.requireNonNull(outputFormat, "outputFormat"),
                standardOutputPath, standardErrorPath);
    }

    /** Copy requesting complete stdout/stderr be written to durable paths by command-backed providers. */
    public AiRequest withOutputPaths(Path standardOutputPath, Path standardErrorPath) {
        return new AiRequest(prompt, systemPrompt, profile, sessionId, workingDirectory,
                permissionMode, outputFormat, Optional.ofNullable(standardOutputPath),
                Optional.ofNullable(standardErrorPath));
    }
}
