package chatmap.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.persistence.ProjectStore;
import chatmap.domain.Chat;
import chatmap.domain.Project;

/** Coordinates project management without exposing SQL to callers. */
public final class ProjectService {

    private final ProjectStore projects;
    private final ChatStore chats;

    public ProjectService(ProjectStore projects, ChatStore chats) {
        this.projects = projects;
        this.chats = chats;
    }

    public Project create(Project project) throws SQLException {
        try {
            return projects.insert(project);
        } catch (SQLException e) {
            if (projects.isDuplicateNameError(e)) {
                throw new IllegalArgumentException(
                        "A project named \"" + project.name() + "\" already exists.", e);
            }
            throw e;
        }
    }

    /**
     * Finds a project by name (case-insensitive), or creates it. Never creates a
     * second project with the same name: the {@code projectsNameIndex} unique
     * index is the backstop against a concurrent caller racing the same name —
     * if this caller's insert loses that race, it falls back to the winner's row
     * instead of failing.
     */
    public Project findOrCreate(String name, String description, String timestamp) throws SQLException {
        Optional<Project> existing = projects.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return projects.insert(new Project(0, name, description, timestamp, timestamp));
        } catch (SQLException e) {
            if (!projects.isDuplicateNameError(e)) {
                throw e;
            }
            return projects.findByName(name).orElseThrow(() -> e);
        }
    }

    public Optional<Project> findById(long projectId) throws SQLException {
        return projects.findById(projectId);
    }

    public List<Project> listAll() throws SQLException {
        return projects.findAll();
    }

    public void update(Project project) throws SQLException {
        projects.update(project);
    }

    public void delete(long projectId) throws SQLException {
        projects.delete(projectId);
    }

    public void assignChat(long chatId, long projectId) throws SQLException {
        chats.assignProject(chatId, projectId);
    }

    public void removeChat(long chatId) throws SQLException {
        chats.assignProject(chatId, null);
    }

    public List<Chat> listChats(long projectId) throws SQLException {
        return chats.findByProject(projectId);
    }
}
