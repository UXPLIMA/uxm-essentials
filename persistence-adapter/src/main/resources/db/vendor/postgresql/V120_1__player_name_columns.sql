-- A player's name can be longer than the sixteen characters a Java account allows: Floodgate prefixes a Bedrock
-- gamertag and may keep its spaces as underscores, and an action taken through the developer API is attributed to
-- <plugin>/<caller>. PostgreSQL refuses a longer value outright, so the seen row of such a player failed on every
-- login and each sanction on them failed with it. Every column that holds a player's or an issuer's name becomes
-- VARCHAR(64) here.
--
-- SQLite stores a VARCHAR of any length whatever it declares, so only PostgreSQL (this folder) and MySQL and MariaDB
-- (db/vendor/mariadb) run a script, each in its own syntax, under the same version. The jOOQ classes read this one
-- beside the shared migrations, so the length a write is fitted to is the widened one everywhere.
--
-- Changing the type keeps the column's nullability. Widening never cuts a value and needs no rewrite of the table.
ALTER TABLE ${prefix}economy_owners ALTER COLUMN name TYPE VARCHAR(64);
ALTER TABLE ${prefix}mail ALTER COLUMN sender_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}moderation_mutes ALTER COLUMN muted_by_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}moderation_jails ALTER COLUMN jailed_by_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}moderation_tempbans ALTER COLUMN banned_by_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}moderation_warns ALTER COLUMN warned_by_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}moderation_ip_bans ALTER COLUMN banned_by_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}moderation_seen ALTER COLUMN name TYPE VARCHAR(64);
ALTER TABLE ${prefix}moderation_sanction_history ALTER COLUMN actor_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}player_names ALTER COLUMN name TYPE VARCHAR(64);
ALTER TABLE ${prefix}player_names ALTER COLUMN lower_name TYPE VARCHAR(64);
ALTER TABLE ${prefix}guard_denials ALTER COLUMN player TYPE VARCHAR(64);
ALTER TABLE ${prefix}board_name ALTER COLUMN name TYPE VARCHAR(64);
ALTER TABLE ${prefix}player_warps ALTER COLUMN owner_name TYPE VARCHAR(64);
