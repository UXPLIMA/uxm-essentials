-- Each player's last written vanish. The live state stays in memory on the backend the player is on; this row is what
-- the next backend reads when the player logs in to it. A vanish ends when the player leaves the network, unless they
-- may persist it, so the row matters for two cases: a server switch through the proxy, told apart from a return by how
-- recently the player left a backend, and a persisting player coming back after a restart.
--
-- One row per player, keyed by the canonical 36-character UUID text. `vanished` is a SMALLINT (0/1) rather than a
-- BOOLEAN so there is no dialect-specific boolean handling. `vanish_level` is the tier the player was hidden at, NULL
-- when they are not vanished; the next join re-derives it from permissions. `updated_at` is when the vanish or reveal
-- was written, epoch milliseconds, and a write older than the kept row is dropped, so two writes that land out of
-- order settle on the newer one. `departed_at` is when the vanished player last left a backend, epoch milliseconds,
-- NULL while they have not.
--
-- Same portability contract as V1-V91: plain CREATE TABLE in the subset SQLite, MySQL/MariaDB and PostgreSQL all
-- accept.
CREATE TABLE ${prefix}player_vanish (
    player_uuid  VARCHAR(36) NOT NULL,
    vanished     SMALLINT    NOT NULL,
    vanish_level INT,
    updated_at   BIGINT      NOT NULL,
    departed_at  BIGINT,
    CONSTRAINT ${prefix}pk_player_vanish PRIMARY KEY (player_uuid)
);
