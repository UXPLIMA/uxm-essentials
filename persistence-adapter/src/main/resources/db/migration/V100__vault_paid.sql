-- What a vault's owner paid to create it, and in which currency. The delete refund is capped by this
-- payment, so a refund configured above the create cost (or a vault that was free) can never pay out more
-- than went in. Both columns are nullable with no default: NULL means no payment, which is what a free vault
-- stores and also how every vault that already exists reads. Those vaults were created before payments were
-- recorded, so what they cost is unknown; capping them at today's cost-to-create could repay a vault that was
-- free (the bypass node, an earlier zero cost), so they refund nothing.
--
-- DECIMAL(20, 4) is the economy's own amount shape. Plain ALTER statements run unchanged on SQLite,
-- MySQL/MariaDB and PostgreSQL.
ALTER TABLE ${prefix}vaults ADD COLUMN paid_amount DECIMAL(20, 4);
ALTER TABLE ${prefix}vaults ADD COLUMN paid_currency VARCHAR(32);
