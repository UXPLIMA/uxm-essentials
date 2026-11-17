-- A player's name can be longer than the sixteen characters a Java account allows: Floodgate prefixes a Bedrock
-- gamertag and may keep its spaces as underscores, and an action taken through the developer API is attributed to
-- <plugin>/<caller>. MySQL and MariaDB refuse a longer value outright in strict mode, so the seen row of such a
-- player failed on every login and each sanction on them failed with it. Every column that holds a player's or an
-- issuer's name becomes VARCHAR(64) here.
--
-- SQLite stores a VARCHAR of any length whatever it declares, so only MySQL and MariaDB (this folder) and PostgreSQL
-- (db/vendor/postgresql) run a script, each in its own syntax, under the same version. The jOOQ classes read the
-- PostgreSQL one beside the shared migrations, so the length a write is fitted to is the widened one everywhere.
--
-- MODIFY restates the whole column, so each keeps the nullability it was created with. Widening never cuts a value,
-- and running a statement again on a column already VARCHAR(64) changes nothing, so a run that fails half way is safe
-- to repeat.
ALTER TABLE ${prefix}economy_owners MODIFY name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}mail MODIFY sender_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}moderation_mutes MODIFY muted_by_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}moderation_jails MODIFY jailed_by_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}moderation_tempbans MODIFY banned_by_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}moderation_warns MODIFY warned_by_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}moderation_ip_bans MODIFY banned_by_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}moderation_seen MODIFY name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}moderation_sanction_history MODIFY actor_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}player_names MODIFY name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}player_names MODIFY lower_name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}guard_denials MODIFY player VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}board_name MODIFY name VARCHAR(64) NOT NULL;
ALTER TABLE ${prefix}player_warps MODIFY owner_name VARCHAR(64);
