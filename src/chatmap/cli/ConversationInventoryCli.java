package chatmap.cli;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.ConversationInventory;
import chatmap.domain.InventoryEntry;
import chatmap.domain.ProviderInventory;

/** Prints a read-only inventory of all discoverable configured-source conversations. */
public final class ConversationInventoryCli {

    public static void main(String[] args) {
        ParsedArguments parsedArguments;
        try {
            parsedArguments = ChatMapPaths.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: conversationInventory [--home <directory>]");
            System.exit(1);
            return;
        }
        if (!parsedArguments.remainingArgs().isEmpty()) {
            System.err.println("Usage: conversationInventory [--home <directory>]");
            System.exit(1);
            return;
        }

        System.out.println(ChatMapPaths.diagnostics(parsedArguments.paths()));
        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments,
                chatmap.app.DefaultServiceIntegrations.create())) {
            print(context.services().conversationInventoryService().inventory());
        } catch (Exception e) {
            System.err.println("Could not inventory conversations: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void print(ConversationInventory inventory) {
        for (ProviderInventory provider : inventory.providers()) {
            long imported = provider.conversations().stream().filter(InventoryEntry::alreadyImported).count();
            long missing = provider.conversations().size() - imported;
            System.out.println();
            System.out.println(provider.providerName()
                    + "\tcount=" + provider.conversations().size()
                    + "\timported=" + imported
                    + "\tmissing=" + missing
                    + "\tcomplete=" + provider.complete());
            if (provider.diagnostic() != null && !provider.diagnostic().isBlank()) {
                System.out.println("diagnostic\t" + provider.diagnostic());
            }
            System.out.println("status\tsource\tchatId\texternalConversationId\tupdatedAt\ttitle\tsourceUri");
            for (InventoryEntry entry : provider.conversations()) {
                ConversationCandidate candidate = entry.candidate();
                System.out.println((entry.alreadyImported() ? "imported" : "missing")
                        + "\t" + candidate.source().dbValue()
                        + "\t" + nullToBlank(entry.importedChatId())
                        + "\t" + nullToBlank(candidate.externalConversationId())
                        + "\t" + nullToBlank(candidate.updatedAt())
                        + "\t" + tsv(candidate.title())
                        + "\t" + tsv(candidate.sourceUri()));
            }
        }
    }

    private static String nullToBlank(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String tsv(String value) {
        return nullToBlank(value).replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
