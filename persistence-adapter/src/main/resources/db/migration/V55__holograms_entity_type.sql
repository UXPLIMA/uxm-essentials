-- Adds the ENTITY hologram content kind to the V13 hologram store: a frozen, decorative mob shown as the
-- hologram. The renderer spawns the named entity type AI-less and weightless. The column is nullable with no
-- DEFAULT so the ALTER stays portable across SQLite, MySQL/MariaDB and PostgreSQL; only an ENTITY hologram
-- carries a value, every other type reads back NULL, so existing rows are untouched with no data migration.
--
--   entity_type   the Bukkit entity-type name shown by an ENTITY hologram, or NULL for other types

ALTER TABLE holograms ADD COLUMN entity_type VARCHAR(64);
