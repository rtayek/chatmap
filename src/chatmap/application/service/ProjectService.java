package chatmap.application.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.persistence.ProjectStore;
import chatmap.application.port.persistence.RelatedProjectStore;
import chatmap.domain.Chat;
import chatmap.domain.Project;

/** Coordinates project management without exposing SQL to callers. */
public final class ProjectService {

    private final ProjectStore projects;
    private final ChatStore chats;
    private final RelatedProjectStore relatedProjects;

    public ProjectService(ProjectStore projects, ChatStore chats) {
        this(projects, chats, null);
    }

    public ProjectService(ProjectStore projects, ChatStore chats, RelatedProjectStore relatedProjects) {
        this.projects = projects;
        this.chats = chats;
        this.relatedProjects = relatedProjects;
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
        return findOrCreate(name, description, timestamp, null);
    }

    public Project findOrCreate(String name, String description, String timestamp, String repositoryPath)
            throws SQLException {
        return findOrCreate(name, description, timestamp, repositoryPath, null);
    }

    public Project findOrCreate(String name, String description, String timestamp, String localPath,
            String remoteUrl) throws SQLException {
        Optional<Project> byPath = projects.findByLocalPath(localPath);
        if (byPath.isPresent()) {
            return updateMissingPaths(byPath.get(), null, remoteUrl, timestamp);
        }
        Optional<Project> existing = projects.findByName(name);
        if (existing.isPresent()) {
            return updateMissingPaths(existing.get(), localPath, remoteUrl, timestamp);
        }
        try {
            return projects.insert(new Project(0, name, description, localPath, localPath, remoteUrl,
                    timestamp, timestamp));
        } catch (SQLException e) {
            if (!projects.isDuplicateNameError(e)) {
                throw e;
            }
            Optional<Project> pathWinner = projects.findByLocalPath(localPath);
            if (pathWinner.isPresent()) {
                return pathWinner.get();
            }
            return projects.findByName(name).orElseThrow(() -> e);
        }
    }

    private Project updateMissingPaths(Project project, String localPath, String remoteUrl, String timestamp)
            throws SQLException {
        String updatedLocalPath = project.localPath() == null && hasText(localPath) ? localPath : project.localPath();
        String updatedRemoteUrl = project.remoteUrl() == null && hasText(remoteUrl) ? remoteUrl : project.remoteUrl();
        if (Objects.equals(updatedLocalPath, project.localPath())
                && Objects.equals(updatedRemoteUrl, project.remoteUrl())) {
            return project;
        }
        String updatedRepositoryPath = project.repositoryPath() == null ? updatedLocalPath : project.repositoryPath();
        Project updated = new Project(project.id(), project.name(), project.description(), updatedRepositoryPath,
                updatedLocalPath, updatedRemoteUrl, project.createdAt(), timestamp);
        projects.update(updated);
        return updated;
    }

    public ProjectContext contextFor(Project project) {
        return ProjectContext.from(project);
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

    public void addRelatedProject(long chatId, long projectId) throws SQLException {
        requireRelatedProjects().assignToChat(chatId, projectId);
    }

    public void removeRelatedProject(long chatId, long projectId) throws SQLException {
        requireRelatedProjects().removeFromChat(chatId, projectId);
    }

    public List<Project> listRelatedProjects(long chatId) throws SQLException {
        return requireRelatedProjects().findByChat(chatId);
    }

    public List<Chat> listChatsForRelatedProject(long projectId) throws SQLException {
        return requireRelatedProjects().findChatsByProject(projectId);
    }

    private RelatedProjectStore requireRelatedProjects() {
        if (relatedProjects == null) {
            throw new IllegalStateException("Related project storage is not configured.");
        }
        return relatedProjects;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
