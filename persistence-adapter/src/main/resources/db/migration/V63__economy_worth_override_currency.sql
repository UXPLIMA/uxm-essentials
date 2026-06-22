-- Adds the pay-out currency to a /setworth override. The override store was
-- price-only (V12): /setworth wrote a material and a decimal price and every
-- override paid out in the default currency, even though the config worth.items
-- list could already name a currency per item. This column lets an operator price
-- an item in any configured currency from in-game too, so the two worth sources
-- (config table and override store) carry the same multi-currency capability.
--
-- The default 'default' matches the warps.cost_currency column (V19): an existing
-- row predating this migration reads back as the default currency, which the
-- combining worth source resolves against the registry exactly as it did before,
-- so the upgrade is transparent to any price set under V12. VARCHAR(36) is the
-- same width every currency id is stored at elsewhere.
--
-- Same portability contract as V1-V62: a plain ALTER TABLE ADD COLUMN the subset
-- SQLite (the default), MySQL/MariaDB and PostgreSQL all accept, with no
-- dialect-specific clause. jOOQ's DDLDatabase parses this file alongside the rest
-- at build time, so the generated EconomyWorthOverrides table gains the column.

ALTER TABLE economy_worth_overrides ADD COLUMN currency VARCHAR(36) DEFAULT 'default' NOT NULL;
