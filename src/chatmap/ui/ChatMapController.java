package chatmap.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.SearchService;

/** Coordinates application operations without depending on JavaFX widgets. */
public final class ChatMapController {

    private final ImportService importService;
    private final ExportService exportService;
    private final SearchService searchService;
    private final ChatListState listState;

    public ChatMapController(
            ImportService importService,
            ExportService exportService,
            SearchService searchService) {
        this.importService = importService;
        this.exportService = exportService;
        this.searchService = searchService;
        listState = new ChatListState();
    }

    public ChatListState.Snapshot loadAllChats() throws SQLException {
        return listState.showAll(searchService.searchResults(""), "Ready");
    }

    public ChatListState.Snapshot searchChats(String query) throws SQLException {
        if (query == null || query.trim().isEmpty()) {
            return loadAllChats();
        }
        var matches = searchService.searchResults(query);
        return listState.showSearchResults(matches, formatMatchStatus(matches.size()));
    }

    public ChatListState.Snapshot importFile(Path file) throws IOException, SQLException {
        Chat imported = importService.importFile(file);
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

    private static String formatMatchStatus(int matches) {
        if (matches == 0) {
            return "No matches";
        }
        if (matches == 1) {
            return "1 match";
        }
        return matches + " matches";
    }
}
