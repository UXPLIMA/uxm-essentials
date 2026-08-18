-- Schema for the skin bounded context: the skin a player chose, so a join can dress them
-- without a single network call. The row is deliberately the whole answer at login time; the
-- source it was resolved from is kept beside the texture only so /skin update has something to
-- re-resolve and /skin info has something to report.
--
-- Same portability contract as V1-V83: the DDL stays in the subset SQLite (the default,
-- single-node servers), MySQL/MariaDB and PostgreSQL all accept. The key is the player's
-- canonical 36-character UUID text, not a database-minted identity column, exactly as every
-- other table in this schema does. jOOQ's DDLDatabase parses this file at build time, so the
-- generated PlayerSkins record always matches the runtime schema.
--
-- One row per player, because a player wears one skin: a change is an update, not a new row.
-- `source_type` is the SkinSource kind (BY_NAME / BY_URL / BY_FILE / BEDROCK / FALLBACK) and
-- `source_value` its single value (a username, a url, a file name, an xuid, a pool entry);
-- `model` is the SkinModel constant name, since a texture cut for the slim arm renders with a
-- seam on the classic one; `texture_value` is the base64 profile-property value and
-- `texture_sign` its Yggdrasil signature, nullable because an unsigned texture is legal;
-- `applied_at` is the instant the choice was made, as epoch milliseconds in a BIGINT so there
-- is no dialect-specific datetime handling.
CREATE TABLE player_skins (
    player_uuid   VARCHAR(36)  NOT NULL,
    source_type   VARCHAR(16)  NOT NULL,
    source_value  VARCHAR(512) NOT NULL,
    model         VARCHAR(8)   NOT NULL,
    texture_value TEXT         NOT NULL,
    texture_sign  TEXT,
    applied_at    BIGINT       NOT NULL,
    CONSTRAINT pk_player_skins PRIMARY KEY (player_uuid)
);
