-- Kit resets kept on a single server for a player who was offline when an operator ran /kit reset. Their one-time
-- stamps live in their own player data, which only exists while they are online, so the reset is applied as they next
-- join. One row per reset, keyed by a client-minted UUID; `player` is the canonical 36-character UUID text, `kit` the
-- kit id or `*` for every kit, `created_at` epoch milliseconds. The pre-login deletes each row before it holds the reset
-- for the join, and holds only the rows its own delete removed.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}kit_pending_resets (
    id          VARCHAR(36)  NOT NULL,
    player      VARCHAR(36)  NOT NULL,
    kit         VARCHAR(64)  NOT NULL,
    created_at  BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_kit_pending_resets PRIMARY KEY (id)
);

-- Serves the read at pre-login, which reads one player's rows.
CREATE INDEX ${prefix}idx_kit_pending_resets_player ON ${prefix}kit_pending_resets (player);
