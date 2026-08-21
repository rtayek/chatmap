package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Project;

/** Application-facing persistence operations for projects. */
public interface ProjectStore {

    Project insert(Project project) throws SQLException;

    Optional<Project> findById(long id) throws SQLException;

    Optional<Project> findByName(String name) throws SQLException;

    Optional<Project> findByRepositoryPath(String repositoryPath) throws SQLException;

    List<Project> findAll() throws SQLException;

    void update(Project project) throws SQLException;

    void delete(long id) throws SQLException;

    boolean isDuplicateNameError(SQLException failure);
}
