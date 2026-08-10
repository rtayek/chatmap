package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.domain.Tag;

/** CRUD for tags and chat/tag assignments. Holds a caller-owned Connection. */
public final class TagRepository {

    private final Connection conn;

    public TagRepository(Connection conn) {
        this.conn = conn;
    }

    public Tag insert(Tag tag) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO tags (name) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, tag.name());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return new Tag(keys.getLong(1), tag.name());
                }
            }
        }
    }

    public Optional<Tag> findById(long id) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM tags WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(read(rs));
                }
            }
        }
    }

    public Optional<Tag> findByName(String name) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM tags WHERE name = ? COLLATE NOCASE")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(read(rs));
                }
            }
        }
    }

    public List<Tag> findAll() throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, name FROM tags ORDER BY name COLLATE NOCASE, id");
                    ResultSet rs = ps.executeQuery()) {
                List<Tag> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(read(rs));
                }
                return results;
            }
        }
    }

    public void update(Tag tag) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE tags SET name = ? WHERE id = ?")) {
                ps.setString(1, tag.name());
                ps.setLong(2, tag.id());
                ps.executeUpdate();
            }
        }
    }

    public void delete(long id) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tags WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        }
    }

    public void assignToChat(long chatId, long tagId) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT OR IGNORE INTO chatTags (chatId, tagId) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                ps.setLong(2, tagId);
                ps.executeUpdate();
            }
        }
    }

    public void removeFromChat(long chatId, long tagId) throws SQLException {
        synchronized (conn) {
            String sql = "DELETE FROM chatTags WHERE chatId = ? AND tagId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                ps.setLong(2, tagId);
                ps.executeUpdate();
            }
        }
    }

    public List<Tag> findByChat(long chatId) throws SQLException {
        synchronized (conn) {
            String sql = "SELECT t.id, t.name FROM tags t "
                    + "JOIN chatTags ct ON ct.tagId = t.id "
                    + "WHERE ct.chatId = ? ORDER BY t.name COLLATE NOCASE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Tag> tags = new ArrayList<>();
                    while (rs.next()) {
                        tags.add(read(rs));
                    }
                    return tags;
                }
            }
        }
    }

    /** Tags for each requested chat, ordered by tag name case-insensitively. */
    public Map<Long, List<Tag>> findByChatIds(List<Long> chatIds) throws SQLException {
        synchronized (conn) {
            Map<Long, List<Tag>> byChat = emptyListsByChatId(chatIds);
            if (byChat.isEmpty()) {
                return byChat;
            }
            String sql = "SELECT ct.chatId, t.id, t.name FROM tags t "
                    + "JOIN chatTags ct ON ct.tagId = t.id "
                    + "WHERE ct.chatId IN (" + placeholders(byChat.size()) + ") "
                    + "ORDER BY ct.chatId, t.name COLLATE NOCASE, t.id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1;
                for (Long chatId : byChat.keySet()) {
                    ps.setLong(index++, chatId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byChat.get(rs.getLong("chatId")).add(read(rs));
                    }
                    return byChat;
                }
            }
        }
    }

    private static Tag read(ResultSet rs) throws SQLException {
        return new Tag(rs.getLong("id"), rs.getString("name"));
    }

    private static Map<Long, List<Tag>> emptyListsByChatId(List<Long> chatIds) {
        Map<Long, List<Tag>> byChat = new LinkedHashMap<>();
        for (Long chatId : chatIds) {
            byChat.putIfAbsent(chatId, new ArrayList<>());
        }
        return byChat;
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}
