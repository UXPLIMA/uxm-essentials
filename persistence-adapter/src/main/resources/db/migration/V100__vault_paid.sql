-- What a vault's owner paid to create it, and in which currency. The delete refund is capped by this
-- payment, so a refund configured above the create cost (or a vault that was free) can never pay out more
-- than went in. Both columns are nullable with no default: NULL means no payment, which is what a free vault
-- stores and also how every vault that already exists reads. Those vaults were created before payments were
-- recorded, so what they cost is unknown; capping them at today's cost-to-create could repay a vault that was
-- free (the bypass node, an earlier zero cost), so they refund nothing.
--
-- DECIMAL(20, 4) is the economy's own amount shape. Plain ALTER, CREATE TABLE and CREATE INDEX statements run
-- unchanged on SQLite, MySQL/MariaDB and PostgreSQL.
ALTER TABLE ${prefix}vaults ADD COLUMN paid_amount DECIMAL(20, 4);
ALTER TABLE ${prefix}vaults ADD COLUMN paid_currency VARCHAR(32);

-- Items of a vault window whose vault was deleted while it was open, kept while the owner is offline and handed back
-- on their next join, so a delete never destroys them and never brings the vault back. The items keep the vaults
-- table's own form, base64 text of the serialized item bytes, because a vault holds enchanted, named and filled items
-- that a material id and a count could not describe. One row per batch, keyed by a client-minted UUID like the other
-- give-back queues; `owner` is the canonical 36-character UUID text, `created_at` epoch milliseconds.
CREATE TABLE ${prefix}vault_item_returns (
    id          VARCHAR(36)  NOT NULL,
    owner       VARCHAR(36)  NOT NULL,
    contents    TEXT         NOT NULL,
    created_at  BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_vault_item_returns PRIMARY KEY (id)
);

-- Serves the take-all on join, which reads and deletes one owner's rows.
CREATE INDEX ${prefix}idx_vault_item_returns_owner ON ${prefix}vault_item_returns (owner);
