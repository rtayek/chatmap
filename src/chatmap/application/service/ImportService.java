package chatmap.application.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.persistence.MessageStore;
import chatmap.application.port.persistence.TransactionManager;
import chatmap.application.port.importing.ConversationFileReader;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.application.model.ImportedChat;
import chatmap.application.port.ai.ModelTarget;

/** Imports selected files through format-specific importers, then persists normalized data. */
public final class ImportService {

    public enum Outcome {
        inserted,
        updated,
        unchanged
    }

    public record PersistResult(Chat chat, Outcome outcome) {
    }

    private final ChatStore chats;
    private final MessageStore messages;
    private final TransactionManager transactions;
    private final ConversationFileReader fileReader;

    public ImportService(ChatStore chats, MessageStore messages) {
        this(chats, messages, chats.transactions(), null);
    }

    public ImportService(ChatStore chats, MessageStore messages, ConversationFileReader fileReader) {
        this(chats, messages, chats.transactions(), fileReader);
    }

    public ImportService(ChatStore chats, MessageStore messages, TransactionManager transactions) {
        this(chats, messages, transactions, null);
    }

    public ImportService(ChatStore chats, MessageStore messages, TransactionManager transactions,
            ConversationFileReader fileReader) {
        this.chats = chats;
        this.messages = messages;
        this.transactions = transactions;
        this.fileReader = fileReader;
    }

    public Chat importFile(Path file) throws IOException, SQLException {
        if (fileReader == null) {
            throw new IllegalStateException("No conversation file reader is configured");
        }
        List<ImportedChat> imported = fileReader.read(file, Instant.now().toString());

        Chat firstStored = null;
        for (ImportedChat candidate : imported) {
            Chat stored = persist(candidate).chat();
            if (firstStored == null) {
                firstStored = stored;
            }
        }
        return firstStored;
    }

    public PersistResult persist(ImportedChat imported) throws SQLException {
        return transactions.inTransaction(() -> persistInTransaction(imported));
    }

    /**
     * Appends {@code newMessages} to the chat already identified by imported
     * external identity or generated prompt-session provenance,
     * preserving its existing messages and title. Unlike {@link #persist},
     * which replaces the whole message set whenever the incoming content hash
     * differs (correct for a re-imported file that IS the full transcript
     * each time), this is for incrementally building up one conversation
     * turn by turn. Creates a new chat via {@link #persist} if none exists
     * yet for that identity.
     */
    public PersistResult appendToConversation(Chat chatTemplate, List<Message> newMessages) throws SQLException {
        return transactions.inTransaction(() -> appendInTransaction(chatTemplate, newMessages));
    }

    public List<String> listPromptSessions(ModelTarget target) throws SQLException {
        Objects.requireNonNull(target, "target");
        return chats.findPromptSessions(target.providerId().name(), target.id());
    }

    private PersistResult appendInTransaction(Chat chatTemplate, List<Message> newMessages) throws SQLException {
        Optional<Chat> existing = existingConversation(chatTemplate);
        if (existing.isEmpty()) {
            return persistInTransaction(new ImportedChat(chatTemplate, newMessages));
        }

        Chat stored = existing.get();
        int nextSequence = messages.findByChat(stored.id()).stream().mapToInt(Message::sequence).max().orElse(-1) + 1;
        List<Message> sequenced = new ArrayList<>();
        for (int i = 0; i < newMessages.size(); i++) {
            Message m = newMessages.get(i);
            sequenced.add(new Message(0, 0, m.role(), m.text(), nextSequence + i, m.timestamp(), m.rawJson()));
        }
        insertMessages(stored.id(), sequenced);

        String touchedAt = sequenced.isEmpty() ? stored.updatedAt() : sequenced.getLast().timestamp();
        String contentHash = ChatContentHasher.hash(messages.findByChat(stored.id()));
        Chat updated = chats.updateImportMetadata(stored.id(), stored.title(), stored.sourceUri(),
                contentHash, touchedAt, touchedAt);
        return new PersistResult(updated, Outcome.updated);
    }

    private Optional<Chat> existingConversation(Chat chatTemplate) throws SQLException {
        if (chatTemplate.providerSessionId() != null && !chatTemplate.providerSessionId().isBlank()) {
            return chats.findByPromptSession(chatTemplate.providerId(), chatTemplate.modelTargetId(),
                    chatTemplate.providerSessionId());
        }
        String externalConversationId = Objects.requireNonNull(chatTemplate.externalConversationId(),
                "chatTemplate must carry an externalConversationId or providerSessionId");
        return chats.findByExternalIdentity(chatTemplate.source(), externalConversationId);
    }

    private PersistResult persistInTransaction(ImportedChat imported) throws SQLException {
        String transcriptHash = ChatContentHasher.hash(imported.messages());
        Chat incoming = withComputedImportMetadata(imported.chat(), transcriptHash);

        if (incoming.externalConversationId() != null) {
            return persistWithExternalIdentity(incoming, imported.messages());
        }

        // No external identity to dedup by (true for every plainText/markdown import, and
        // for any provider fetch that didn't supply one) — content hash is the only
        // remaining way to recognize "this is the same conversation again."
        var exactDuplicate = chats.findBySourceAndContentHash(incoming.source(), transcriptHash);
        if (exactDuplicate.isPresent()) {
            return unchangedResult(exactDuplicate.get(), incoming, transcriptHash);
        }

        try {
            Chat storedChat = chats.insert(incoming);
            insertMessages(storedChat.id(), imported.messages());
            return new PersistResult(storedChat, Outcome.inserted);
        } catch (SQLException e) {
            if (!chats.isUniqueConstraintError(e)) {
                throw e;
            }
            // chatsContentHashIndex backstop: another writer (e.g. ChatConsolidatorCli
            // running alongside the GUI, a separate process with its own serialized
            // executor) inserted the same (source, contentHash) between our check above
            // and this insert. Treat it the same as if we'd found it there.
            Chat winner = chats.findBySourceAndContentHash(incoming.source(), transcriptHash).orElseThrow(() -> e);
            return unchangedResult(winner, incoming, transcriptHash);
        }
    }

    private PersistResult unchangedResult(Chat existing, Chat incoming, String transcriptHash) throws SQLException {
        Chat updated = chats.updateImportMetadata(existing.id(), incoming.title(), incoming.sourceUri(),
                transcriptHash, incoming.sourceUpdatedAt(), incoming.lastImportedAt());
        return new PersistResult(updated, Outcome.unchanged);
    }

    private PersistResult persistWithExternalIdentity(Chat incoming, List<Message> incomingMessages)
            throws SQLException {
        var existing = chats.findByExternalIdentity(incoming.source(), incoming.externalConversationId());
        if (existing.isEmpty()) {
            try {
                Chat storedChat = chats.insert(incoming);
                insertMessages(storedChat.id(), incomingMessages);
                return new PersistResult(storedChat, Outcome.inserted);
            } catch (SQLException e) {
                if (!chats.isUniqueConstraintError(e)) {
                    throw e;
                }
                // chatsExternalIdentityIndex backstop: another writer won this identity
                // between our check above and this insert. Fall back to it, same as the
                // "already exists" branch below.
                existing = Optional.of(chats.findByExternalIdentity(incoming.source(), incoming.externalConversationId())
                        .orElseThrow(() -> e));
            }
        }

        Chat stored = existing.get();
        if (incoming.contentHash().equals(stored.contentHash())) {
            Chat updated = chats.updateImportMetadata(stored.id(), incoming.title(), incoming.sourceUri(),
                    incoming.contentHash(), incoming.sourceUpdatedAt(), incoming.lastImportedAt());
            return new PersistResult(updated, Outcome.unchanged);
        }

        chats.updateFromSource(stored.id(), incoming.source(), incoming.title(), incoming.createdAt(), incoming.updatedAt(),
                incoming.externalConversationId(), incoming.sourceUri(), incoming.contentHash(),
                incoming.sourceUpdatedAt(), incoming.lastImportedAt());
        messages.deleteByChat(stored.id());
        insertMessages(stored.id(), incomingMessages);
        return new PersistResult(chats.findById(stored.id()).orElseThrow(), Outcome.updated);
    }

    private void insertMessages(long chatId, List<Message> incomingMessages) throws SQLException {
        if (incomingMessages.isEmpty()) {
            return;
        }
        List<Message> prepared = incomingMessages.stream()
                .map(message -> new Message(0, chatId, message.role(), message.text(),
                        message.sequence(), message.timestamp(), message.rawJson()))
                .toList();
        messages.insertAll(prepared);
    }

    private static Chat withComputedImportMetadata(Chat chat, String contentHash) {
        String lastImportedAt = chat.lastImportedAt() == null ? chat.importedAt() : chat.lastImportedAt();
        String sourceUpdatedAt = chat.sourceUpdatedAt() == null ? chat.updatedAt() : chat.sourceUpdatedAt();
        return chat.toBuilder()
                .externalConversationId(blankToNull(chat.externalConversationId()))
                .sourceUri(blankToNull(chat.sourceUri()))
                .contentHash(contentHash)
                .sourceUpdatedAt(sourceUpdatedAt)
                .lastImportedAt(lastImportedAt)
                .providerId(blankToNull(chat.providerId()))
                .modelTargetId(blankToNull(chat.modelTargetId()))
                .providerModelName(blankToNull(chat.providerModelName()))
                .providerSessionId(blankToNull(chat.providerSessionId()))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

}
