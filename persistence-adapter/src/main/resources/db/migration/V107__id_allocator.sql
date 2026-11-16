-- Blocks of ids handed out by the database, for tables whose id is minted by the plugin rather than by the engine.
-- One row per sequence: `next_id` is the first id nobody has been given yet. A writer reserves n ids by adding n to
-- `next_id` and reading it back in one short transaction; the row lock (the file lock on SQLite) makes two writers,
-- on one server or on several sharing the database, take disjoint blocks. Ids of a block a writer never used are
-- simply skipped.
--
-- The economy ledger (`transactions`) is the first sequence. It used to count from MAX(id) read at start on each
-- server, which two servers sharing one database both read, so it starts after the highest id already written.
--
-- Same portability contract as the earlier migrations: plain DDL and an INSERT ... SELECT that SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}id_allocator (
    sequence_name  VARCHAR(64)  NOT NULL,
    next_id        BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_id_allocator PRIMARY KEY (sequence_name)
);

INSERT INTO ${prefix}id_allocator (sequence_name, next_id)
SELECT 'transactions', COALESCE(MAX(id), 0) + 1
FROM ${prefix}transactions;
