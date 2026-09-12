package chatmap.infrastructure.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import chatmap.application.port.persistence.RelatedProjectStore;
import chatmap.domain.Chat;
import chatmap.domain.Project;

/** CRUD for chat/related-project assignments. Holds a caller-owned Connection. */
public final class RelatedProjectRepository implements RelatedProjectStore {

    private final Connection conn;

    public RelatedProjectRepository(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void assignToChat(long chatId, long projectId) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT OR IGNORE INTO chatRelatedProjects (chatId, projectId) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                ps.setLong(2, projectId);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void removeFromChat(long chatId, long projectId) throws SQLException {
        synchronized (conn) {
            String sql = "DELETE FROM chatRelatedProjects WHERE chatId = ? AND projectId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                ps.setLong(2, projectId);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public List<Project> findByChat(long chatId) throws SQLException {
        synchronized (conn) {
            String sql = selectProjectColumns("p")
                    + "FROM projects p "
                    + "JOIN chatRelatedProjects crp ON crp.projectId = p.id "
                    + "WHERE crp.chatId = ? ORDER BY p.name COLLATE NOCASE, p.id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                try (ResultSet rs = ps.executeQuery()) {
                    return readProjects(rs);
                }
            }
        }
    }

    @Override
    public List<Chat> findChatsByProject(long projectId) throws SQLException {
        synchronized (conn) {
            String sql = ChatRowMapper.SELECT_C_COLUMNS
                    + "FROM chats c "
                    + "JOIN chatRelatedProjects crp ON crp.chatId = c.id "
                    + "WHERE crp.projectId = ? ORDER BY c.importedAt, c.id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Chat> chats = new ArrayList<>();
                    while (rs.next()) {
                        chats.add(ChatRowMapper.read(rs));
                    }
                    return chats;
                }
            }
        }
    }

    private static List<Project> readProjects(ResultSet rs) throws SQLException {
        List<Project> projects = new ArrayList<>();
        while (rs.next()) {
            projects.add(readProject(rs));
        }
        return projects;
    }

    private static Project readProject(ResultSet rs) throws SQLException {
        return new Project(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("repositoryPath"),
                rs.getString("localPath"),
                rs.getString("remoteUrl"),
                rs.getString("createdAt"),
                rs.getString("updatedAt"));
    }

    private static String selectProjectColumns(String alias) {
        return "SELECT " + alias + ".id, " + alias + ".name, " + alias + ".description, "
                + alias + ".repositoryPath, " + alias + ".localPath, " + alias + ".remoteUrl, "
                + alias + ".createdAt, " + alias + ".updatedAt ";
    }
}
