package chatmap.cli;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import chatmap.backend.ai.AiBackend;
import chatmap.backend.ai.DefaultAiBackends;
import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.service.PromptResult;
import chatmap.service.PromptService;

/** Executable CLI entry point for running prompts against AI backends and recording chats in SQLite. */
public final class RunPromptCli {

    public static void main(String[] args) {
        try {
            PromptResult result = execute(args, DefaultAiBackends.defaults(), Clock.systemUTC());
            System.out.println("Backend: " + result.backendLabel());
            result.transcript().ifPresent(path -> System.out.println("Transcript: " + path));
            System.out.println("----------------------------------------");
            System.out.println(result.response());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Could not run prompt: " + e.getMessage());
            System.exit(1);
        }
    }

    public static PromptResult execute(String[] args, Map<String, AiBackend> backends, Clock clock) throws Exception {
        ParsedArguments parsedArguments = ChatMapPaths.parse(args);
        List<String> remaining = parsedArguments.remainingArgs();
        if (remaining.size() < 2) {
            throw new IllegalArgumentException("Usage: runPrompt [--home <directory>] <backendId> [--session <id>] <prompt>");
        }

        String backendId = remaining.get(0);
        String sessionId = null;
        String prompt;

        if (remaining.size() >= 4 && "--session".equals(remaining.get(1))) {
            sessionId = remaining.get(2);
            prompt = String.join(" ", remaining.subList(3, remaining.size()));
        } else {
            prompt = String.join(" ", remaining.subList(1, remaining.size()));
        }

        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            PromptService promptService = context.services().promptService();
            if (backends != null && !backends.isEmpty() && !backends.equals(DefaultAiBackends.defaults())) {
                promptService = new PromptService(
                        backends,
                        null,
                        context.services().importService(),
                        clock,
                        context.paths().transcriptsDirectory());
            }

            if (!promptService.hasBackend(backendId)) {
                throw new IllegalArgumentException("Unknown backend '" + backendId + "'. Available backends: "
                        + promptService.backends().stream().map(chatmap.service.BackendDescriptor::id).toList());
            }

            return promptService.submit(backendId, prompt, sessionId);
        }
    }

    private static void printUsage() {
        System.err.println("Usage: runPrompt [--home <directory>] <backendId> [--session <id>] <prompt>");
    }
}
