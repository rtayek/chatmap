package chatmap.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.backend.providers.ChatProvider;
import chatmap.backend.providers.DefaultChatProviders;
import chatmap.backend.web.CdpWebChatProvider;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.importer.ImportedChat;

/** Prints the latest prompt and response visible from each configured chat source. */
public final class LiveSourceExchanges {

    public static void main(String[] args) {
        for (ChatProvider provider : DefaultChatProviders.ordered()) {
            printField("provider", provider.name());
            try {
                Optional<ImportedChat> latest = provider.latestChat();
                if (latest.isEmpty()) {
                    System.out.println("no live chat found");
                    unavailableReason(provider).ifPresent(reason -> printField("reason", reason));
                } else {
                    printChat(latest.get());
                }
            } catch (Exception failure) {
                printField("unavailable", diagnostic(failure));
            }
            System.out.println("--------------------------------------------------------------------------------");
        }
    }

    private static void printChat(ImportedChat chat) {
        printField("source", chat.chat().source().dbValue());
        printField("title", chat.chat().title());
        printField("externalConversationId", chat.chat().externalConversationId());
        printField("sourceUri", chat.chat().sourceUri());
        printBlock("last prompt", lastText(chat.messages(), MessageRole.user));
        printBlock("last response", lastText(chat.messages(), MessageRole.assistant));
    }

    private static String lastText(List<Message> messages, MessageRole role) {
        List<Message> matches = new ArrayList<>();
        for (Message message : messages) {
            if (role == message.role()) {
                matches.add(message);
            }
        }
        if (matches.isEmpty()) {
            return "";
        }
        return matches.get(matches.size() - 1).text();
    }

    private static Optional<String> unavailableReason(ChatProvider provider) {
        return provider instanceof CdpWebChatProvider cdp
                ? cdp.lastUnavailableReason()
                : Optional.empty();
    }

    private static void printField(String label, String value) {
        System.out.println(label + ": " + nullToBlank(value));
    }

    private static void printBlock(String label, String value) {
        System.out.println(label + ":");
        System.out.println(nullToBlank(value));
    }

    private static String diagnostic(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return failure.getClass().getSimpleName() + ": " + message;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
