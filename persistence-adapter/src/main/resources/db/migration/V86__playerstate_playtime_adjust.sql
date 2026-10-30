-- One row per player for the playtime an operator wrote in by hand rather than the sampler counted: /playtime
-- set, add and take, and the vanilla statistic /playtime import brings in. The per-day ledger of V64 stays the
-- record of what the sampler saw; the summary adds this row's seconds to the all-time totals, so the today, week
-- and month windows are never bent by an operator's correction.
--
-- seconds is signed: a take or a set below the counted time stores a negative correction. imported marks a player
-- whose vanilla statistic was already brought in, so the join-time import runs once per player and a reset does
-- not bring the old time back.
--
-- Same portability contract as V1-V85: plain DDL that SQLite, MySQL/MariaDB and PostgreSQL accept, the UUID as
-- 36-character text, and the flag a SMALLINT (0/1) as in V33.
CREATE TABLE ${prefix}playerstate_playtime_adjust (
    uuid      VARCHAR(36) NOT NULL PRIMARY KEY,
    seconds   BIGINT      NOT NULL DEFAULT 0,
    imported  SMALLINT    NOT NULL DEFAULT 0
);
