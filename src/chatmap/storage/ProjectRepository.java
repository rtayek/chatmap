package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Project;

/** CRUD for projects. Holds a Connection supplied by the caller; does not own it. */
public final class ProjectRepository {

    private final Connection conn;

    public ProjectRepository(Connection conn) {
        this.conn = conn;
    }

    public Project insert(Project project) throws SQLException {
        String sql = "INSERT INTO projects (name, description, createdAt, updatedAt) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, project.name());
            ps.setString(2, project.description());
            ps.setString(3, project.createdAt());
            ps.setString(4, project.updatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Project(keys.getLong(1), project.name(), project.description(),
                        project.createdAt(), project.updatedAt());
            }
        }
    }

    public Optional<Project> findById(long id) throws SQLException {
        String sql = "SELECT id, name, description, createdAt, updatedAt FROM projects WHERE id = ?";
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

    public List<Project> findAll() throws SQLException {
        String sql = "SELECT id, name, description, createdAt, updatedAt "
                + "FROM projects ORDER BY name COLLATE NOCASE, id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Project> results = new ArrayList<>();
            while (rs.next()) {
                results.add(read(rs));
            }
            return results;
        }
    }

    public void update(Project project) throws SQLException {
        String sql = "UPDATE projects SET name = ?, description = ?, createdAt = ?, updatedAt = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, project.name());
            ps.setString(2, project.description());
            ps.setString(3, project.createdAt());
            ps.setString(4, project.updatedAt());
            ps.setLong(5, project.id());
            ps.executeUpdate();
        }
    }

    public void delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static Project read(ResultSet rs) throws SQLException {
        return new Project(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("createdAt"),
                rs.getString("updatedAt"));
    }
}
