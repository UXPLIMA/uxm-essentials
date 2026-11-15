-- Mark a pending cross-server teleport whose owner share is left for the arrival to bank. An origin
-- on this release only debits the visitor and sets the flag to 1; the target that moves the player
-- then pays the owner. A row an earlier origin wrote has the column NULL: that origin already paid
-- the owner at charge time, so the arrival must not pay again.
--
-- A SMALLINT (0/1) rather than a BOOLEAN so there is no dialect-specific boolean handling, and
-- nullable with no default so a row already in flight during the upgrade reads as unmarked.
-- One ALTER runs unchanged on SQLite, MySQL/MariaDB and PostgreSQL.
ALTER TABLE ${prefix}player_warp_pending_teleports ADD COLUMN accrue_on_arrival SMALLINT;
