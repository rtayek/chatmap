package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import chatmap.domain.Chat;

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
        String sql = "INSERT INTO chats (project_id, source, title, created_at, updated_at, imported_at, archived) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (chat.projectId() == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setLong(1, chat.projectId());
            }
            ps.setString(2, chat.source());
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
        String sql = "SELECT id, project_id, source, title, created_at, updated_at, imported_at, archived "
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

    private static Chat read(ResultSet rs) throws SQLException {
        long projectId = rs.getLong("project_id");
        Long boxedProjectId = rs.wasNull() ? null : projectId;
        return new Chat(
                rs.getLong("id"),
                boxedProjectId,
                rs.getString("source"),
                rs.getString("title"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                rs.getString("imported_at"),
                rs.getInt("archived") != 0);
    }
}
