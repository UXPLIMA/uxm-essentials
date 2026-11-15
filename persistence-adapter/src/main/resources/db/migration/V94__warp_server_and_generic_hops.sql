-- Two changes for warps used across the servers of a network.
--
-- 1. A warp records the backend it was set on. The column is nullable with no default. A row that already exists
-- is marked 'unscoped': its backend is unknown, and this file cannot know which backend holds its world. Each
-- backend adopts the unscoped rows whose world it has loaded when it starts (WarpServerClaimer), so the first
-- backend to start never takes every warp of the network. Nothing ever saves under 'unscoped', the same sentinel
-- V89 uses for staff loadouts. A NULL is what a backend of the previous release writes for a warp it creates
-- during a rolling upgrade; it is adopted the same way.
--
-- 2. The pending cross-server hop table carries any kind of hop, not only player warps. The rows and columns a
-- backend of the previous release reads and writes stay exactly as they are, so it keeps working during a rolling
-- upgrade: a player-warp hop is still written with kind NULL, warp_id and accrue_on_arrival. A hop of another kind
-- names its kind, its target in target_ref and its payee, if any, in settle_to, and writes warp_id 0. No player warp
-- has id 0, so a previous-release backend fails to read such a row and leaves it to the origin's watchdog.
--
-- Plain ALTER and UPDATE statements run unchanged on SQLite, MySQL/MariaDB and PostgreSQL.

ALTER TABLE ${prefix}warps ADD COLUMN server_id VARCHAR(64);
UPDATE ${prefix}warps SET server_id = 'unscoped';

ALTER TABLE ${prefix}player_warp_pending_teleports ADD COLUMN kind VARCHAR(16);
ALTER TABLE ${prefix}player_warp_pending_teleports ADD COLUMN target_ref VARCHAR(128);
ALTER TABLE ${prefix}player_warp_pending_teleports ADD COLUMN settle_to VARCHAR(128);
