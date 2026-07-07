package chatmap.importer;

final class ChatGptJsonFixture {

    static final String json = """
            {
              "title": "ChatGPT Import Sample",
              "create_time": 1720000000.0,
              "update_time": 1720000300.0,
              "mapping": {
                "systemNode": {
                  "message": {
                    "id": "systemMessage",
                    "author": { "role": "system" },
                    "create_time": 1720000001.0,
                    "content": { "content_type": "text", "parts": ["You are concise."] }
                  }
                },
                "userNode": {
                  "message": {
                    "id": "userMessage",
                    "author": { "role": "user" },
                    "create_time": 1720000010.0,
                    "content": { "content_type": "text", "parts": ["Please explain SQLite FTS5.", "Keep it practical."] }
                  }
                },
                "assistantNode": {
                  "message": {
                    "id": "assistantMessage",
                    "author": { "role": "assistant" },
                    "create_time": 1720000020.0,
                    "content": { "content_type": "text", "parts": ["SQLite FTS5 indexes text for fast search."] }
                  }
                },
                "toolNode": {
                  "message": {
                    "id": "toolMessage",
                    "author": { "role": "tool" },
                    "content": { "content_type": "text", "parts": ["Tool payload without timestamp."] }
                  }
                }
              }
            }
            """;

    private ChatGptJsonFixture() {
    }
}
