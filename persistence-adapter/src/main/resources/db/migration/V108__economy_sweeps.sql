-- The journal of /eco giveall and /eco resetall: one row per sweep over every account of one currency.
--
-- A sweep changes the accounts in owner order, a chunk at a time, each chunk in a short transaction of its own so no
-- payment waits long on its locks. The same transaction moves `after_owner` past the chunk and adds to the counts,
-- so the journal always says exactly which accounts are done: a sweep cut short by a stop or a lost connection
-- carries on from `after_owner` and never changes an account twice. A runner locks the row before each chunk, so two
-- servers resuming the same sweep take turns rather than repeat a chunk.
--
-- `kind` is CREDIT (giveall, adds `amount`) or RESET (resetall, sets `amount`); `scope` is PLAYERS (accounts of
-- players who have joined) or ALL (every account, also those other plugins opened). `state` is RUNNING until the
-- last chunk, then DONE with `finished_at`. Owners are the canonical 36-character UUID text, `after_owner` is ''
-- before the first chunk, timestamps are epoch milliseconds, money is DECIMAL(20,4) like the wallets.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}economy_sweeps (
    id           VARCHAR(36)    NOT NULL,
    kind         VARCHAR(16)    NOT NULL,
    scope        VARCHAR(16)    NOT NULL,
    currency     VARCHAR(32)    NOT NULL,
    amount       DECIMAL(20, 4) NOT NULL,
    reason       VARCHAR(32)    NOT NULL,
    actor        VARCHAR(36)    NOT NULL,
    actor_name   VARCHAR(16)    NOT NULL,
    started_at   BIGINT         NOT NULL,
    after_owner  VARCHAR(36)    NOT NULL,
    changed      INT            NOT NULL,
    skipped      INT            NOT NULL,
    gone         INT            NOT NULL,
    state        VARCHAR(16)    NOT NULL,
    finished_at  BIGINT,
    CONSTRAINT ${prefix}pk_economy_sweeps PRIMARY KEY (id)
);

-- Serves the start-up read of the sweeps still running.
CREATE INDEX ${prefix}idx_economy_sweeps_state ON ${prefix}economy_sweeps (state);
