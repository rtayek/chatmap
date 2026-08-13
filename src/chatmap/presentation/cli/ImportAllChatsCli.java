package chatmap.presentation.cli;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import chatmap.application.port.provider.ChatProvider;
import chatmap.application.port.provider.ChatProviderException;
import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.provider.NoImportableContentException;
import chatmap.app.DefaultServiceIntegrations;
import chatmap.app.bootstrap.ChatMapPaths.ParsedArguments;
import chatmap.domain.ConversationCandidate;
import chatmap.application.model.ImportedChat;
import chatmap.application.service.ImportService;
import chatmap.app.ServiceGraph;

/**
 * Imports every chat discoverable from the configured providers (all six by
 * default: three CLI-history sources, three live web sources over CDP) and
 * persists each one that isn't already imported.
 *
 * Unlike {@code ConversationInventoryCli} (read-only discovery) this actually
 * fetches and persists. Already-imported conversations are skipped by
 * checking external identity first, so a re-run doesn't re-fetch chats that
 * are already stored — this matters most for the web providers, where a
 * fetch is a real CDP round trip, not just a file read.
 */
public final class ImportAllChatsCli {

    private static final String USAGE =
            "Usage: importAllChats [--home <directory>] [--source <name>]\n"
            + "  --source <name>   Only import from the provider whose name() matches "
            + "(case-insensitive, e.g. \"Claude Code\", \"ChatGPT\"). Default: all configured providers.";

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, USAGE);
        List<String> remaining = parsedArguments.remainingArgs();

        String sourceFilter = null;
        if (!remaining.isEmpty()) {
            if (remaining.size() != 2 || !remaining.get(0).equals("--source")) {
                CliBootstrap.exitWithUsage(USAGE);
                return;
            }
            sourceFilter = remaining.get(1);
        }

        List<ChatProvider> providers = DefaultServiceIntegrations.chatProviders();
        if (sourceFilter != null) {
            String wanted = sourceFilter;
            providers = providers.stream()
                    .filter(p -> p.name().equalsIgnoreCase(wanted))
                    .toList();
            if (providers.isEmpty()) {
                System.err.println("No configured provider named \"" + sourceFilter + "\".");
                System.err.println("Configured providers: "
                        + DefaultServiceIntegrations.chatProviders().stream().map(ChatProvider::name).toList());
                System.exit(1);
                return;
            }
        }

        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            ServiceGraph services = context.services();
            int totalInserted = 0;
            int totalUpdated = 0;
            int totalSkipped = 0;
            int totalNoContent = 0;
            int totalFailed = 0;

            for (ChatProvider provider : providers) {
                System.out.println();
                System.out.println("== " + provider.name() + " ==");

                List<ConversationCandidate> candidates;
                try {
                    candidates = provider.listChats();
                } catch (ChatProviderException unavailable) {
                    System.out.println("  unavailable: " + unavailable.getMessage());
                    continue;
                }
                boolean providerComplete = provider.inventoryComplete();
                String providerDiagnostic = provider.inventoryDiagnostic().orElse(null);

                if (candidates.isEmpty()) {
                    System.out.println("  no chats found");
                    printCompleteness(providerComplete, providerDiagnostic);
                    continue;
                }

                Map<String, Long> alreadyImported;
                try {
                    alreadyImported = services.chats().findImportedIdsByExternalIdentity(candidates);
                } catch (SQLException e) {
                    System.out.println("  failed to check already-imported chats: " + e.getMessage());
                    continue;
                }

                int inserted = 0;
                int updated = 0;
                int skipped = 0;
                int noContent = 0;
                int failed = 0;

                for (ConversationCandidate candidate : candidates) {
                    boolean knownImported = candidate.externalConversationId() != null
                            && !candidate.externalConversationId().isBlank()
                            && alreadyImported.containsKey(ChatStore.identityKey(
                                    candidate.source(), candidate.externalConversationId()));
                    if (knownImported) {
                        skipped++;
                        continue;
                    }

                    try {
                        ImportedChat imported = provider.fetch(candidate);
                        ImportService.PersistResult result = services.importService().persist(imported);
                        switch (result.outcome()) {
                            case inserted -> {
                                inserted++;
                                System.out.println("  + " + result.chat().title());
                            }
                            case updated -> updated++;
                            case unchanged -> skipped++;
                        }
                    } catch (NoImportableContentException noContentFound) {
                        // The candidate was read successfully but has no real turns (e.g. a
                        // Codex rollout for an abandoned session, or a Gemini CLI session file
                        // that's only injected setup context). Not a failure -- don't print a
                        // per-candidate line for it, just count it, so a provider with thousands
                        // of these doesn't bury the genuine failures below.
                        noContent++;
                    } catch (Exception e) {
                        failed++;
                        System.out.println("  ! failed: " + candidate.title() + " (" + e.getMessage() + ")");
                    }
                }

                System.out.println("  " + inserted + " new, " + updated + " updated, "
                        + skipped + " already imported, " + noContent + " no content, " + failed + " failed");
                printCompleteness(providerComplete, providerDiagnostic);
                totalInserted += inserted;
                totalUpdated += updated;
                totalSkipped += skipped;
                totalNoContent += noContent;
                totalFailed += failed;
            }

            System.out.println();
            System.out.println("Total: " + totalInserted + " new, " + totalUpdated + " updated, "
                    + totalSkipped + " already imported, " + totalNoContent + " no content, "
                    + totalFailed + " failed");
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Reports whether this provider's discovery reached a verified terminal condition. */
    private static void printCompleteness(boolean complete, String diagnostic) {
        if (complete) {
            System.out.println("  complete"
                    + (diagnostic == null || diagnostic.isBlank() ? "" : ": " + diagnostic));
        } else {
            System.out.println("  incomplete"
                    + (diagnostic == null || diagnostic.isBlank() ? "" : ": " + diagnostic));
        }
    }
}
