-- Adds visitors, password, and is_locked columns to both warps and player_warps tables.
-- These are stored in a dialect-agnostic portable format:
-- visitors: BIGINT default 0
-- password: VARCHAR(128) (nullable)
-- is_locked: INT default 0

ALTER TABLE ${prefix}warps ADD COLUMN visitors BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ${prefix}warps ADD COLUMN password VARCHAR(128);
ALTER TABLE ${prefix}warps ADD COLUMN is_locked INT DEFAULT 0 NOT NULL;

ALTER TABLE ${prefix}player_warps ADD COLUMN visitors BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ${prefix}player_warps ADD COLUMN password VARCHAR(128);
ALTER TABLE ${prefix}player_warps ADD COLUMN is_locked INT DEFAULT 0 NOT NULL;
