-- Rank action lines kept for a player who was not on the server that ranked them up (they left while the rank was
-- saved, moved to another backend, or were promoted while offline), run on their next join on any server. One row per
-- line, keyed by a client-minted UUID; `owner` is the canonical 36-character UUID text, `created_at` epoch
-- milliseconds and `seq` the line's place in its rank's list, which together keep the lines in order. A join
-- deletes each row before it runs the line, and runs only the rows its own delete removed, so two servers never both
-- run one line.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}rank_pending_actions (
    id          VARCHAR(36)  NOT NULL,
    owner       VARCHAR(36)  NOT NULL,
    created_at  BIGINT       NOT NULL,
    seq         INT          NOT NULL,
    action_line TEXT         NOT NULL,
    CONSTRAINT ${prefix}pk_rank_pending_actions PRIMARY KEY (id)
);

-- Serves the read on join, which reads one owner's rows.
CREATE INDEX ${prefix}idx_rank_pending_actions_owner ON ${prefix}rank_pending_actions (owner);
