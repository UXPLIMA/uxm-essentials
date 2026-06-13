-- Re-key homes from (owner, name) to (owner, slot); add icon + updated_at; name becomes the optional
-- label. Existing homes backfill to slots 0,1,2,... per owner in created_at order, keeping their old
-- name as the label. The per-player main-home table is retired.
--
-- The table-rebuild pattern is used here because SQLite does not support DROP COLUMN or RENAME COLUMN
-- in older versions, and the portable DDL subset must work on every supported backend. The new schema
-- mirrors the target described in the homes domain: (owner, slot) is the primary key, label is
-- nullable (it was the old name column), icon is nullable and new, and updated_at is new.
--
-- The backfill assigns each owner's homes a slot index 0, 1, 2, ... ordered by created_at ASC then
-- name ASC (matching the old list order), preserving the original name as the label so no player-facing
-- data is silently lost. updated_at is seeded from created_at so all pre-migration homes have a valid
-- timestamp without requiring a wall-clock call.

CREATE TABLE homes_new (
    owner       VARCHAR(36)      NOT NULL,
    slot        INTEGER          NOT NULL,
    label       VARCHAR(64),
    icon        VARCHAR(64),
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    created_at  BIGINT           NOT NULL,
    updated_at  BIGINT           NOT NULL,
    CONSTRAINT pk_homes_new PRIMARY KEY (owner, slot)
);

INSERT INTO homes_new (owner, slot, label, icon, world, world_name, x, y, z, yaw, pitch, created_at, updated_at)
SELECT h.owner,
       (SELECT COUNT(*) FROM homes AS h2
         WHERE h2.owner = h.owner
           AND (h2.created_at < h.created_at OR (h2.created_at = h.created_at AND h2.name < h.name))) AS slot,
       h.name,
       NULL,
       h.world,
       h.world_name,
       h.x,
       h.y,
       h.z,
       h.yaw,
       h.pitch,
       h.created_at,
       h.created_at
FROM homes AS h;

DROP TABLE homes;
ALTER TABLE homes_new RENAME TO homes;
CREATE INDEX idx_homes_owner ON homes (owner);

DROP TABLE home_main;
