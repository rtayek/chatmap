package chatmap.ui;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;

import chatmap.backend.ChatProvider;
import chatmap.backend.ClaudeCliBackend;
import chatmap.backend.DefaultChatProviders;
import chatmap.service.ChatGptArchiveImportService;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.LiveChatFetchService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
import chatmap.service.SummaryService;
import chatmap.service.TagService;
import chatmap.storage.ChatRepository;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.SearchRepository;
import chatmap.storage.SummaryRepository;
import chatmap.storage.TagRepository;

/** Factory for bootstrapping ChatMapController and underlying storage repositories/services. */
public final class ChatMapControllerFactory {

    private ChatMapControllerFactory() {
    }

    public static ChatMapController create(Connection connection) {
        ChatRepository chats = new ChatRepository(connection);
        MessageRepository messages = new MessageRepository(connection);
        ProjectRepository projects = new ProjectRepository(connection);
        TagRepository tags = new TagRepository(connection);
        SummaryRepository summaries = new SummaryRepository(connection);
        SearchRepository search = new SearchRepository(connection);

        ImportService importService = new ImportService(chats, messages);
        ChatGptArchiveImportService archiveImportService =
                new ChatGptArchiveImportService(importService);
        SummaryService summaryService = new SummaryService(chats, messages, summaries, tags,
                new ClaudeCliBackend(Duration.ofMinutes(3)));
        List<ChatProvider> providers = DefaultChatProviders.ordered();
        LiveChatFetchService liveChatFetchService =
                new LiveChatFetchService(providers, importService, chats);

        return new ChatMapController(
                importService,
                new ExportService(chats, messages, projects, tags),
                new SearchService(search),
                new ProjectService(projects, chats),
                new TagService(tags, chats),
                summaryService,
                liveChatFetchService,
                archiveImportService);
    }
}
