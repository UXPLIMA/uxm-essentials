-- Catalogue lines kept for players who were not online when they were meant for them, such as being paid by /pay,
-- shown when the player is next online on any server that shares this database. One row per kept line, keyed by a
-- client-minted UUID. `recipient` is the canonical 36-character UUID text, `message_key` the catalogue key, `args`
-- the values it is filled with as name=value pairs joined by '&' with each side percent-encoded as UTF-8, and
-- `created_at` epoch milliseconds, which orders a player's lines. A server deletes each row before it shows the line
-- and shows only the rows its own delete removed, so a line is shown once.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}player_notices (
    id           VARCHAR(36)   NOT NULL,
    recipient    VARCHAR(36)   NOT NULL,
    message_key  VARCHAR(128)  NOT NULL,
    args         TEXT          NOT NULL,
    created_at   BIGINT        NOT NULL,
    CONSTRAINT ${prefix}pk_player_notices PRIMARY KEY (id)
);

-- Serves the take, which reads one player's rows oldest first.
CREATE INDEX ${prefix}idx_player_notices_recipient ON ${prefix}player_notices (recipient, created_at);
