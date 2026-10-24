-- The queue of physical-economy credits destined for offline players: a banknote
-- redeemed (or a physical payout) for a player who is not online is parked here
-- and replayed on their next join. Carries the economy_ context prefix and
-- foreign-keys the target to economy_owners like the rest of the economy schema.
--
-- Same portability contract as V1/V2: plain CREATE TABLE, money in DECIMAL(20,4),
-- the currency column named `currency` (matching V2/V20), no engine-specific
-- syntax. jOOQ's DDLDatabase parses this file alongside V1-V20 at build time.
CREATE TABLE ${prefix}economy_pending_physical_transactions (
    id          VARCHAR(36)    NOT NULL,
    player_uuid VARCHAR(36)    NOT NULL,
    currency    VARCHAR(32)    NOT NULL,
    amount      DECIMAL(20, 4) NOT NULL,
    created_at  BIGINT         NOT NULL,
    CONSTRAINT ${prefix}pk_economy_pending_physical_transactions PRIMARY KEY (id),
    CONSTRAINT ${prefix}fk_economy_pending_physical_transactions_player FOREIGN KEY (player_uuid)
        REFERENCES ${prefix}economy_owners (owner)
);

CREATE INDEX ${prefix}idx_economy_pending_physical_transactions_player
    ON ${prefix}economy_pending_physical_transactions (player_uuid);
