package chatmap.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.storage.ChatRepository;
import chatmap.storage.ProjectRepository;

/** Coordinates project management without exposing SQL to callers. */
public final class ProjectService {

    private final ProjectRepository projects;
    private final ChatRepository chats;

    public ProjectService(ProjectRepository projects, ChatRepository chats) {
        this.projects = projects;
        this.chats = chats;
    }

    public Project create(Project project) throws SQLException {
        return projects.insert(project);
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
