package chatmap.presentation.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.ConversationInventory;
import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import chatmap.application.model.ChatExportModel;
import chatmap.application.service.ExportService;
import chatmap.application.service.ChatGptArchiveImportService;
import chatmap.application.service.ChatGptArchiveImportService.BulkImportResult;
import chatmap.application.service.ConversationInventoryService;
import chatmap.application.service.ImportService;
import chatmap.application.service.LiveChatFetchService;
import chatmap.application.service.ProjectService;
import chatmap.application.service.PromptResult;
import chatmap.application.service.PromptRouterService;
import chatmap.application.service.PromptRoutingResult;
import chatmap.application.service.PromptService;
import chatmap.application.service.ConversationContext;
import chatmap.application.service.ProjectContext;
import chatmap.application.service.SearchService;
import chatmap.app.ServiceGraph;
import chatmap.application.service.SummaryService;
import chatmap.application.service.TagService;
import chatmap.application.support.Locks;

/** Coordinates application operations without depending on JavaFX widgets. */
public final class ChatMapController {

    private final ImportService importService;
    private final ExportService exportService;
    private final SearchService searchService;
    private final ProjectService projectService;
    private final TagService tagService;
    private final SummaryService summaryService;
    private final LiveChatFetchService liveChatFetchService;
    private final ChatGptArchiveImportService archiveImportService;
    private final ConversationInventoryService conversationInventoryService;
    private final PromptService promptService;
    private final PromptRouterService promptRouterService;
    private final ChatListState listState;
    
    /**
     * Lock protecting {@link #listState} and {@link #filterCriteria}.
     * INVARIANT: Never acquire this lock while holding a repository connection lock.
     * To prevent deadlocks, always run database queries before acquiring this lock, 
     * or release this lock before making a database query.
     */
    private final Object stateLock = new Object();

    private ChatFilterCriteria filterCriteria = ChatFilterCriteria.EMPTY;

    private ChatMapController(Builder builder) {
        importService = builder.importService;
        exportService = builder.exportService;
        searchService = builder.searchService;
        projectService = builder.projectService;
        tagService = builder.tagService;
        summaryService = builder.summaryService;
        liveChatFetchService = builder.liveChatFetchService;
        archiveImportService = builder.archiveImportService;
        conversationInventoryService = builder.conversationInventoryService;
        promptService = builder.promptService;
        promptRouterService = builder.promptRouterService;
        listState = new ChatListState();
    }

    /** Wires the controller from the shared {@link ServiceGraph} — the production path. */
    public ChatMapController(ServiceGraph services) {
        this(builder()
                .importService(services.importService())
                .exportService(services.exportService())
                .searchService(services.searchService())
                .projectService(services.projectService())
                .tagService(services.tagService())
                .summaryService(services.summaryService())
                .liveChatFetchService(services.liveChatFetchService())
                .archiveImportService(services.archiveImportService())
                .conversationInventoryService(services.conversationInventoryService())
                .promptService(services.promptService())
                .promptRouterService(services.promptRouterService()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ImportService importService;
        private ExportService exportService;
        private SearchService searchService;
        private ProjectService projectService;
        private TagService tagService;
        private SummaryService summaryService;
        private LiveChatFetchService liveChatFetchService;
        private ChatGptArchiveImportService archiveImportService;
        private ConversationInventoryService conversationInventoryService;
        private PromptService promptService;
        private PromptRouterService promptRouterService;

        private Builder() {
        }

        public Builder importService(ImportService value) {
            importService = value;
            return this;
        }

        public Builder exportService(ExportService value) {
            exportService = value;
            return this;
        }

        public Builder searchService(SearchService value) {
            searchService = value;
            return this;
        }

        public Builder projectService(ProjectService value) {
            projectService = value;
            return this;
        }

        public Builder tagService(TagService value) {
            tagService = value;
            return this;
        }

        public Builder summaryService(SummaryService value) {
            summaryService = value;
            return this;
        }

        public Builder liveChatFetchService(LiveChatFetchService value) {
            liveChatFetchService = value;
            return this;
        }

        public Builder archiveImportService(ChatGptArchiveImportService value) {
            archiveImportService = value;
            return this;
        }

        public Builder conversationInventoryService(ConversationInventoryService value) {
            conversationInventoryService = value;
            return this;
        }

        public Builder promptService(PromptService value) {
            promptService = value;
            return this;
        }

        public Builder promptRouterService(PromptRouterService value) {
            promptRouterService = value;
            return this;
        }

        public ChatMapController build() {
            Objects.requireNonNull(importService, "importService");
            Objects.requireNonNull(exportService, "exportService");
            Objects.requireNonNull(searchService, "searchService");
            Objects.requireNonNull(projectService, "projectService");
            Objects.requireNonNull(tagService, "tagService");
            Objects.requireNonNull(summaryService, "summaryService");
            Objects.requireNonNull(liveChatFetchService, "liveChatFetchService");
            return new ChatMapController(this);
        }
    }

    public ChatFilterCriteria filterCriteria() {
        return locked(() -> filterCriteria);
    }

    public ChatListState.Snapshot loadAllChats() throws SQLException {
        List<SearchResult> results = searchService.searchResults("");
        return locked(() -> {
            filterCriteria = ChatFilterCriteria.EMPTY;
            return listState.showAll(results, "Loaded " + results.size() + " chats.");
        });
    }

    public ChatListState.Snapshot searchChats(String query) throws SQLException {
        ChatFilterCriteria criteria = locked(() -> {
            filterCriteria = filterCriteria.withQuery(query);
            return filterCriteria;
        });
        if (criteria.isEmpty()) {
            return loadAllChats();
        }
        List<SearchResult> matches = searchService.searchResults(criteria.query(), criteria.toSearchOptions());
        return locked(() -> listState.showSearchResults(matches, formatMatchStatus(matches.size())));
    }

    public ChatListState.Snapshot importFile(Path file) throws IOException, SQLException {
        Chat imported = importService.importFile(file);
        List<SearchResult> results = searchService.searchResults("");
        return locked(() -> {
            filterCriteria = ChatFilterCriteria.EMPTY;
            listState.showAll(results, "Imported " + imported.title());
            return listState.select(imported.id());
        });
    }

    public ChatListState.Snapshot importChatGptArchive(Path zipFile) throws Exception {
        if (archiveImportService == null) {
            throw new IllegalStateException("ChatGPT archive import is not configured.");
        }
        BulkImportResult result = archiveImportService.importArchive(zipFile);
        List<SearchResult> results = searchService.searchResults("");
        return locked(() -> {
            filterCriteria = ChatFilterCriteria.EMPTY;
            return listState.showAll(results, result.summary());
        });
    }

    public ConversationInventory conversationInventory() throws Exception {
        if (conversationInventoryService == null) {
            throw new IllegalStateException("Conversation inventory is not configured.");
        }
        return conversationInventoryService.inventory();
    }

    public PromptResult executePrompt(String backendName, String prompt) throws SQLException {
        if (promptService == null) {
            throw new IllegalStateException("Prompt service is not configured.");
        }
        return promptService.submit(backendName, prompt);
    }

    public PromptRoutingResult routePrompt(Project project, String conversationId, String prompt) throws SQLException {
        return routePrompt(project, conversationId, prompt, null);
    }

    public PromptRoutingResult routePrompt(Project project, String conversationId, String prompt, Chat resumeChat)
            throws SQLException {
        if (promptRouterService == null) {
            throw new IllegalStateException("Prompt router is not configured.");
        }
        if (project == null) {
            throw new IllegalArgumentException("Project is required.");
        }
        String trimmedPrompt = requireName(prompt, "Prompt");
        ConversationContext conversation = new ConversationContext(requireName(conversationId, "Conversation"));
        return promptRouterService.route(ProjectContext.from(project), conversation, trimmedPrompt, resumeChat);
    }

    public List<Chat> listPromptResumeCandidates(Project project) throws SQLException {
        if (project == null) {
            return List.of();
        }
        return projectService.listChats(project.id());
    }

    public ChatListState.Snapshot selectChat(long chatId) {
        return locked(() -> listState.select(chatId));
    }

    /**
     * Fetches the latest live chat (importing it) and selects it, using the same
     * fallback order as the CLI: live provider, else the most recent stored chat.
     * Blocking (HTTP with retry/backoff) — callers should run it off the UI thread.
     * Storage access serializes inside the repositories, so long provider checks never block UI reads.
     */
    public ChatListState.Snapshot fetchLatestChat() throws Exception {
        LiveChatFetchService.Resolution resolution = liveChatFetchService.resolve(null);
        List<SearchResult> results = searchService.searchResults("");
        return locked(() -> {
            filterCriteria = ChatFilterCriteria.EMPTY;
            listState.showAll(results, "Using " + resolution.how());
            return listState.select(resolution.chatId());
        });
    }

    /**
     * Summarizes and tags the given chat, then re-selects it so the new tags (and
     * summary, via {@link #latestSummary}) are reflected. Blocking (calls the
     * claude CLI) — callers should run it off the UI thread. The CLI call runs
     * lock-free; only the storage reads/writes inside serialize on the connection.
     */
    public ChatListState.Snapshot summarizeAndTag(long chatId) throws Exception {
        summaryService.summarize(chatId);
        return refreshCurrent("Summarized and tagged chat " + chatId, chatId);
    }

    public Optional<ChatExportModel> loadChatDetails(long chatId) throws SQLException {
        return exportService.loadChat(chatId);
    }

    public Optional<ChatSummary> latestSummary(long chatId) throws SQLException {
        return summaryService.latestSummary(chatId);
    }

    public boolean exportChatMarkdown(long chatId, Path outputPath) throws SQLException, IOException {
        return exportService.writeChatMarkdown(chatId, outputPath);
    }

    public List<Project> listProjects() throws SQLException {
        return projectService.listAll();
    }

    public Project createProject(String name) throws SQLException {
        String projectName = requireName(name, "Project name");
        String now = Instant.now().toString();
        return projectService.create(new Project(0, projectName, null, now, now));
    }

    public ChatListState.Snapshot assignProject(long chatId, long projectId) throws SQLException {
        projectService.assignChat(chatId, projectId);
        return refreshCurrent("Project assigned", chatId);
    }

    public ChatListState.Snapshot clearProject(long chatId) throws SQLException {
        projectService.removeChat(chatId);
        return refreshCurrent("Project cleared", chatId);
    }

    public ChatListState.Snapshot filterByProject(long projectId) throws SQLException {
        ChatFilterCriteria criteria = locked(() -> {
            filterCriteria = filterCriteria.withProjectId(projectId);
            return filterCriteria;
        });
        List<SearchResult> matches = searchService.searchResults(criteria.query(), criteria.toSearchOptions());
        return locked(() -> listState.showSearchResults(matches, formatFilterStatus(matches.size())));
    }

    public ChatListState.Snapshot addRelatedProject(long chatId, long projectId) throws SQLException {
        projectService.addRelatedProject(chatId, projectId);
        return refreshCurrent("Related project added", chatId);
    }

    public ChatListState.Snapshot removeRelatedProject(long chatId, long projectId) throws SQLException {
        projectService.removeRelatedProject(chatId, projectId);
        return refreshCurrent("Related project removed", chatId);
    }

    public List<Project> listRelatedProjects(long chatId) throws SQLException {
        return projectService.listRelatedProjects(chatId);
    }

    public ChatListState.Snapshot filterByRelatedProject(long projectId) throws SQLException {
        ChatFilterCriteria criteria = locked(() -> {
            filterCriteria = filterCriteria.withRelatedProjectId(projectId);
            return filterCriteria;
        });
        List<SearchResult> matches = searchService.searchResults(criteria.query(), criteria.toSearchOptions());
        return locked(() -> listState.showSearchResults(matches, formatFilterStatus(matches.size())));
    }

    public List<Tag> listTags() throws SQLException {
        return tagService.listAll();
    }

    public Tag createTag(String name) throws SQLException {
        return tagService.create(new Tag(0, requireName(name, "Tag name")));
    }

    public ChatListState.Snapshot addTag(long chatId, long tagId) throws SQLException {
        tagService.addToChat(chatId, tagId);
        return refreshCurrent("Tag added", chatId);
    }

    public ChatListState.Snapshot removeTag(long chatId, long tagId) throws SQLException {
        tagService.removeFromChat(chatId, tagId);
        return refreshCurrent("Tag removed", chatId);
    }

    public ChatListState.Snapshot filterByTag(long tagId) throws SQLException {
        ChatFilterCriteria criteria = locked(() -> {
            filterCriteria = filterCriteria.withTagId(tagId);
            return filterCriteria;
        });
        List<SearchResult> matches = searchService.searchResults(criteria.query(), criteria.toSearchOptions());
        return locked(() -> listState.showSearchResults(matches, formatFilterStatus(matches.size())));
    }

    public ChatListState.Snapshot clearFilters() throws SQLException {
        return loadAllChats();
    }

    private ChatListState.Snapshot refreshCurrent(String statusText, long selectedChatId) throws SQLException {
        ChatFilterCriteria criteria = locked(() -> filterCriteria);
        List<SearchResult> matches = searchService.searchResults(criteria.query(), criteria.toSearchOptions());
        
        return locked(() -> {
            if (filterCriteria.isEmpty()) {
                listState.showAll(matches, statusText);
            } else {
                listState.showSearchResults(matches, statusText);
            }
            return listState.select(selectedChatId);
        });
    }

    private <T, E extends Exception> T locked(Locks.Work<T, E> work) throws E {
        return Locks.locked(stateLock, work);
    }



    private static String requireName(String name, String label) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return trimmed;
    }

    private static String formatMatchStatus(int matches) {
        return countLabel(matches, "No matches", "1 match", " matches");
    }

    private static String formatFilterStatus(int matches) {
        return countLabel(matches, "No chats", "1 chat", " chats");
    }

    /** Pluralized count label: {@code none} at 0, {@code singular} at 1, else {@code count + plural}. */
    private static String countLabel(int count, String none, String singular, String plural) {
        if (count == 0) {
            return none;
        }
        if (count == 1) {
            return singular;
        }
        return count + plural;
    }
}
