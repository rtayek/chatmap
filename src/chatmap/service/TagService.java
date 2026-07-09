package chatmap.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.Tag;
import chatmap.storage.ChatRepository;
import chatmap.storage.TagRepository;

/** Coordinates tag management without exposing SQL to callers. */
public final class TagService {

    private final TagRepository tags;
    private final ChatRepository chats;

    public TagService(TagRepository tags, ChatRepository chats) {
        this.tags = tags;
        this.chats = chats;
    }

    public Tag create(Tag tag) throws SQLException {
        return tags.insert(tag);
    }

    public Optional<Tag> findById(long tagId) throws SQLException {
        return tags.findById(tagId);
    }

    public Optional<Tag> findByName(String name) throws SQLException {
        return tags.findByName(name);
    }

    public List<Tag> listAll() throws SQLException {
        return tags.findAll();
    }

    public void update(Tag tag) throws SQLException {
        tags.update(tag);
    }

    public void delete(long tagId) throws SQLException {
        tags.delete(tagId);
    }

    public void addToChat(long chatId, long tagId) throws SQLException {
        tags.assignToChat(chatId, tagId);
    }

    public void removeFromChat(long chatId, long tagId) throws SQLException {
        tags.removeFromChat(chatId, tagId);
    }

    public List<Tag> listForChat(long chatId) throws SQLException {
        return tags.findByChat(chatId);
    }

    public List<Chat> listChats(long tagId) throws SQLException {
        return chats.findByTag(tagId);
    }
}
