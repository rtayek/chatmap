package chatmap.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.ConversationInventory;
import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ChatGptArchiveImportService;
import chatmap.service.ChatGptArchiveImportService.BulkImportResult;
import chatmap.service.ConversationInventoryService;
import chatmap.service.ImportService;
import chatmap.service.LiveChatFetchService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
import chatmap.service.ServiceGraph;
import chatmap.service.SummaryService;
import chatmap.service.TagService;

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
    private final ChatListState listState;
    private final Object stateLock = new Object();
    private final Object databaseLock;

    private ChatFilterCriteria filterCriteria = ChatFilterCriteria.EMPTY;

    public ChatMapController(
            ImportService importService,
            ExportService exportService,
            SearchService searchService,
            ProjectService projectService,
            TagService tagService,
            SummaryService summaryService,
            LiveChatFetchService liveChatFetchService) {
        this(importService, exportService, searchService, projectService, tagService,
                summaryService, liveChatFetchService, null, null, new Object());
    }

    public ChatMapController(
            ImportService importService,
            ExportService exportService,
            SearchService searchService,
            ProjectService projectService,
            TagService tagService,
            SummaryService summaryService,
            LiveChatFetchService liveChatFetchService,
            ChatGptArchiveImportService archiveImportService) {
        this(importService, exportService, searchService, projectService, tagService,
                summaryService, liveChatFetchService, archiveImportService, null, new Object());
    }

    public ChatMapController(
            ImportService importService,
            ExportService exportService,
            SearchService searchService,
            ProjectService projectService,
            TagService tagService,
            SummaryService summaryService,
            LiveChatFetchService liveChatFetchService,
            ChatGptArchiveImportService archiveImportService,
            ConversationInventoryService conversationInventoryService) {
        this(importService, exportService, searchService, projectService, tagService, summaryService,
                liveChatFetchService, archiveImportService, conversationInventoryService, new Object());
    }

    private ChatMapController(
            ImportService importService, ExportService exportService, SearchService searchService,
            ProjectService projectService, TagService tagService, SummaryService summaryService,
            LiveChatFetchService liveChatFetchService, ChatGptArchiveImportService archiveImportService,
            ConversationInventoryService conversationInventoryService, Object databaseLock) {
        this.importService = importService;
        this.exportService = exportService;
        this.searchService = searchService;
        this.projectService = projectService;
        this.tagService = tagService;
        this.summaryService = summaryService;
        this.liveChatFetchService = liveChatFetchService;
        this.archiveImportService = archiveImportService;
        this.conversationInventoryService = conversationInventoryService;
        this.databaseLock = databaseLock;
        listState = new ChatListState();
    }

    /** Wires the controller from the shared {@link ServiceGraph} — the production path. */
    public ChatMapController(ServiceGraph services) {
        this(services.importService(), services.exportService(), services.searchService(),
                services.projectService(), services.tagService(), services.summaryService(),
                services.liveChatFetchService(), services.archiveImportService(),
                services.conversationInventoryService(), services.databaseLock());
    }

    public ChatFilterCriteria filterCriteria() {
        synchronized (stateLock) {
            return filterCriteria;
        }
    }

    public ChatListState.Snapshot loadAllChats() throws SQLException {
        synchronized (databaseLock) {
            List<SearchResult> results = searchService.searchResults("");
        synchronized (stateLock) {
            resetFiltersLocked();
            return listState.showAll(results, "Loaded " + results.size() + " chats.");
            }
        }
    }

    public ChatListState.Snapshot searchChats(String query) throws SQLException {
        synchronized (databaseLock) { synchronized (stateLock) {
            filterCriteria = filterCriteria.withQuery(query);
            if (filterCriteria.isEmpty()) {
                return loadAllChatsInternal();
            }
            List<SearchResult> matches = currentResultsLocked();
            return listState.showSearchResults(matches, formatMatchStatus(matches.size()));
        } }
    }

    public ChatListState.Snapshot importFile(Path file) throws IOException, SQLException {
        synchronized (databaseLock) { Chat imported = importService.importFile(file);
        List<SearchResult> results = searchService.searchResults("");
        synchronized (stateLock) {
            resetFiltersLocked();
            listState.showAll(results, "Imported " + imported.title());
            return listState.select(imported.id());
        } }
    }

    public ChatListState.Snapshot importChatGptArchive(Path zipFile) throws Exception {
        synchronized (databaseLock) {
            if (archiveImportService == null) {
                throw new IllegalStateException("ChatGPT archive import is not configured.");
            }
            BulkImportResult result = archiveImportService.importArchive(zipFile);
            List<SearchResult> results = searchService.searchResults("");
            synchronized (stateLock) {
                resetFiltersLocked();
                return listState.showAll(results, result.summary());
            }
        }
    }

    public ConversationInventory conversationInventory() throws Exception {
        if (conversationInventoryService == null) {
            throw new IllegalStateException("Conversation inventory is not configured.");
        }
        synchronized (databaseLock) { return conversationInventoryService.inventory(); }
    }

    public ChatListState.Snapshot selectChat(long chatId) {
        synchronized (stateLock) {
            return listState.select(chatId);
        }
    }

    /**
     * Fetches the latest live chat (importing it) and selects it, using the same
     * fallback order as the CLI: live provider, else the most recent stored chat.
     * Blocking (HTTP with retry/backoff) — callers should run it off the UI thread.
     * Long-running provider checks run before the database lock is acquired to keep UI reads responsive.
     */
    public ChatListState.Snapshot fetchLatestChat() throws Exception {
        LiveChatFetchService.Resolution resolution = liveChatFetchService.resolve(null);
        synchronized (databaseLock) { List<SearchResult> results = searchService.searchResults("");
        synchronized (stateLock) {
            resetFiltersLocked();
            listState.showAll(results, "Using " + resolution.how());
            return listState.select(resolution.chatId());
        } }
    }

    /**
     * Summarizes and tags the given chat, then re-selects it so the new tags (and
     * summary, via {@link #latestSummary}) are reflected. Blocking (calls the
     * claude CLI) — callers should run it off the UI thread.
     * The database lock covers the complete operation so the shared JDBC connection cannot be
     * used concurrently; the UI invokes this method on the background executor.
     */
    public ChatListState.Snapshot summarizeAndTag(long chatId) throws Exception {
        synchronized (databaseLock) { summaryService.summarize(chatId);
            return refreshCurrent("Summarized and tagged chat " + chatId, chatId); }
    }

    public Optional<ChatExportModel> loadChatDetails(long chatId) throws SQLException {
        synchronized (databaseLock) { return exportService.loadChat(chatId); }
    }

    public Optional<ChatSummary> latestSummary(long chatId) throws SQLException {
        synchronized (databaseLock) { return summaryService.latestSummary(chatId); }
    }

    public boolean exportChatMarkdown(long chatId, Path outputPath) throws SQLException, IOException {
        synchronized (databaseLock) { return exportService.writeChatMarkdown(chatId, outputPath); }
    }

    public List<Project> listProjects() throws SQLException {
        synchronized (databaseLock) { return projectService.listAll(); }
    }

    public Project createProject(String name) throws SQLException {
        String projectName = requireName(name, "Project name");
        String now = Instant.now().toString();
        synchronized (databaseLock) { return projectService.create(new Project(0, projectName, null, now, now)); }
    }

    public ChatListState.Snapshot assignProject(long chatId, long projectId) throws SQLException {
        synchronized (databaseLock) { projectService.assignChat(chatId, projectId);
            return refreshCurrent("Project assigned", chatId); }
    }

    public ChatListState.Snapshot clearProject(long chatId) throws SQLException {
        synchronized (databaseLock) { projectService.removeChat(chatId);
            return refreshCurrent("Project cleared", chatId); }
    }

    public ChatListState.Snapshot filterByProject(long projectId) throws SQLException {
        synchronized (databaseLock) { synchronized (stateLock) {
            filterCriteria = filterCriteria.withProjectId(projectId);
            return filteredSnapshotLocked();
        } }
    }

    public List<Tag> listTags() throws SQLException {
        synchronized (databaseLock) { return tagService.listAll(); }
    }

    public Tag createTag(String name) throws SQLException {
        synchronized (databaseLock) { return tagService.create(new Tag(0, requireName(name, "Tag name"))); }
    }

    public ChatListState.Snapshot addTag(long chatId, long tagId) throws SQLException {
        synchronized (databaseLock) { tagService.addToChat(chatId, tagId);
            return refreshCurrent("Tag added", chatId); }
    }

    public ChatListState.Snapshot removeTag(long chatId, long tagId) throws SQLException {
        synchronized (databaseLock) { tagService.removeFromChat(chatId, tagId);
            return refreshCurrent("Tag removed", chatId); }
    }

    public ChatListState.Snapshot filterByTag(long tagId) throws SQLException {
        synchronized (databaseLock) { synchronized (stateLock) {
            filterCriteria = filterCriteria.withTagId(tagId);
            return filteredSnapshotLocked();
        } }
    }

    public ChatListState.Snapshot clearFilters() throws SQLException {
        return loadAllChats();
    }

    private ChatListState.Snapshot loadAllChatsInternal() throws SQLException {
        resetFiltersLocked();
        List<SearchResult> results = searchService.searchResults("");
        return listState.showAll(results, "Loaded " + results.size() + " chats.");
    }

    /** Clears the active search query and project/tag filters. Caller must hold {@code stateLock}. */
    private void resetFiltersLocked() {
        filterCriteria = ChatFilterCriteria.EMPTY;
    }

    private ChatListState.Snapshot filteredSnapshotLocked() throws SQLException {
        List<SearchResult> matches = currentResultsLocked();
        return listState.showSearchResults(matches, formatFilterStatus(matches.size()));
    }

    private ChatListState.Snapshot refreshCurrent(String statusText, long selectedChatId) throws SQLException {
        synchronized (stateLock) {
            List<SearchResult> matches = currentResultsLocked();
            if (filterCriteria.isEmpty()) {
                listState.showAll(matches, statusText);
            } else {
                listState.showSearchResults(matches, statusText);
            }
            return listState.select(selectedChatId);
        }
    }

    private List<SearchResult> currentResultsLocked() throws SQLException {
        return searchService.searchResults(
                filterCriteria.query(),
                filterCriteria.toSearchOptions());
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
