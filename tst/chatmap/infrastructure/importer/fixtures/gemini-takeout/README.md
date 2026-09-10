# Gemini Takeout fixtures

`conversation-1786837272.json` preserves the full 16-turn structure of the
single conversation in Ray's 2026-09-09 Workspace Takeout export. Prompts,
answers, title, citation labels, and URLs have been replaced with sample
content. Timestamps, indices, field names, and citation counts are retained.

`conversation-1786837273.json` is a synthetic second conversation derived from
that structure, with a different prompt and a two-part response. It tests
multiple-file import and multipart text; it is not a second observed export.

The importer reads these JSON objects from `conversation_<id>.txt` files.
