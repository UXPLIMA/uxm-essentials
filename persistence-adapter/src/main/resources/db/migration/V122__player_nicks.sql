-- The players' `/nick` display names, shared by every backend of one database, so a nick survives a relog and follows
-- the player to every backend of the network. Each backend holds the nicks of the players online on it in memory, read
-- when they log in and written back when they change; these rows are what the next backend reads.
--
-- One row per player who ever set a nick. `nick` is the display name, empty after `/nick off`, and `lower_nick` the
-- same in lower case, so a nick can be found in any case and two players cannot take one name in different cases.
-- `changed_at` is when the player set it, in epoch milliseconds; a write older than the row is dropped, so two
-- backends writing at once keep the newer nick.
--
-- UUIDs are the canonical 36-character text. Same portability contract as the earlier migrations: plain CREATE TABLE
-- and CREATE INDEX in the subset SQLite, MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}player_nicks (
    player_uuid VARCHAR(36) NOT NULL,
    nick        VARCHAR(32) NOT NULL,
    lower_nick  VARCHAR(32) NOT NULL,
    changed_at  BIGINT      NOT NULL,
    CONSTRAINT ${prefix}pk_player_nicks PRIMARY KEY (player_uuid)
);

CREATE INDEX ${prefix}idx_player_nicks_lower ON ${prefix}player_nicks (lower_nick);
