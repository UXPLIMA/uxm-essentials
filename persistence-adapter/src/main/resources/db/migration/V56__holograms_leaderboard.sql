-- Adds the leaderboard configuration to the V13 hologram store: which ranked data source a hologram shows and
-- how many top rows. When a provider is set, the renderer regenerates the hologram's lines from the provider's
-- top rows on each refresh. Both columns are nullable with no DEFAULT so the ALTER stays portable across SQLite,
-- MySQL/MariaDB and PostgreSQL; a NULL provider means the hologram is not a leaderboard, so existing rows are
-- untouched with no data migration.
--
--   leaderboard_provider   the data-source id ('balance', …) a leaderboard hologram shows, or NULL = not a leaderboard
--   leaderboard_limit      how many top rows the leaderboard shows (NULL only when provider is NULL)

ALTER TABLE holograms ADD COLUMN leaderboard_provider VARCHAR(64);
ALTER TABLE holograms ADD COLUMN leaderboard_limit INT;
