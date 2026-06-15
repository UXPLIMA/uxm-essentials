-- Adds FancyHolograms-style ITEM and BLOCK hologram types to the hologram store. As with V35 and V36, every
-- column is nullable with no DEFAULT clause so the ALTER stays portable across SQLite, MySQL/MariaDB and
-- PostgreSQL; an absent value reads back as NULL and the mapper resolves a NULL type to TEXT, so existing rows
-- keep rendering their text lines with no data migration. A TEXT hologram leaves item_material / block_data
-- NULL; an ITEM hologram carries a Material name in item_material; a BLOCK hologram carries a BlockData string
-- (e.g. minecraft:oak_log[axis=y]) in block_data.
--
--   type           the hologram type enum name (TEXT / ITEM / BLOCK); NULL = TEXT
--   item_material  the Material name an ITEM hologram shows (NULL for TEXT / BLOCK)
--   block_data     the BlockData string a BLOCK hologram shows (NULL for TEXT / ITEM)

ALTER TABLE holograms ADD COLUMN type VARCHAR(16);
ALTER TABLE holograms ADD COLUMN item_material VARCHAR(128);
ALTER TABLE holograms ADD COLUMN block_data VARCHAR(256);
