-- ChatMap SQLite schema
-- Loaded and executed by chatmap.storage.Database.
--
-- NOTE: PRAGMA foreign_keys is a per-connection setting in SQLite and is
-- enabled by Database.java on every connection. It cannot be set here once
-- and persist.
--
-- Statements are separated by ';' at end of line. Database.java splits on
-- that, so keep one statement per semicolon and avoid ';' inside literals.

CREATE TABLE IF NOT EXISTS projects (
    id          INTEGER PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS chats (
    id          INTEGER PRIMARY KEY,
    project_id  INTEGER REFERENCES projects(id) ON DELETE SET NULL,
    source      TEXT NOT NULL,
    title       TEXT NOT NULL,
    created_at  TEXT,
    updated_at  TEXT,
    imported_at TEXT NOT NULL,
    archived    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_chats_project ON chats(project_id);

CREATE TABLE IF NOT EXISTS messages (
    id        INTEGER PRIMARY KEY,
    chat_id   INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    role      TEXT NOT NULL,
    text      TEXT NOT NULL,
    sequence  INTEGER NOT NULL,
    timestamp TEXT,
    raw_json  TEXT
);

CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(chat_id, sequence);

CREATE TABLE IF NOT EXISTS tags (
    id   INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE IF NOT EXISTS chat_tags (
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    tag_id  INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (chat_id, tag_id)
);

-- ---------------------------------------------------------------------------
-- Full-text search: external-content FTS5 table over messages.text.
--
-- The durable text lives in `messages`; `messages_fts` is only an index.
-- With content='messages', FTS5 reads row content from the messages table,
-- so the index must be kept in sync via the triggers below.
--
-- IMPORTANT: for external-content tables, deletions/updates must go through
-- the special 'delete' command:
--   INSERT INTO messages_fts(messages_fts, rowid, text) VALUES('delete', ...)
-- A plain DELETE FROM messages_fts does NOT work correctly.
-- ---------------------------------------------------------------------------

CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
    text,
    content='messages',
    content_rowid='id'
);

CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
    INSERT INTO messages_fts(rowid, text) VALUES (new.id, new.text);
END;

CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, text) VALUES ('delete', old.id, old.text);
END;

CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE OF text ON messages BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, text) VALUES ('delete', old.id, old.text);
    INSERT INTO messages_fts(rowid, text) VALUES (new.id, new.text);
END;
