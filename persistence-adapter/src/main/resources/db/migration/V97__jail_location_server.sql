-- A jail location records the backend it was set on, so a player held on another backend of the network is sent
-- there. The column is nullable with no default. A row that already exists is marked 'unscoped': its backend is
-- unknown, and this file cannot know which backend holds its world. Each backend adopts the unscoped rows whose
-- world it has loaded when it starts (JailServerClaimer), so the first backend to start never takes every jail of
-- the network. Nothing ever saves under 'unscoped', the sentinel V94 uses for warps. A NULL is what a backend of the
-- previous release writes for a jail it creates during a rolling upgrade; it is adopted the same way.
--
-- Plain ALTER and UPDATE statements run unchanged on SQLite, MySQL/MariaDB and PostgreSQL.

ALTER TABLE ${prefix}moderation_jail_locations ADD COLUMN server_id VARCHAR(64);
UPDATE ${prefix}moderation_jail_locations SET server_id = 'unscoped';
