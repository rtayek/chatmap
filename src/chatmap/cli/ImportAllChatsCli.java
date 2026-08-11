package chatmap.cli;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import chatmap.backend.providers.ChatProvider;
import chatmap.backend.providers.ChatProviderException;
import chatmap.backend.providers.DefaultChatProviders;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.domain.ConversationCandidate;
import chatmap.importer.ImportedChat;
import chatmap.service.ImportService;
import chatmap.service.ServiceGraph;
import chatmap.storage.ChatRepository;

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

        List<ChatProvider> providers = DefaultChatProviders.ordered();
        if (sourceFilter != null) {
            String wanted = sourceFilter;
            providers = providers.stream()
                    .filter(p -> p.name().equalsIgnoreCase(wanted))
                    .toList();
            if (providers.isEmpty()) {
                System.err.println("No configured provider named \"" + sourceFilter + "\".");
                System.err.println("Configured providers: "
                        + DefaultChatProviders.ordered().stream().map(ChatProvider::name).toList());
                System.exit(1);
                return;
            }
        }

        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            ServiceGraph services = context.services();
            int totalInserted = 0;
            int totalUpdated = 0;
            int totalSkipped = 0;
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

                if (candidates.isEmpty()) {
                    System.out.println("  no chats found");
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
                int failed = 0;

                for (ConversationCandidate candidate : candidates) {
                    boolean knownImported = candidate.externalConversationId() != null
                            && !candidate.externalConversationId().isBlank()
                            && alreadyImported.containsKey(ChatRepository.identityKey(
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
                    } catch (ChatProviderException | SQLException e) {
                        failed++;
                        System.out.println("  ! failed: " + candidate.title() + " (" + e.getMessage() + ")");
                    }
                }

                System.out.println("  " + inserted + " new, " + updated + " updated, "
                        + skipped + " already imported, " + failed + " failed");
                totalInserted += inserted;
                totalUpdated += updated;
                totalSkipped += skipped;
                totalFailed += failed;
            }

            System.out.println();
            System.out.println("Total: " + totalInserted + " new, " + totalUpdated + " updated, "
                    + totalSkipped + " already imported, " + totalFailed + " failed");
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            System.exit(1);
        }
    }
}
