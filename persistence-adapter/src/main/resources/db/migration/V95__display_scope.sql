-- Let holograms, NPCs and main spawns belong to some servers of a network rather than all of them.
-- A scope is server:<id> or set:<name>, the form guard regions use. The columns are nullable with no
-- default: a row written before this release, and any row left unscoped, reads as every server, so an
-- existing hologram, NPC or main spawn keeps applying everywhere exactly as before.
--
-- The main spawn table held one row pinned at id 0. That row stays the network-wide main spawn with a
-- NULL scope; each scoped main spawn is a further row with its own id, at most one per scope, which the
-- unique index enforces. Every backend lets a unique index hold several NULLs, and only id 0 has one.
--
-- Plain ALTER TABLE ADD COLUMN and CREATE UNIQUE INDEX run unchanged on SQLite, MySQL/MariaDB and
-- PostgreSQL.
ALTER TABLE ${prefix}holograms ADD COLUMN scope VARCHAR(80);

ALTER TABLE ${prefix}npc ADD COLUMN scope VARCHAR(80);

ALTER TABLE ${prefix}teleport_main_spawn ADD COLUMN scope VARCHAR(80);

CREATE UNIQUE INDEX ${prefix}ux_teleport_main_spawn_scope ON ${prefix}teleport_main_spawn (scope);
