-- The plugin's own name-to-uuid index, written on join and read back at enable.
--
-- Paper only consults the server's name cache when the server is in online mode
-- (CraftServer.getOfflinePlayer(String) gates the nameToIdCache read on
-- proxies.isProxyOnlineMode()); on an offline-mode server it derives the uuid from
-- the typed name verbatim, so a name typed in a different case resolves to a uuid
-- nobody owns. This table gives both modes the same case-insensitive resolution.
--
-- lower_name is stored rather than computed at read time because LOWER() over a
-- column is not index-usable the same way across SQLite, MySQL and PostgreSQL, and
-- this index is read on the command path.
--
-- lower_name is deliberately NOT unique: on an online-mode server a name can move
-- between accounts after a name change, and on an offline-mode server two case
-- variants are two different accounts. Both cases mean two uuids may share one
-- lower_name; the reader takes the most recently seen row.
--
-- Same portability contract as V1-V79: the DDL stays in the subset SQLite (the
-- default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific
-- clause. last_seen is epoch milliseconds as a BIGINT, matching every other
-- timestamp in this schema.

CREATE TABLE player_names (
    uuid        VARCHAR(36) NOT NULL,
    name        VARCHAR(16) NOT NULL,
    lower_name  VARCHAR(16) NOT NULL,
    last_seen   BIGINT      NOT NULL,
    CONSTRAINT pk_player_names PRIMARY KEY (uuid)
);

-- The enable-time warm reads the most recently seen rows first and stops at the
-- configured cap, so last_seen leads this index.
CREATE INDEX idx_player_names_last_seen ON player_names (last_seen);

-- The by-name read that the warm cannot serve (a name outside the cap) scans this
-- one rather than the whole table.
CREATE INDEX idx_player_names_lower_name ON player_names (lower_name);
