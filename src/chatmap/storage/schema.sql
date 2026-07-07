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
    createdAt   TEXT NOT NULL,
    updatedAt   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS chats (
    id          INTEGER PRIMARY KEY,
    projectId   INTEGER REFERENCES projects(id) ON DELETE SET NULL,
    source      TEXT NOT NULL,
    title       TEXT NOT NULL,
    createdAt   TEXT,
    updatedAt   TEXT,
    importedAt  TEXT NOT NULL,
    archived    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS chatsProjectIndex ON chats(projectId);

CREATE TABLE IF NOT EXISTS messages (
    id        INTEGER PRIMARY KEY,
    chatId    INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    role      TEXT NOT NULL,
    text      TEXT NOT NULL,
    sequence  INTEGER NOT NULL,
    timestamp TEXT,
    rawJson   TEXT
);

CREATE INDEX IF NOT EXISTS messagesChatIndex ON messages(chatId, sequence);

CREATE TABLE IF NOT EXISTS tags (
    id   INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE INDEX IF NOT EXISTS tagsNameIndex ON tags(name COLLATE NOCASE);

CREATE TABLE IF NOT EXISTS chatTags (
    chatId INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    tagId  INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (chatId, tagId)
);

CREATE INDEX IF NOT EXISTS chatTagsTagIndex ON chatTags(tagId);

-- ---------------------------------------------------------------------------
-- Full-text search: external-content FTS5 table over messages.text.
--
-- The durable text lives in `messages`; `messageFts` is only an index.
-- With content='messages', FTS5 reads row content from the messages table,
-- so the index must be kept in sync via the triggers below.
--
-- IMPORTANT: for external-content tables, deletions/updates must go through
-- the special 'delete' command:
--   INSERT INTO messageFts(messageFts, rowid, text) VALUES('delete', ...)
-- A plain DELETE FROM messageFts does NOT work correctly.
-- ---------------------------------------------------------------------------

CREATE VIRTUAL TABLE IF NOT EXISTS messageFts USING fts5(
    text,
    content='messages',
    content_rowid='id'
);

CREATE TRIGGER IF NOT EXISTS messagesAfterInsert AFTER INSERT ON messages BEGIN
    INSERT INTO messageFts(rowid, text) VALUES (new.id, new.text);
END;

CREATE TRIGGER IF NOT EXISTS messagesAfterDelete AFTER DELETE ON messages BEGIN
    INSERT INTO messageFts(messageFts, rowid, text) VALUES ('delete', old.id, old.text);
END;

CREATE TRIGGER IF NOT EXISTS messagesAfterUpdate AFTER UPDATE OF text ON messages BEGIN
    INSERT INTO messageFts(messageFts, rowid, text) VALUES ('delete', old.id, old.text);
    INSERT INTO messageFts(rowid, text) VALUES (new.id, new.text);
END;
