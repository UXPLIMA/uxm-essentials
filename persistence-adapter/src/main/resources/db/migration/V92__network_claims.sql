-- One-time kit claims, cooldowns and the daily login reward, shared by every server on one database. A single
-- server keeps these in the player's PDC; a network with network.enabled on reads and writes them here, because each
-- backend has its own PDC and would grant them again.
--
-- player_kit_claims: one row per player and one-time kit. The primary key is the authoritative claim: a server takes
-- the claim with an insert that does nothing on conflict, so of two servers racing for it exactly one insert lands.
-- player_cooldowns: when each cooldown ends, last write wins. ready_at is epoch milliseconds; a row whose ready_at is
-- in the past is dead and is removed when its player is next read.
-- player_daily_rewards: when the player last took the daily reward, epoch milliseconds. A server takes the reward with
-- an update conditioned on the stored stamp (or an insert when there is none), so only one server pays it.
--
-- Same portability contract as V1-V91: plain CREATE TABLE in the subset SQLite, MySQL/MariaDB and PostgreSQL all
-- accept. `player` is the canonical 36-character UUID text; `scope` is the stamp name folded to [a-z0-9._-].
CREATE TABLE ${prefix}player_kit_claims (
    player      VARCHAR(36)  NOT NULL,
    kit         VARCHAR(64)  NOT NULL,
    claimed_at  BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_player_kit_claims PRIMARY KEY (player, kit)
);

CREATE TABLE ${prefix}player_cooldowns (
    player      VARCHAR(36)  NOT NULL,
    scope       VARCHAR(128) NOT NULL,
    ready_at    BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_player_cooldowns PRIMARY KEY (player, scope)
);

CREATE TABLE ${prefix}player_daily_rewards (
    player      VARCHAR(36)  NOT NULL,
    claimed_at  BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_player_daily_rewards PRIMARY KEY (player)
);
