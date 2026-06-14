-- Index the sanction history by actor so /staffhistory can answer "every action
-- this staff member issued, newest-first" off an index instead of a full-table
-- scan. V11 already indexes (target, ts DESC) for the per-target reads
-- (/banhistory, /mutehistory, /history); this is its mirror on the issuer side.
-- The leading `actor` scopes the index to one staff member's actions; the read
-- still orders by ts DESC, but the actor scope is the selective predicate here so
-- a plain (actor) index is enough — a console actor's null UUID is simply absent
-- from the index, which matches /staffhistory only ever resolving a real player.
--
-- Portability contract: a plain CREATE INDEX in the subset SQLite (default),
-- MySQL/MariaDB, and PostgreSQL all accept, with no dialect-specific clause —
-- the same contract as V1-V27. jOOQ's DDLDatabase parses this file alongside the
-- earlier migrations at build time.

CREATE INDEX idx_moderation_sanction_history_actor ON moderation_sanction_history (actor);
