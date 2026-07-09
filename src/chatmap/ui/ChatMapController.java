package chatmap.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.domain.SearchOptions;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
import chatmap.service.TagService;

/** Coordinates application operations without depending on JavaFX widgets. */
public final class ChatMapController {

    private final ImportService importService;
    private final ExportService exportService;
    private final SearchService searchService;
    private final ProjectService projectService;
    private final TagService tagService;
    private final ChatListState listState;
    private String currentQuery = "";
    private Long currentProjectId;
    private Long currentTagId;

    public ChatMapController(
            ImportService importService,
            ExportService exportService,
            SearchService searchService,
            ProjectService projectService,
            TagService tagService) {
        this.importService = importService;
        this.exportService = exportService;
        this.searchService = searchService;
        this.projectService = projectService;
        this.tagService = tagService;
        listState = new ChatListState();
    }

    public ChatListState.Snapshot loadAllChats() throws SQLException {
        currentQuery = "";
        currentProjectId = null;
        currentTagId = null;
        return listState.showAll(searchService.searchResults(""), "Ready");
    }

    public ChatListState.Snapshot searchChats(String query) throws SQLException {
        currentQuery = query == null ? "" : query.trim();
        if (currentQuery.isEmpty() && currentProjectId == null && currentTagId == null) {
            return loadAllChats();
        }
        List<SearchResult> matches = currentResults();
        return listState.showSearchResults(matches, formatMatchStatus(matches.size()));
    }

    public ChatListState.Snapshot importFile(Path file) throws IOException, SQLException {
        Chat imported = importService.importFile(file);
        currentQuery = "";
        currentProjectId = null;
        currentTagId = null;
        listState.showAll(searchService.searchResults(""), "Imported " + imported.title());
        return listState.select(imported.id());
    }

    public ChatListState.Snapshot selectChat(long chatId) {
        return listState.select(chatId);
    }

    public Optional<ChatExportModel> loadChatDetails(long chatId) throws SQLException {
        return exportService.loadChat(chatId);
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
        currentProjectId = projectId;
        return filteredSnapshot();
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
        currentTagId = tagId;
        return filteredSnapshot();
    }

    public ChatListState.Snapshot clearFilters() throws SQLException {
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
