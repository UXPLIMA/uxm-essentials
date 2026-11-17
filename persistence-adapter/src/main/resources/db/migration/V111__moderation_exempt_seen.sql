-- What each backend last saw of a player's punishment exemption (the uxmessentials.bypass.punishment node), so a
-- backend can tell that an offline player is exempt through another backend's own operator list or an unshared
-- permission store.
--
-- One row per player and backend. A backend writes the node's live answer when the player joins it, when they leave
-- it, and when their permissions change while they are on it (when the permission plugin says so). `held` is 1 when
-- the node was held and 0 when it was not, a SMALLINT (0/1) rather than a BOOLEAN for the same portability reason as
-- V29. `seen_at` is when it was read, in epoch milliseconds; a write older than the row's is dropped, so the row is
-- always that backend's latest reading. An offline player counts as exempt when any backend's row holds the node.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE in the subset SQLite, MySQL/MariaDB and
-- PostgreSQL all accept.
CREATE TABLE ${prefix}moderation_exempt_seen (
    player_uuid VARCHAR(36) NOT NULL,
    server      VARCHAR(64) NOT NULL,
    held        SMALLINT    NOT NULL,
    seen_at     BIGINT      NOT NULL,
    CONSTRAINT ${prefix}pk_moderation_exempt_seen PRIMARY KEY (player_uuid, server)
);
