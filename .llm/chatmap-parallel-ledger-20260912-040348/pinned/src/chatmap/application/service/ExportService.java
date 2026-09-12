package chatmap.application.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import chatmap.application.model.ChatExportModel;
import chatmap.application.model.ProjectHandoffModel;
import chatmap.application.port.exporting.ChatExportFormatter;
import chatmap.application.port.exporting.ProjectHandoffFormatter;
import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.persistence.MessageStore;
import chatmap.application.port.persistence.ProjectStore;
import chatmap.application.port.persistence.TagStore;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.Tag;

/** Loads hydrated export models and leaves formatting to exporters. */
public final class ExportService {

    private final ChatStore chats;
    private final MessageStore messages;
    private final ProjectStore projects;
    private final TagStore tags;
    private final ChatExportFormatter chatFormatter;
    private final ProjectHandoffFormatter handoffFormatter;

    public ExportService(
            ChatStore chats,
            MessageStore messages,
            ProjectStore projects,
            TagStore tags,
            ChatExportFormatter chatFormatter,
            ProjectHandoffFormatter handoffFormatter) {
        this.chats = Objects.requireNonNull(chats, "chats");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.chatFormatter = Objects.requireNonNull(chatFormatter, "chatFormatter");
        this.handoffFormatter = Objects.requireNonNull(handoffFormatter, "handoffFormatter");
    }

    /** Loads a chat and its messages as a single consistent snapshot. */
    public Optional<ChatExportModel> loadChat(long chatId) throws SQLException {
        return chats.transactions().inTransaction(() -> {
            Optional<Chat> chat = chats.findById(chatId);
            if (chat.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ChatExportModel(chat.get(), messages.findByChat(chatId)));
        });
    }

    public Optional<String> exportChatMarkdown(long chatId) throws SQLException {
        Optional<ChatExportModel> model = loadChat(chatId);
        if (model.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(chatFormatter.format(model.get()));
    }

    public boolean writeChatMarkdown(long chatId, Path outputPath) throws SQLException, IOException {
        Optional<String> markdown = exportChatMarkdown(chatId);
        if (markdown.isEmpty()) {
            return false;
        }
        Files.writeString(outputPath, markdown.get(), StandardCharsets.UTF_8);
        return true;
    }

    /**
     * Loads a project handoff — project, its chats, their messages, and their
     * tags — as a single consistent snapshot. Without the transaction, a write
     * landing between any of the underlying reads (e.g. a chat reassigned away
     * from this project mid-export) could produce a torn handoff; design.md
     * calls this export "deterministic," which only holds if it's atomic.
     */
    public Optional<ProjectHandoffModel> loadProjectHandoff(long projectId, String exportedAt) throws SQLException {
        return chats.transactions().inTransaction(() -> {
            Optional<Project> project = projects.findById(projectId);
            if (project.isEmpty()) {
                return Optional.empty();
            }

            List<Chat> projectChats = chats.findByProject(projectId);
            List<Long> chatIds = projectChats.stream().map(Chat::id).toList();
            Map<Long, List<Message>> messagesByChat = messages.findByChatIds(chatIds);
            Map<Long, List<Tag>> tagsByChat = tags.findByChatIds(chatIds);

            List<ProjectHandoffModel.ChatEntry> entries = new ArrayList<>();
            for (Chat chat : projectChats) {
                entries.add(new ProjectHandoffModel.ChatEntry(
                        chat,
                        messagesByChat.getOrDefault(chat.id(), List.of()),
                        tagsByChat.getOrDefault(chat.id(), List.of())));
            }

            return Optional.of(new ProjectHandoffModel(project.get(), exportedAt, entries));
        });
    }

    public Optional<String> exportProjectHandoff(long projectId, String exportedAt) throws SQLException {
        Optional<ProjectHandoffModel> model = loadProjectHandoff(projectId, exportedAt);
        if (model.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(handoffFormatter.format(model.get()));
    }

    public boolean writeProjectHandoff(long projectId, String exportedAt, Path outputPath)
            throws SQLException, IOException {
        Optional<String> markdown = exportProjectHandoff(projectId, exportedAt);
        if (markdown.isEmpty()) {
            return false;
        }
        Files.writeString(outputPath, markdown.get(), StandardCharsets.UTF_8);
        return true;
    }
}
