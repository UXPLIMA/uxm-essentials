-- Adds the HEAD hologram content kind to the V13 hologram store: a base64 skin-texture property value, the
-- same shape the NPC store keeps in its skin_texture column (V38). A HEAD hologram renders as an ItemDisplay
-- showing a PLAYER_HEAD built from this texture. The column is nullable with no DEFAULT so the ALTER stays
-- portable across SQLite, MySQL/MariaDB and PostgreSQL; only a HEAD hologram carries a value, every other
-- type reads back NULL, so existing rows are untouched with no data migration.
--
--   head_texture   the base64 "textures" property value of the head's skin (NULL for non-HEAD holograms)

ALTER TABLE holograms ADD COLUMN head_texture TEXT;
