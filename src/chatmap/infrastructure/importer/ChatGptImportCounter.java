package chatmap.infrastructure.importer;


import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks statistics for unsupported content parts during ChatGPT import. */
final class ChatGptImportCounter {

    int unsupportedContentParts;

    final Map<String, Integer> unsupportedContentCategories = new LinkedHashMap<>();

    void countUnsupported(String category) {
        unsupportedContentParts++;
        unsupportedContentCategories.merge(category, 1, Integer::sum);
    }
}
