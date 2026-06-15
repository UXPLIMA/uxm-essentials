-- Adds FancyHolograms-style per-player visibility and a visibility distance to the hologram store.
-- As with V35, every column is nullable with no DEFAULT clause so the ALTER stays portable across SQLite,
-- MySQL/MariaDB and PostgreSQL; an absent value reads back as NULL and the mapper resolves that to
-- Visibility.everyone() (mode ALL, no node, distance 0 = the vanilla view range), so existing rows stay
-- visible to everyone with no data migration.
--
--   visibility_mode        the visibility mode enum name (ALL / PERMISSION); NULL = ALL
--   visibility_permission  the node a player must hold for PERMISSION mode (NULL = none / not gated)
--   visibility_distance    the visibility radius in blocks, 0/NULL = unlimited (vanilla tracking range)

ALTER TABLE holograms ADD COLUMN visibility_mode VARCHAR(16);
ALTER TABLE holograms ADD COLUMN visibility_permission VARCHAR(128);
ALTER TABLE holograms ADD COLUMN visibility_distance INT;
