-- Each player's vanish, kept so it follows them to another backend and through a restart. The live state stays in
-- memory on the backend the player is on; this row is what a backend reads when the player logs in to it.
--
-- One row per player, keyed by the canonical 36-character UUID text. `vanished` is a SMALLINT (0/1) rather than a
-- BOOLEAN so there is no dialect-specific boolean handling. `vanish_level` is the tier the player was hidden at, NULL
-- when they are not vanished; the next join re-derives it from permissions. `updated_at` is epoch milliseconds, and a
-- write older than the kept row is dropped, so two writes that land out of order settle on the newer one.
--
-- Same portability contract as V1-V91: plain CREATE TABLE in the subset SQLite, MySQL/MariaDB and PostgreSQL all
-- accept.
CREATE TABLE ${prefix}player_vanish (
    player_uuid  VARCHAR(36) NOT NULL,
    vanished     SMALLINT    NOT NULL,
    vanish_level INT,
    updated_at   BIGINT      NOT NULL,
    CONSTRAINT ${prefix}pk_player_vanish PRIMARY KEY (player_uuid)
);
