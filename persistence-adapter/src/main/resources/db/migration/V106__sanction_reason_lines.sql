-- A sanction reason the server writes from a catalog line (a warn ladder step: "reached 3 warnings, the last for:
-- spam") is kept as that line and its arguments, so every reader reads it in their own language and an operator's
-- later edit of the line reaches sanctions already given. `reason_key` is the catalog key, `reason_args` the
-- arguments as name=value pairs joined by '&', each name and value percent-encoded as UTF-8.
--
-- Both columns are nullable with no default. A reason a staff member typed, and every row written before this
-- release, has NULL in both and is shown as its `reason` text, which is unchanged. A ladder row still writes its
-- `reason` text too, drawn for the punished player, so the audit log and an older release read what they always did.
--
-- The IP ban table carries the columns because a warn ladder ban under STRICT address strictness bans the player's
-- addresses with the ban's reason.
--
-- Plain ALTER TABLE ADD COLUMN statements run unchanged on SQLite, MySQL/MariaDB and PostgreSQL.
ALTER TABLE ${prefix}moderation_mutes ADD COLUMN reason_key VARCHAR(64);
ALTER TABLE ${prefix}moderation_mutes ADD COLUMN reason_args TEXT;

ALTER TABLE ${prefix}moderation_tempbans ADD COLUMN reason_key VARCHAR(64);
ALTER TABLE ${prefix}moderation_tempbans ADD COLUMN reason_args TEXT;

ALTER TABLE ${prefix}moderation_ip_bans ADD COLUMN reason_key VARCHAR(64);
ALTER TABLE ${prefix}moderation_ip_bans ADD COLUMN reason_args TEXT;

ALTER TABLE ${prefix}moderation_sanction_history ADD COLUMN reason_key VARCHAR(64);
ALTER TABLE ${prefix}moderation_sanction_history ADD COLUMN reason_args TEXT;
