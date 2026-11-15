-- A managed world belongs to the backend it runs on. Two backends of a network may each run a world of the same name,
-- so a world row is keyed by (server_id, name) and its settings by (server_id, world_name, setting_key).
--
-- A primary key cannot be changed in place on SQLite, so both tables are rebuilt the way V70 rebuilt player_warps:
-- the old table is renamed aside, the new one is created under constraint names V61 never used (constraint names are
-- global per schema on PostgreSQL, SQLite and the H2 engine the jOOQ code generator parses against, and the renamed
-- table still holds V61's names until it is dropped), the rows are copied, and the old table is dropped.
--
-- Every existing row is copied under the server 'unscoped', the sentinel V89 and V94 use: this file cannot know which
-- backend holds a world. A backend that records no server (off a network, or under the shipped default name) keeps
-- reading and writing these rows as its own, so a standalone server behaves exactly as before. A backend on a network
-- under its own name copies, at its first start under that name, the unscoped row and settings of each world it
-- actually has (loaded, or a world folder of that name) onto its own name, and the unscoped row stays as a template
-- for every other backend that has the same world, since several backends may hold copies of one world folder and
-- some may not start for a while. A row no backend has copied is listed as unclaimed.
--
-- world_claim records each server name whose start-up claim has run, written in the same transaction as the copies.
-- The claim runs once per name, so a world a backend later unregisters is not copied back from its template at the
-- next start, and no backend's unregister or delete has to touch a template another backend may still need.
--
-- MySQL and MariaDB do not roll DDL back, so a run that fails half way leaves the tables part renamed, and the retry
-- fails on the first RENAME. To recover, look at which tables exist (with your table prefix):
--   * world_setting_v1_legacy does not exist: the copy finished and only the last DROP failed. Drop world_v1_legacy
--     and world_claim only, and start the server again. Do not drop world or world_setting: they hold the copied
--     rows. The start repairs the history, which removes the failed V98 entry, and this file then runs once more over
--     the copied tables, which keeps every row, since no backend has claimed one yet.
--   * world_setting_v1_legacy exists: drop world, world_setting and world_claim if this file created them, rename
--     world_v1_legacy and world_setting_v1_legacy back to world and world_setting, fix the cause and start the server
--     again.
--
-- Plain ALTER, CREATE, INSERT ... SELECT and DROP statements run unchanged on SQLite, MySQL/MariaDB and PostgreSQL.

ALTER TABLE ${prefix}world RENAME TO ${prefix}world_v1_legacy;
ALTER TABLE ${prefix}world_setting RENAME TO ${prefix}world_setting_v1_legacy;

CREATE TABLE ${prefix}world (
    server_id           VARCHAR(64)  NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    uid                 VARCHAR(36),
    environment         VARCHAR(16)  NOT NULL,
    world_type          VARCHAR(16)  NOT NULL,
    seed                BIGINT,
    generator_ref       VARCHAR(255),
    dimension           VARCHAR(128),
    generate_structures INT          NOT NULL,
    alias               VARCHAR(64),
    auto_load           INT          NOT NULL,
    adopted             INT          NOT NULL,
    created_at          BIGINT       NOT NULL,
    created_by          VARCHAR(36),
    CONSTRAINT ${prefix}pk_world_by_server PRIMARY KEY (server_id, name)
);

CREATE TABLE ${prefix}world_setting (
    server_id     VARCHAR(64)  NOT NULL,
    world_name    VARCHAR(64)  NOT NULL,
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(512) NOT NULL,
    CONSTRAINT ${prefix}pk_world_setting_by_server PRIMARY KEY (server_id, world_name, setting_key)
);

INSERT INTO ${prefix}world (server_id, name, uid, environment, world_type, seed, generator_ref, dimension,
                            generate_structures, alias, auto_load, adopted, created_at, created_by)
SELECT 'unscoped', name, uid, environment, world_type, seed, generator_ref, dimension,
       generate_structures, alias, auto_load, adopted, created_at, created_by
FROM ${prefix}world_v1_legacy;

INSERT INTO ${prefix}world_setting (server_id, world_name, setting_key, setting_value)
SELECT 'unscoped', world_name, setting_key, setting_value
FROM ${prefix}world_setting_v1_legacy;

CREATE TABLE ${prefix}world_claim (
    server_id   VARCHAR(64)  NOT NULL,
    claimed_at  BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_world_claim PRIMARY KEY (server_id)
);

DROP TABLE ${prefix}world_setting_v1_legacy;
DROP TABLE ${prefix}world_v1_legacy;
