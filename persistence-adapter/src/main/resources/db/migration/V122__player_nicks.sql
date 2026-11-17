-- The players' `/nick` display names, shared by every backend of one database, so a nick survives a relog and follows
-- the player to every backend of the network. Each backend holds the nicks of the players online on it in memory, read
-- when they log in; a change is written here first and then announced, and these rows are what the next backend reads.
--
-- One row per player who ever set a nick. `nick` is the display name, empty after `/nick off`. `lower_nick` is the
-- same in lower case, NULL when there is none; its unique index lets one player at most hold a nick in any case, so
-- two backends claiming one nick at once cannot both have it, while every cleared row holds NULL side by side.
-- `version` counts the writes to the row: a write names the version it read and is refused when the row moved on, so
-- the order of writes never rests on the clocks of the backends.
--
-- UUIDs are the canonical 36-character text. Same portability contract as the earlier migrations: plain CREATE TABLE
-- and CREATE UNIQUE INDEX in the subset SQLite, MySQL/MariaDB and PostgreSQL all accept, each of which lets a unique
-- index hold many NULLs.
CREATE TABLE ${prefix}player_nicks (
    player_uuid VARCHAR(36) NOT NULL,
    nick        VARCHAR(32) NOT NULL,
    lower_nick  VARCHAR(32),
    version     BIGINT      NOT NULL,
    CONSTRAINT ${prefix}pk_player_nicks PRIMARY KEY (player_uuid)
);

CREATE UNIQUE INDEX ${prefix}ux_player_nicks_lower ON ${prefix}player_nicks (lower_nick);
