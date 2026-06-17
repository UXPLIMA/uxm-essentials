-- Adds a click action to the V13 hologram store: a command run when a player clicks the hologram. The renderer
-- spawns an Interaction entity beside a hologram that carries a click command, and the click listener runs the
-- command as the clicking player. The column is nullable with no DEFAULT so the ALTER stays portable across
-- SQLite, MySQL/MariaDB and PostgreSQL; a NULL means the hologram is not clickable (no Interaction entity is
-- spawned), so existing rows are untouched with no data migration.
--
--   click_command   the command run (as the clicking player) when the hologram is clicked, or NULL = not clickable

ALTER TABLE holograms ADD COLUMN click_command TEXT;
