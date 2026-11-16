-- Items a same-server trade had to hand back to a player who was offline or unreachable by then (they left while the
-- trade settled, or the server stopped with the trade open), kept and handed over on their next join on any server.
-- The items keep the vaults' serialized form, base64 text of each stack's own bytes, because a trade moves
-- enchanted, named and filled items that a material id and a count could not describe. One row per batch, keyed by a
-- client-minted UUID like the other give-back queues; `owner` is the canonical 36-character UUID text, `created_at`
-- epoch milliseconds.
--
-- A delivery claims a row before it hands the items over and deletes it only after: `claimed_by` is the claiming
-- delivery's token and `claimed_at` when it claimed, both NULL while the row waits. A claim older than the stale
-- window can be taken again, so a server that crashed between the claim and the hand-over does not strand the row.
-- `contents` is TEXT like `vault_item_returns`, which caps a batch at 64 KB on MySQL and MariaDB.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}trade_item_returns (
    id          VARCHAR(36)  NOT NULL,
    owner       VARCHAR(36)  NOT NULL,
    contents    TEXT         NOT NULL,
    created_at  BIGINT       NOT NULL,
    claimed_by  VARCHAR(36),
    claimed_at  BIGINT,
    CONSTRAINT ${prefix}pk_trade_item_returns PRIMARY KEY (id)
);

-- Serves the read on join, which reads one owner's rows.
CREATE INDEX ${prefix}idx_trade_item_returns_owner ON ${prefix}trade_item_returns (owner);
