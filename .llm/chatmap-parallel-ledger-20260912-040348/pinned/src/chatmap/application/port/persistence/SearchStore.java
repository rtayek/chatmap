package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.List;

import chatmap.domain.SearchOptions;
import chatmap.domain.SearchResult;

/** Application-facing full-text search operations. */
public interface SearchStore {

    List<SearchResult> listResults(SearchOptions options) throws SQLException;

    List<SearchResult> searchResultsByMessageText(String query, SearchOptions options) throws SQLException;
}
