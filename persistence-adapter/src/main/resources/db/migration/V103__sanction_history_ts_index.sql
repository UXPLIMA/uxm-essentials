-- Index the sanction history by time so the server-wide /modstats count, which reads every row since a threshold
-- (ts >= ?), is a range read instead of a scan of the whole table. V11 indexes (target, ts DESC) for the per-player
-- reads and V28 (actor) for /staffhistory and the per-staff /modstats view; neither starts with ts.
--
-- Plain CREATE INDEX in the subset SQLite, MySQL/MariaDB and PostgreSQL all accept.

CREATE INDEX ${prefix}idx_moderation_sanction_history_ts ON ${prefix}moderation_sanction_history (ts);
