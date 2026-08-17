package chatmap.presentation.cli;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.ProviderId;
import chatmap.app.DefaultServiceIntegrations;
import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.application.service.PromptResult;
import chatmap.application.service.PromptService;

/** Executable CLI entry point for running prompts against LLM backends and recording chats in SQLite. */
public final class RunPromptCli {

    private static final String USAGE = "Usage: runPrompt [--home <directory>] <backendId> [--session <id>] <prompt>";

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, USAGE);
        RunPromptArguments promptArguments;
        try {
            promptArguments = parsePromptArguments(parsedArguments);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            CliBootstrap.exitWithUsage(USAGE);
            return;
        }
        try {
            PromptResult result = execute(
                    parsedArguments, promptArguments, DefaultServiceIntegrations.promptProviders(), Clock.systemUTC());
            System.out.println("Backend: " + result.backendLabel());
            System.out.println("Provider: " + result.providerId());
            System.out.println("Target: " + result.targetId());
            System.out.println("Model: " + result.providerModelName());
            result.sessionId().ifPresent(session -> System.out.println("Session: " + session));
            result.transcript().ifPresent(path -> System.out.println("Transcript: " + path));
            System.out.println("----------------------------------------");
            System.out.println(result.response());
        } catch (Exception e) {
            System.err.println("Could not run prompt: " + e.getMessage());
            System.exit(1);
        }
    }

    public static PromptResult execute(String[] args, Map<ProviderId, LlmProvider> providers, Clock clock)
            throws Exception {
        ParsedArguments parsedArguments = CliBootstrap.parse(args);
        return execute(parsedArguments, parsePromptArguments(parsedArguments), providers, clock);
    }

    private static PromptResult execute(
            ParsedArguments parsedArguments,
            RunPromptArguments promptArguments,
            Map<ProviderId, LlmProvider> providers,
            Clock clock) throws Exception {
        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            PromptService promptService = new PromptService(
                    providers,
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
            throw new IllegalArgumentException("Expected a backend id and a prompt.");
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

    private record RunPromptArguments(String backendId, String sessionId, String prompt) {
    }
}
