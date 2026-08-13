package chatmap.presentation.cli;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import chatmap.application.port.ai.AiBackend;
import chatmap.app.DefaultServiceIntegrations;
import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.application.service.PromptResult;
import chatmap.application.service.PromptService;

/** Executable CLI entry point for running prompts against AI backends and recording chats in SQLite. */
public final class RunPromptCli {

    public static void main(String[] args) {
        try {
            ParsedArguments parsedArguments = CliBootstrap.parse(args);
            RunPromptArguments promptArguments = parsePromptArguments(parsedArguments);
            PromptResult result = execute(
                    parsedArguments, promptArguments, DefaultServiceIntegrations.promptBackends(), Clock.systemUTC());
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
        ParsedArguments parsedArguments = CliBootstrap.parse(args);
        return execute(parsedArguments, parsePromptArguments(parsedArguments), backends, clock);
    }

    private static PromptResult execute(
            ParsedArguments parsedArguments,
            RunPromptArguments promptArguments,
            Map<String, AiBackend> backends,
            Clock clock) throws Exception {
        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            PromptService promptService = new PromptService(
                    backends,
                    context.services().importService(),
                    clock,
                    context.paths().transcriptsDirectory());

            if (!promptService.hasBackend(promptArguments.backendId())) {
                throw new IllegalArgumentException("Unknown backend '" + promptArguments.backendId()
                        + "'. Available backends: "
                        + promptService.backends().stream().map(chatmap.application.service.BackendDescriptor::id).toList());
            }

            return promptService.submit(
                    promptArguments.backendId(), promptArguments.prompt(), promptArguments.sessionId());
        }
    }

    private static RunPromptArguments parsePromptArguments(ParsedArguments parsedArguments) {
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
        return new RunPromptArguments(backendId, sessionId, prompt);
    }

    private static void printUsage() {
        System.err.println("Usage: runPrompt [--home <directory>] <backendId> [--session <id>] <prompt>");
    }

    private record RunPromptArguments(String backendId, String sessionId, String prompt) {
    }
}
