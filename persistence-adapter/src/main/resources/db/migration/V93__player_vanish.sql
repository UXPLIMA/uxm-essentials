-- Each player's last written vanish. The live state stays in memory on the backend the player is on; this row is what
-- the next backend reads when the player logs in to it. A vanish ends when the player leaves the network, unless they
-- may persist it, so the row matters for two cases: a server switch through the proxy, and a persisting player coming
-- back after a restart.
--
-- One row per player, keyed by the canonical 36-character UUID text. `vanished` is a SMALLINT (0/1) rather than a
-- BOOLEAN so there is no dialect-specific boolean handling. `vanish_level` is the tier the player was hidden at, NULL
-- when they are not vanished; the next join re-derives it from permissions. `last_server` is the server-name of the
-- backend the vanished player was last on, NULL when they are not vanished. `updated_at` is when the vanish or reveal
-- was written. `seen_at` is when `last_server` last confirmed the vanished player was on it; that backend refreshes it
-- while they stay, so a stale value means it went down without writing a departure. `departed_at` is when the vanished
-- player left `last_server`, NULL while they are on it. Every time is epoch milliseconds in a BIGINT, and a vanish or
-- reveal older than the kept row is dropped, so two writes that land out of order settle on the newer one.
--
-- Same portability contract as V1-V92: plain CREATE TABLE in the subset SQLite, MySQL/MariaDB and PostgreSQL all
-- accept.
CREATE TABLE ${prefix}player_vanish (
    player_uuid  VARCHAR(36)  NOT NULL,
    vanished     SMALLINT     NOT NULL,
    vanish_level INT,
    last_server  VARCHAR(255),
    updated_at   BIGINT       NOT NULL,
    seen_at      BIGINT,
    departed_at  BIGINT,
    CONSTRAINT ${prefix}pk_player_vanish PRIMARY KEY (player_uuid)
);
