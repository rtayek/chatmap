package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Tag;

/** CRUD for tags and chat/tag assignments. Holds a caller-owned Connection. */
public final class TagRepository {

    private final Connection conn;

    public TagRepository(Connection conn) {
        this.conn = conn;
    }

    public Tag insert(Tag tag) throws SQLException {
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

    public Optional<Tag> findById(long id) throws SQLException {
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

    public Optional<Tag> findByName(String name) throws SQLException {
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

    public void delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tags WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void assignToChat(long chatId, long tagId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO chat_tags (chat_id, tag_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setLong(2, tagId);
            ps.executeUpdate();
        }
    }

    public void removeFromChat(long chatId, long tagId) throws SQLException {
        String sql = "DELETE FROM chat_tags WHERE chat_id = ? AND tag_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setLong(2, tagId);
            ps.executeUpdate();
        }
    }

    public List<Tag> findByChat(long chatId) throws SQLException {
        String sql = "SELECT t.id, t.name FROM tags t "
                + "JOIN chat_tags ct ON ct.tag_id = t.id "
                + "WHERE ct.chat_id = ? ORDER BY t.name COLLATE NOCASE";
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

    private static Tag read(ResultSet rs) throws SQLException {
        return new Tag(rs.getLong("id"), rs.getString("name"));
    }
}
