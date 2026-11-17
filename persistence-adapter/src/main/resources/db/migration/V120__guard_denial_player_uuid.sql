-- A logged denial named its player only, so /guard log <name> merged the accounts that share a name in different case
-- on an offline-mode server, and lost a player who was renamed. The account's uuid is kept beside the name from now
-- on and the log is read by it; a row logged before this has none and is still found by its name.
ALTER TABLE ${prefix}guard_denials ADD COLUMN player_uuid VARCHAR(36);

CREATE INDEX ${prefix}idx_guard_denials_player_uuid ON ${prefix}guard_denials (player_uuid, happened_at);
