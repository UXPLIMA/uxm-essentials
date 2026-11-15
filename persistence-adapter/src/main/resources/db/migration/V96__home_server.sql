-- A home records the backend it was set on, so a network can send a player to a home on another server.
--
-- The column is nullable with no default. A row that already exists is marked 'unscoped': its backend is unknown, and
-- this file cannot know which backend holds its world. Each backend claims the unscoped rows whose world it has loaded
-- when it starts (HomeServerClaimer), so the first backend to start never takes every home of the network. Nothing
-- ever saves under 'unscoped', the sentinel V89 and V94 use too. A NULL is what a backend of the previous release, or
-- an importer, writes for a home; it is claimed the same way.
--
-- Plain ALTER and UPDATE statements run unchanged on SQLite, MySQL/MariaDB and PostgreSQL.

ALTER TABLE ${prefix}homes ADD COLUMN server_id VARCHAR(64);
UPDATE ${prefix}homes SET server_id = 'unscoped';
