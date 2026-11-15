-- Which row a server's copy of a vault came from, and how many item writes that row had seen. A server that just
-- wrote a vault's items keeps them in memory until it can tell that a copy it reads is from after its write; comparing
-- last-touched instants for that does not work across servers, whose clocks differ. The version only ever goes up, by
-- one per item write, inside the database, so a copy whose version is below the one a write produced is older than
-- that write, whatever the clocks say.
--
-- A deleted vault bought again is a new row, and its version counts from zero again, so versions are compared only
-- within one lineage: a random UUID given to a row when it is inserted. Rows that already exist start at version 0
-- with no lineage; they all share the empty lineage, which is safe because an owner's index holds one row at a time
-- and every row inserted from now on gets a lineage.
--
-- The writer is a random token naming one closing window's snapshot, written with every attempt to store it. A write
-- that failed is retried only while the row is still at the version it was written against, or already carries its
-- own token; and it counts as landed only when the row carries its token. So a retry never overwrites what another
-- server wrote in between, and a write applied without an answer is recognised as this snapshot's, not someone else's.
--
-- BIGINT NOT NULL with a constant default and nullable VARCHARs: plain ALTER statements that run unchanged on
-- SQLite, MySQL/MariaDB and PostgreSQL.
ALTER TABLE ${prefix}vaults ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ${prefix}vaults ADD COLUMN lineage VARCHAR(36);
ALTER TABLE ${prefix}vaults ADD COLUMN writer VARCHAR(36);
