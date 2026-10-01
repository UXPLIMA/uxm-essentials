-- Account linking left this plugin on 2026-10-01: the owner sells it as a plugin of its own. Both tables V16
-- made go, with every binding and every outstanding code in them. The owner chose that over keeping two tables
-- nothing reads.
--
-- V16 itself stays. Flyway refuses to start on a database that applied a migration the jar no longer carries.
--
-- Same portability contract as V1-V84: a plain DROP TABLE, which SQLite, MySQL/MariaDB and PostgreSQL all accept,
-- and which takes each table's own indexes with it.
DROP TABLE discord_link_pending;
DROP TABLE discord_links;
