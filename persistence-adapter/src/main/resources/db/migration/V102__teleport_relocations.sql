-- Where an offline player is moved when they next join one backend: /tpofflinehere stores the spot here instead of
-- touching the player's own data file, and the backend applies it once the player has joined and deletes the row.
--
-- One row per player and backend: a relocation set on one server applies only when the player joins that server,
-- and a later /tpofflinehere on the same server replaces the earlier one. The world is kept by its uid, the
-- position as doubles and the facing as reals, like the spawn tables. created_at is epoch milliseconds and actor the
-- UUID of the staff member who set it; together with the key they name one stored relocation, so applying it is a
-- conditional DELETE that only one of two quick joins can win.
--
-- Plain CREATE TABLE in the subset SQLite, MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}teleport_relocations (
    player_uuid  VARCHAR(36)      NOT NULL,
    server_id    VARCHAR(64)      NOT NULL,
    world        VARCHAR(36)      NOT NULL,
    x            DOUBLE PRECISION NOT NULL,
    y            DOUBLE PRECISION NOT NULL,
    z            DOUBLE PRECISION NOT NULL,
    yaw          REAL             NOT NULL,
    pitch        REAL             NOT NULL,
    created_at   BIGINT           NOT NULL,
    actor        VARCHAR(36)      NOT NULL,
    CONSTRAINT ${prefix}pk_teleport_relocations PRIMARY KEY (player_uuid, server_id)
);
