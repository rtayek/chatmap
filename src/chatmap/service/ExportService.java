package chatmap.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.exporter.ChatExportModel;
import chatmap.exporter.ProjectHandoffModel;
import chatmap.storage.ChatRepository;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.TagRepository;

/** Loads hydrated export models and leaves formatting to exporters. */
public final class ExportService {

    private final ChatRepository chats;
    private final MessageRepository messages;
    private final ProjectRepository projects;
    private final TagRepository tags;

    public ExportService(ChatRepository chats, MessageRepository messages) {
        this(chats, messages, null, null);
    }

    public ExportService(
            ChatRepository chats,
            MessageRepository messages,
            ProjectRepository projects,
            TagRepository tags) {
        this.chats = chats;
        this.messages = messages;
        this.projects = projects;
        this.tags = tags;
    }

    public Optional<ChatExportModel> loadChat(long chatId) throws SQLException {
        Optional<Chat> chat = chats.findById(chatId);
        if (chat.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ChatExportModel(chat.get(), messages.findByChat(chatId)));
    }

    public Optional<ProjectHandoffModel> loadProjectHandoff(long projectId, String exportedAt) throws SQLException {
        if (projects == null || tags == null) {
            throw new IllegalStateException("project and tag repositories are required for project handoff export");
        }

        Optional<Project> project = projects.findById(projectId);
        if (project.isEmpty()) {
            return Optional.empty();
        }

        List<ProjectHandoffModel.ChatEntry> entries = new ArrayList<>();
        for (Chat chat : chats.findByProject(projectId)) {
            entries.add(new ProjectHandoffModel.ChatEntry(
                    chat,
                    messages.findByChat(chat.id()),
                    tags.findByChat(chat.id())));
        }

        return Optional.of(new ProjectHandoffModel(project.get(), exportedAt, entries));
    }
}
