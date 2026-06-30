-- Arbitrary per-player key→value store for the menu engine's persistent player-data substrate. One row per
-- (player, key) holds a single string value; the Phase-2 data actions (SET/ADD/SUB/MUL/DIV) upsert a row and the
-- Phase-6 %..._value_<key>% placeholders read it. The data is server-authoritative and must outlive a restart or a
-- world rollback, which is why it lives in the database rather than in the per-holder PDC the transient PlayerMeta
-- accessor uses. Operators name their own keys, so the value is an open string column, not a typed one.
--
-- Same portability contract as V1-V67: the DDL stays in the subset SQLite (the default), MySQL/MariaDB and
-- PostgreSQL all accept, with no dialect-specific clause. The UUID is the canonical 36-character text used
-- everywhere else. jOOQ's DDLDatabase parses this file at build time, so the generated MenuPlayerData table matches
-- the runtime schema.
--
-- The columns are named data_key / data_value rather than key / value because key and value are SQL reserved words
-- that jOOQ's DDLDatabase (and several backends) would fold or reject when written unquoted; the data_ prefix keeps
-- the file dialect-neutral. data_key is a bounded VARCHAR so it can sit in the composite primary key on every
-- backend (MySQL cannot index an unbounded TEXT), while data_value is TEXT because an operator's value has no
-- natural length bound and a plain string round-trips byte-identically across all three backends (see V6, which
-- stores base64 in a TEXT column for the same reason).

CREATE TABLE menu_player_data (
    uuid        VARCHAR(36)   NOT NULL,
    data_key    VARCHAR(128)  NOT NULL,
    data_value  TEXT          NOT NULL,
    CONSTRAINT pk_menu_player_data PRIMARY KEY (uuid, data_key)
);

-- A player's whole row set is loaded in one scan on join (load-on-join cache warm), so the leading uuid column lets
-- the index serve that load directly.
CREATE INDEX idx_menu_player_data_uuid ON menu_player_data (uuid);
