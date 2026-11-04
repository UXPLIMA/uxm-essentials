-- The guard context's store: regions, their change history and the optional denial log.
--
-- A region is keyed (scope, world, id): the scope says which servers load it (server:<id> or set:<name>), so two
-- servers sharing one database keep their own regions apart while a shared set is one row every member reads.
-- The region itself (shape, priority, parent, family, template, tags, lock, rules, behaviours, roles) is one JSON
-- document in `data`: rules and behaviours grow with the action tree, and a document keeps this schema stable while
-- they do. `import_batch` is a column of its own so an import undo finds its rows without reading every document.
-- `version` rises with every write of the row and `updated_at` is epoch milliseconds; peers without Redis poll
-- MAX(updated_at) per scope to notice a change.
--
-- History rows hold the region before and after a change as the same JSON documents (either may be NULL for a
-- create or a removal), so any change can be shown and undone. Ids are assigned by the writer inside the same
-- transaction as the region write.
--
-- Same portability contract as V1-V87: plain CREATE TABLE and CREATE INDEX in the subset SQLite, MySQL/MariaDB and
-- PostgreSQL all accept.

CREATE TABLE ${prefix}guard_regions (
    scope        VARCHAR(80)  NOT NULL,
    world        VARCHAR(128) NOT NULL,
    id           VARCHAR(64)  NOT NULL,
    data         TEXT         NOT NULL,
    import_batch VARCHAR(64),
    version      BIGINT       NOT NULL,
    updated_at   BIGINT       NOT NULL,
    updated_by   VARCHAR(64)  NOT NULL,
    CONSTRAINT ${prefix}pk_guard_regions PRIMARY KEY (scope, world, id)
);

CREATE INDEX ${prefix}idx_guard_regions_batch ON ${prefix}guard_regions (import_batch);

CREATE TABLE ${prefix}guard_history (
    id         BIGINT       NOT NULL,
    happened_at BIGINT      NOT NULL,
    actor      VARCHAR(64)  NOT NULL,
    scope      VARCHAR(80)  NOT NULL,
    world      VARCHAR(128) NOT NULL,
    region     VARCHAR(64)  NOT NULL,
    change_kind VARCHAR(32) NOT NULL,
    before_data TEXT,
    after_data  TEXT,
    CONSTRAINT ${prefix}pk_guard_history PRIMARY KEY (id)
);

CREATE INDEX ${prefix}idx_guard_history_region ON ${prefix}guard_history (world, region, id);

CREATE TABLE ${prefix}guard_denials (
    happened_at BIGINT   NOT NULL,
    player  VARCHAR(16)  NOT NULL,
    world   VARCHAR(128) NOT NULL,
    x       INT          NOT NULL,
    y       INT          NOT NULL,
    z       INT          NOT NULL,
    region  VARCHAR(64)  NOT NULL,
    action_id VARCHAR(96) NOT NULL
);

CREATE INDEX ${prefix}idx_guard_denials_at ON ${prefix}guard_denials (happened_at);

CREATE INDEX ${prefix}idx_guard_denials_player ON ${prefix}guard_denials (player, happened_at);
