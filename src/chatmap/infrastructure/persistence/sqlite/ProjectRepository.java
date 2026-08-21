package chatmap.infrastructure.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.application.port.persistence.ProjectStore;
import chatmap.domain.Project;

/** CRUD for projects. Holds a Connection supplied by the caller; does not own it. */
public final class ProjectRepository implements ProjectStore {

    private final Connection conn;

    public ProjectRepository(Connection conn) {
        this.conn = conn;
    }

    public Project insert(Project project) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO projects (name, description, repositoryPath, createdAt, updatedAt) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, project.name());
                ps.setString(2, project.description());
                ps.setString(3, project.repositoryPath());
                ps.setString(4, project.createdAt());
                ps.setString(5, project.updatedAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return new Project(keys.getLong(1), project.name(), project.description(), project.repositoryPath(),
                            project.createdAt(), project.updatedAt());
                }
            }
        }
    }

    public Optional<Project> findById(long id) throws SQLException {
        synchronized (conn) {
            String sql = "SELECT id, name, description, repositoryPath, createdAt, updatedAt FROM projects WHERE id = ?";
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
    }

    /**
     * Case-insensitive lookup by name, for callers that must not create duplicate
     * projects. Compares in Java ({@link String#equalsIgnoreCase}) rather than SQL
     * {@code COLLATE NOCASE}, which only folds ASCII letters — this also matches
     * names differing only in non-ASCII case (e.g. "München" vs "MÜNCHEN"). The
     * projects table is small (a personal project list), so a full scan is cheap;
     * the {@code projectsNameIndex} unique index (ASCII-only, see
     * {@link Database#applyMigrations}) is the concurrency backstop, not the
     * source of truth for this comparison.
     */
    public Optional<Project> findByName(String name) throws SQLException {
        for (Project project : findAll()) {
            if (project.name().equalsIgnoreCase(name)) {
                return Optional.of(project);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Project> findByRepositoryPath(String repositoryPath) throws SQLException {
        synchronized (conn) {
            if (repositoryPath == null || repositoryPath.isBlank()) {
                return Optional.empty();
            }
            String sql = "SELECT id, name, description, repositoryPath, createdAt, updatedAt "
                    + "FROM projects WHERE repositoryPath = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, repositoryPath);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(read(rs));
                }
            }
        }
    }

    /**
     * True if the exception is a violation of the {@code projectsNameIndex}
     * unique-name constraint — i.e. a concurrent insert won a name that this
     * caller was also trying to claim. Callers use this to fall back to
     * {@link #findByName} instead of failing outright.
     */
    public static boolean isDuplicateNameViolation(SQLException e) {
        return e instanceof org.sqlite.SQLiteException sqliteException
                && sqliteException.getResultCode() == org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
    }

    @Override
    public boolean isDuplicateNameError(SQLException failure) {
        return isDuplicateNameViolation(failure);
    }

    public List<Project> findAll() throws SQLException {
        synchronized (conn) {
            String sql = "SELECT id, name, description, repositoryPath, createdAt, updatedAt "
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
    }

    public void update(Project project) throws SQLException {
        synchronized (conn) {
            String sql = "UPDATE projects SET name = ?, description = ?, repositoryPath = ?, createdAt = ?, "
                    + "updatedAt = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, project.name());
                ps.setString(2, project.description());
                ps.setString(3, project.repositoryPath());
                ps.setString(4, project.createdAt());
                ps.setString(5, project.updatedAt());
                ps.setLong(6, project.id());
                ps.executeUpdate();
            }
        }
    }

    public void delete(long id) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        }
    }

    private static Project read(ResultSet rs) throws SQLException {
        return new Project(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("repositoryPath"),
                rs.getString("createdAt"),
                rs.getString("updatedAt"));
    }
}
