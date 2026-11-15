-- Mark the staff loadouts held under the default server id as unscoped. Every backend saved
-- under 'server-1' whatever its network.server-name said, so such a row may belong to any
-- backend on the database. Each backend moves the unscoped rows onto its own id on start
-- (JooqStaffLoadoutRepository.adopt), and nothing is ever written under 'unscoped', so a row
-- a backend really named 'server-1' saves after this migration is never taken by another one.
-- A single UPDATE runs unchanged on SQLite, MySQL/MariaDB and PostgreSQL.

UPDATE ${prefix}staff_loadout SET server_id = 'unscoped' WHERE server_id = 'server-1';
