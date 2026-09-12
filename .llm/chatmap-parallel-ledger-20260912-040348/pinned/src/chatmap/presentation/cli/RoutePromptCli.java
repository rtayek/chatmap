package chatmap.presentation.cli;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import chatmap.app.DefaultServiceIntegrations;
import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.application.port.llm.Channel;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.service.ConversationContext;
import chatmap.application.service.DeterministicPromptClassifier;
import chatmap.application.service.ProjectContext;
import chatmap.application.service.PromptRouteSelector;
import chatmap.application.service.PromptRouterService;
import chatmap.application.service.PromptRoutingResult;
import chatmap.application.service.PromptService;

/** CLI entry point for classifying, routing, executing, and recording a prompt. */
public final class RoutePromptCli {

    private static final String USAGE = "Usage: routePrompt [--home <directory>] --project <name> "
            + "[--conversation <id>] <prompt>";

    private RoutePromptCli() {
    }

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, USAGE);
        RoutePromptArguments routeArguments;
        try {
            routeArguments = parseRouteArguments(parsedArguments);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            CliBootstrap.exitWithUsage(USAGE);
            return;
        }
        try {
            PromptRoutingResult result = execute(
                    parsedArguments,
                    routeArguments,
                    DefaultServiceIntegrations.promptProviders(),
                    Clock.systemUTC());
            print(result);
        } catch (Exception e) {
            System.err.println("Could not route prompt: " + e.getMessage());
            System.exit(1);
        }
    }

    public static PromptRoutingResult execute(String[] args, Map<Channel, LlmProvider> providers, Clock clock)
            throws Exception {
        ParsedArguments parsedArguments = CliBootstrap.parse(args);
        return execute(parsedArguments, parseRouteArguments(parsedArguments), providers, clock);
    }

    private static PromptRoutingResult execute(
            ParsedArguments parsedArguments,
            RoutePromptArguments routeArguments,
            Map<Channel, LlmProvider> providers,
            Clock clock) throws Exception {
        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            PromptService promptService = new PromptService(
                    providers,
                    context.services().importService(),
                    clock,
                    context.paths().transcriptsDirectory());
            PromptRouterService router = new PromptRouterService(
                    new DeterministicPromptClassifier(),
                    PromptRouteSelector.defaults(),
                    promptService,
                    context.services().projectService(),
                    context.services().promptRoutes(),
                    clock);
            return router.route(
                    ProjectContext.of(routeArguments.project(), null),
                    new ConversationContext(routeArguments.conversation()),
                    routeArguments.prompt());
        }
    }

    private static RoutePromptArguments parseRouteArguments(ParsedArguments parsedArguments) {
        List<String> remaining = parsedArguments.remainingArgs();
        String project = null;
        String conversation = null;
        int index = 0;
        while (index < remaining.size()) {
            String arg = remaining.get(index);
            if ("--project".equals(arg)) {
                project = readOptionValue(remaining, index, "--project");
                index += 2;
            } else if ("--conversation".equals(arg)) {
                conversation = readOptionValue(remaining, index, "--conversation");
                index += 2;
            } else {
                break;
            }
        }
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("Expected --project <name>.");
        }
        if (index >= remaining.size()) {
            throw new IllegalArgumentException("Expected a prompt.");
        }
        String prompt = String.join(" ", remaining.subList(index, remaining.size()));
        String actualConversation = conversation == null || conversation.isBlank()
                ? project + "-current-task"
                : conversation;
        return new RoutePromptArguments(project, actualConversation, prompt);
    }

    private static String readOptionValue(List<String> args, int index, String option) {
        int valueIndex = index + 1;
        if (valueIndex >= args.size() || args.get(valueIndex).isBlank()) {
            throw new IllegalArgumentException("Expected value after " + option + ".");
        }
        return args.get(valueIndex);
    }

    private static void print(PromptRoutingResult result) {
        System.out.println("Project: " + result.projectContext().workingProjectIdentity());
        System.out.println("Conversation: " + result.conversationContext().id());
        System.out.println("Classification: " + result.classification().level());
        System.out.println("Confidence: " + result.classification().confidence());
        System.out.println("Reasons: " + result.classification().reasons().stream()
                .map(chatmap.domain.PromptClassificationReason::code)
                .toList());
        System.out.println("Provider: " + result.route().target().channel().name());
        System.out.println("Target: " + result.route().target().id());
        System.out.println("Model: " + result.promptResult().providerModelName());
        result.promptResult().sessionId().ifPresent(session -> System.out.println("Session: " + session));
        result.promptResult().transcript().ifPresent(path -> System.out.println("Transcript: " + path));
        System.out.println("----------------------------------------");
        System.out.println(result.promptResult().response());
    }

    private record RoutePromptArguments(String project, String conversation, String prompt) {
    }
}
