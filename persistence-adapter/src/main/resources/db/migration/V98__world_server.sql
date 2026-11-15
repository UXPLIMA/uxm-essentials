-- A managed world belongs to the backend it runs on. Two backends of a network may each run a world of the same name,
-- so a world row is keyed by (server_id, name) and its settings by (server_id, world_name, setting_key).
--
-- A primary key cannot be changed in place on SQLite, so both tables are rebuilt the way V70 rebuilt player_warps:
-- the old table is renamed aside, the new one is created under constraint names V61 never used (constraint names are
-- global per schema on PostgreSQL, SQLite and the H2 engine the jOOQ code generator parses against, and the renamed
-- table still holds V61's names until it is dropped), the rows are copied, and the old table is dropped.
--
-- Every existing row is copied under the server 'unscoped', the sentinel V89 and V94 use: this file cannot know which
-- backend holds a world. Each backend claims, when it starts, the unscoped rows of the worlds it actually has (a world
-- loaded under the row's uid, or a world folder of that name with no loaded world of another uid), so the first
-- backend to start never takes the rows of another. A row no backend claims stays unscoped and is listed as unclaimed.
-- Nothing ever saves under 'unscoped'.
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

DROP TABLE ${prefix}world_setting_v1_legacy;
DROP TABLE ${prefix}world_v1_legacy;
