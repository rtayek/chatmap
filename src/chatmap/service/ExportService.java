package chatmap.service;

import java.sql.SQLException;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.exporter.ChatExportModel;
import chatmap.storage.ChatRepository;
import chatmap.storage.MessageRepository;

/** Loads hydrated export models and leaves formatting to exporters. */
public final class ExportService {

    private final ChatRepository chats;
    private final MessageRepository messages;

    public ExportService(ChatRepository chats, MessageRepository messages) {
        this.chats = chats;
        this.messages = messages;
    }

    public Optional<ChatExportModel> loadChat(long chatId) throws SQLException {
        Optional<Chat> chat = chats.findById(chatId);
        if (chat.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ChatExportModel(chat.get(), messages.findByChat(chatId)));
    }
}
