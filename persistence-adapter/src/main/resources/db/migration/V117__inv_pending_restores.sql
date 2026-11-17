-- Inventory restores staff chose for a player who was not online, applied as the player next joins a server that
-- shares this database. Each row keeps a full copy of the chosen snapshot (its id, cause, capture instant and base64
-- contents), so the retention sweep pruning the snapshot meanwhile does not lose the restore. One row per kept
-- restore, keyed by a client-minted UUID; `player` is the canonical 36-character UUID text, `created_at` epoch
-- milliseconds of the keep, which orders a player's restores. A join deletes each row before it applies the restore,
-- and applies only the rows its own delete removed, so a restore is applied once.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept. MariaDB widens `contents` from its own location (V117.1).
CREATE TABLE ${prefix}inv_pending_restores (
    id                   VARCHAR(36)  NOT NULL,
    player               VARCHAR(36)  NOT NULL,
    snapshot_id          VARCHAR(36)  NOT NULL,
    cause                VARCHAR(16)  NOT NULL,
    snapshot_created_at  BIGINT       NOT NULL,
    contents             TEXT,
    created_at           BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_inv_pending_restores PRIMARY KEY (id)
);

-- Serves the take at join, which reads one player's rows oldest first.
CREATE INDEX ${prefix}idx_inv_pending_restores_player ON ${prefix}inv_pending_restores (player, created_at);
