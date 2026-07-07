package chatmap.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import chatmap.domain.Chat;
import chatmap.domain.SearchOptions;
import chatmap.storage.ChatRepository;
import chatmap.storage.SearchRepository;

/** Coordinates search without exposing SQL to callers. */
public final class SearchService {

    private final ChatRepository chats;
    private final SearchRepository search;

    public SearchService(ChatRepository chats, SearchRepository search) {
        this.chats = chats;
        this.search = search;
    }

    public List<Chat> searchChats(String query) throws SQLException {
        return searchChats(query, SearchOptions.none());
    }

    public List<Chat> searchChats(String query, SearchOptions options) throws SQLException {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return chats.findAll();
        }
        return search.searchChatsByMessageText(toFtsPrefixQuery(trimmed), options);
    }

    private static String toFtsPrefixQuery(String query) {
        List<String> tokens = new ArrayList<>();
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (!token.isBlank()) {
                tokens.add(token + "*");
            }
        }
        if (tokens.isEmpty()) {
            return "";
        }
        return String.join(" OR ", tokens);
    }
}
