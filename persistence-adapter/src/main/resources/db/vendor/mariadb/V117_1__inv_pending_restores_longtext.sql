-- The kept inventory restores hold a full snapshot, as large as a row of inv_snapshots, which V105 widened for the
-- same reason: MySQL and MariaDB cap a TEXT column at 64 KB. This runs on MySQL and MariaDB only, from their own
-- location beside the shared migrations, right after V117 created the table.
ALTER TABLE ${prefix}inv_pending_restores MODIFY contents LONGTEXT;
