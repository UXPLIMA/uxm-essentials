-- Adds the timed-warning expiry to the moderation warning history so /tempwarn
-- can append a warning that lapses on its own, alongside the standing /warn that
-- holds until /unwarn clears it. The warn history stays append-only (the same V5
-- invariant): a row is never updated, and a lapsed warning is filtered out at the
-- /warns read by comparing `expires_at` against the wall clock rather than being
-- deleted, so the full history is still reconstructable.
--
-- Same portability contract as V1-V6: `ALTER TABLE ... ADD COLUMN` with a single
-- nullable column is in the subset SQLite (the default, single-node servers),
-- MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific DEFAULT or
-- column-position clause. The expiry is epoch milliseconds in a BIGINT, mirroring
-- every other instant in V5; a NULL `expires_at` is a standing /warn (no expiry),
-- a non-NULL one a /tempwarn. jOOQ's DDLDatabase parses this file alongside V1-V6
-- at build time, so the generated ModerationWarnsRecord gains the column.

ALTER TABLE moderation_warns ADD COLUMN expires_at BIGINT;

-- Serves the lapsed-warning filter at the /warns read (… AND (expires_at IS NULL
-- OR expires_at > ?)). A standing warn (NULL expires_at) sorts out of this index
-- and is never treated as lapsed.
CREATE INDEX idx_moderation_warns_expires ON moderation_warns (expires_at);
