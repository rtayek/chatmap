package chatmap.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.Project;
import chatmap.domain.SearchOptions;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ChatGptArchiveImportService;
import chatmap.service.ChatGptArchiveImportService.BulkImportResult;
import chatmap.service.ImportService;
import chatmap.service.LiveChatFetchService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
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
    private final ChatListState listState;
    private String currentQuery = "";
    private Long currentProjectId;
    private Long currentTagId;

    public ChatMapController(
            ImportService importService,
            ExportService exportService,
            SearchService searchService,
            ProjectService projectService,
            TagService tagService,
            SummaryService summaryService,
            LiveChatFetchService liveChatFetchService) {
        this(importService, exportService, searchService, projectService, tagService,
                summaryService, liveChatFetchService, null);
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
        this.importService = importService;
        this.exportService = exportService;
        this.searchService = searchService;
        this.projectService = projectService;
        this.tagService = tagService;
        this.summaryService = summaryService;
        this.liveChatFetchService = liveChatFetchService;
        this.archiveImportService = archiveImportService;
        listState = new ChatListState();
    }

    public synchronized ChatListState.Snapshot loadAllChats() throws SQLException {
        currentQuery = "";
        currentProjectId = null;
        currentTagId = null;
        List<SearchResult> results = searchService.searchResults("");
        return listState.showAll(results, "Loaded " + results.size() + " chats.");
    }

    public synchronized ChatListState.Snapshot searchChats(String query) throws SQLException {
        currentQuery = query == null ? "" : query.trim();
        if (currentQuery.isEmpty() && currentProjectId == null && currentTagId == null) {
            return loadAllChats();
        }
        List<SearchResult> matches = currentResults();
        return listState.showSearchResults(matches, formatMatchStatus(matches.size()));
    }

    public synchronized ChatListState.Snapshot importFile(Path file) throws IOException, SQLException {
        Chat imported = importService.importFile(file);
        currentQuery = "";
        currentProjectId = null;
        currentTagId = null;
        listState.showAll(searchService.searchResults(""), "Imported " + imported.title());
        return listState.select(imported.id());
    }

    public synchronized ChatListState.Snapshot importChatGptArchive(Path zipFile) throws Exception {
        if (archiveImportService == null) {
            throw new IllegalStateException("ChatGPT archive import is not configured.");
        }
        BulkImportResult result = archiveImportService.importArchive(zipFile);
        currentQuery = "";
        currentProjectId = null;
        currentTagId = null;
        return listState.showAll(searchService.searchResults(""), result.summary());
    }

    public synchronized ChatListState.Snapshot selectChat(long chatId) {
        return listState.select(chatId);
    }

    /**
     * Fetches the latest live chat (importing it) and selects it, using the same
     * fallback order as the CLI: live provider, else the most recent stored chat.
     * Blocking (HTTP with retry/backoff) — callers should run it off the UI thread.
     */
    public synchronized ChatListState.Snapshot fetchLatestChat() throws Exception {
        LiveChatFetchService.Resolution resolution = liveChatFetchService.resolve(null);
        currentQuery = "";
        currentProjectId = null;
        currentTagId = null;
        listState.showAll(searchService.searchResults(""), "Using " + resolution.how());
        return listState.select(resolution.chatId());
    }

    /**
     * Summarizes and tags the given chat, then re-selects it so the new tags (and
     * summary, via {@link #latestSummary}) are reflected. Blocking (calls the
     * claude CLI) — callers should run it off the UI thread.
     */
    public synchronized ChatListState.Snapshot summarizeAndTag(long chatId) throws Exception {
        summaryService.summarize(chatId);
        return refreshCurrent("Summarized and tagged chat " + chatId, chatId);
    }

    public synchronized Optional<ChatExportModel> loadChatDetails(long chatId) throws SQLException {
        return exportService.loadChat(chatId);
    }

    public synchronized Optional<ChatSummary> latestSummary(long chatId) throws SQLException {
        return summaryService.latestSummary(chatId);
    }

    public synchronized boolean exportChatMarkdown(long chatId, Path outputPath) throws SQLException, IOException {
        return exportService.writeChatMarkdown(chatId, outputPath);
    }

    public synchronized List<Project> listProjects() throws SQLException {
        return projectService.listAll();
    }

    public synchronized Project createProject(String name) throws SQLException {
        String projectName = requireName(name, "Project name");
        String now = Instant.now().toString();
        return projectService.create(new Project(0, projectName, null, now, now));
    }

    public synchronized ChatListState.Snapshot assignProject(long chatId, long projectId) throws SQLException {
        projectService.assignChat(chatId, projectId);
        return refreshCurrent("Project assigned", chatId);
    }

    public synchronized ChatListState.Snapshot clearProject(long chatId) throws SQLException {
        projectService.removeChat(chatId);
        return refreshCurrent("Project cleared", chatId);
    }

    public synchronized ChatListState.Snapshot filterByProject(long projectId) throws SQLException {
        currentProjectId = projectId;
        return filteredSnapshot();
    }

    public synchronized List<Tag> listTags() throws SQLException {
        return tagService.listAll();
    }

    public synchronized Tag createTag(String name) throws SQLException {
        return tagService.create(new Tag(0, requireName(name, "Tag name")));
    }

    public synchronized ChatListState.Snapshot addTag(long chatId, long tagId) throws SQLException {
        tagService.addToChat(chatId, tagId);
        return refreshCurrent("Tag added", chatId);
    }

    public synchronized ChatListState.Snapshot removeTag(long chatId, long tagId) throws SQLException {
        tagService.removeFromChat(chatId, tagId);
        return refreshCurrent("Tag removed", chatId);
    }

    public synchronized ChatListState.Snapshot filterByTag(long tagId) throws SQLException {
        currentTagId = tagId;
        return filteredSnapshot();
    }

    public synchronized ChatListState.Snapshot clearFilters() throws SQLException {
        return loadAllChats();
    }

    private ChatListState.Snapshot filteredSnapshot() throws SQLException {
        List<SearchResult> matches = currentResults();
        return listState.showSearchResults(matches, formatFilterStatus(matches.size()));
    }

    private ChatListState.Snapshot refreshCurrent(String statusText, long selectedChatId) throws SQLException {
        List<SearchResult> matches = currentResults();
        if (currentQuery.isEmpty() && currentProjectId == null && currentTagId == null) {
            listState.showAll(matches, statusText);
        } else {
            listState.showSearchResults(matches, statusText);
        }
        return listState.select(selectedChatId);
    }

    private List<SearchResult> currentResults() throws SQLException {
        return searchService.searchResults(
                currentQuery,
                new SearchOptions(currentProjectId, currentTagId, null));
    }

    private static String requireName(String name, String label) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return trimmed;
    }

    private static String formatMatchStatus(int matches) {
        if (matches == 0) {
            return "No matches";
        }
        if (matches == 1) {
            return "1 match";
        }
        return matches + " matches";
    }

    private static String formatFilterStatus(int matches) {
        if (matches == 0) {
            return "No chats";
        }
        if (matches == 1) {
            return "1 chat";
        }
        return matches + " chats";
    }
}
