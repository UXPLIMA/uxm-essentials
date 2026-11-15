-- Items a /sell or /sellall took from a seller but did not pay for, kept while the seller is offline and handed
-- back on their next join. A sale only ever takes plain stacks, so a row is a material id and a count rather than
-- serialized item bytes: nothing about the stack is lost and the row stays readable to an operator.
--
-- One row per give-back, keyed by a client-minted UUID like the other economy queues, so a write needs no
-- read-back and no dialect-specific identity column. `owner` is the canonical 36-character UUID text, `item` the
-- lower case material id, `created_at` epoch milliseconds. No foreign key to economy_owners: a seller on a foreign
-- economy provider has no row there.
--
-- Same portability contract as V1-V89: plain CREATE TABLE and CREATE INDEX in the subset SQLite, MySQL/MariaDB and
-- PostgreSQL all accept.
CREATE TABLE ${prefix}economy_item_returns (
    id          VARCHAR(36)  NOT NULL,
    owner       VARCHAR(36)  NOT NULL,
    item        VARCHAR(128) NOT NULL,
    amount      INT          NOT NULL,
    created_at  BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_economy_item_returns PRIMARY KEY (id)
);

-- Serves the take-all on join, which reads and deletes one owner's rows.
CREATE INDEX ${prefix}idx_economy_item_returns_owner ON ${prefix}economy_item_returns (owner);
