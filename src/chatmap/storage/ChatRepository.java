package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.Source;

/**
 * CRUD for chats. Holds a Connection supplied by the caller; does not own it.
 * (Single shared connection also keeps :memory: test databases coherent.)
 */
public final class ChatRepository {

    private final Connection conn;

    public ChatRepository(Connection conn) {
        this.conn = conn;
    }

    /** Inserts a chat; the id field of the argument is ignored. Returns the stored chat with its new id. */
    public Chat insert(Chat chat) throws SQLException {
        String sql = "INSERT INTO chats (projectId, source, title, createdAt, updatedAt, importedAt, archived) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (chat.projectId() == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setLong(1, chat.projectId());
            }
            ps.setString(2, chat.source().dbValue());
            ps.setString(3, chat.title());
            ps.setString(4, chat.createdAt());
            ps.setString(5, chat.updatedAt());
            ps.setString(6, chat.importedAt());
            ps.setInt(7, chat.archived() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new Chat(id, chat.projectId(), chat.source(), chat.title(),
                        chat.createdAt(), chat.updatedAt(), chat.importedAt(), chat.archived());
            }
        }
    }

    public Optional<Chat> findById(long id) throws SQLException {
        String sql = "SELECT id, projectId, source, title, createdAt, updatedAt, importedAt, archived "
                + "FROM chats WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(rs));
            }
        }
    }

    public List<Chat> findAll() throws SQLException {
        String sql = "SELECT id, projectId, source, title, createdAt, updatedAt, importedAt, archived "
                + "FROM chats ORDER BY importedAt, id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return readAll(rs);
        }
    }

    /** Deletes the chat; messages cascade via FK, and the FTS index follows via triggers. */
    public void delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chats WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void setArchived(long id, boolean archived) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET archived = ? WHERE id = ?")) {
            ps.setInt(1, archived ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void assignProject(long id, Long projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET projectId = ? WHERE id = ?")) {
            if (projectId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setLong(1, projectId);
            }
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public List<Chat> findByProject(long projectId) throws SQLException {
        String sql = "SELECT id, projectId, source, title, createdAt, updatedAt, importedAt, archived "
                + "FROM chats WHERE projectId = ? ORDER BY importedAt, id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    public List<Chat> findByTag(long tagId) throws SQLException {
        String sql = "SELECT c.id, c.projectId, c.source, c.title, c.createdAt, c.updatedAt, c.importedAt, c.archived "
                + "FROM chats c "
                + "JOIN chatTags ct ON ct.chatId = c.id "
                + "WHERE ct.tagId = ? "
                + "ORDER BY c.importedAt, c.id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    public void updateTitle(long id, String title) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET title = ? WHERE id = ?")) {
            ps.setString(1, title);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static Chat read(ResultSet rs) throws SQLException {
        long projectId = rs.getLong("projectId");
        Long boxedProjectId = rs.wasNull() ? null : projectId;
        return new Chat(
                rs.getLong("id"),
                boxedProjectId,
                Source.fromDbValue(rs.getString("source")),
                rs.getString("title"),
                rs.getString("createdAt"),
                rs.getString("updatedAt"),
                rs.getString("importedAt"),
                rs.getInt("archived") != 0);
    }

    private static List<Chat> readAll(ResultSet rs) throws SQLException {
        List<Chat> chats = new ArrayList<>();
        while (rs.next()) {
            chats.add(read(rs));
        }
        return chats;
    }
}
