-- The journal of /eco giveall and /eco resetall: one row per sweep over every account of one currency.
--
-- A sweep works through the accounts in owner order, a chunk at a time, each chunk in a short transaction of its own
-- so no payment waits long on its locks. The same transaction moves `after_owner` past the chunk and adds to the
-- counts, so the journal always says exactly which accounts are done: a sweep cut short by a stop or a lost
-- connection carries on from `after_owner` and never changes an account twice. A runner locks the row before each
-- chunk, so two servers resuming the same sweep take turns rather than repeat a chunk.
--
-- `mode` says where the currency's money is kept. LEDGER: the chunk changes the wallet rows. ITEMS: the chunk queues
-- one row per player in `economy_item_deliveries`. PROVIDER: another plugin keeps the balances; the chunk hands its
-- players out in `pending_owners` (comma separated owner ids) with `pending_by` (the runner's token) and `pending_at`,
-- the runner pays them through that plugin and then adds what it did and clears the three. Such calls are not
-- transactional, so a chunk still handed out when its runner is gone (`pending_at` older than the stale window) is
-- counted in `uncertain` and logged, never paid again.
--
-- `kind` is CREDIT (giveall, adds `amount`) or RESET (resetall, sets `amount`); `scope` is PLAYERS (accounts of
-- players who have joined) or ALL (every account, also those other plugins opened). `state` is RUNNING, or PAUSED
-- while a failure that may pass (a lost connection) waits to be tried again, with `last_error` saying what failed.
-- It ends as DONE after the last chunk, CANCELLED by an operator, or FAILED when the database refuses a chunk's rows
-- for good or the currency is no longer configured, each with `finished_at`.
--
-- `active_currency` holds the currency while the sweep is unfinished and is NULL once it ends. Its unique constraint
-- lets one currency have one unfinished sweep across every server: a second opened while one runs or is paused would
-- pay everyone twice when the first resumes, or interleave a giveall with a resetall. Every backend lets a unique
-- column hold any number of NULLs. Owners are the canonical 36-character UUID text (a Bedrock player's included),
-- `after_owner` is '' before the first chunk, timestamps are epoch milliseconds, money is DECIMAL(20,4) like the
-- wallets. `actor_name` is wider than a Java name so a console or a proxy's longer name fits.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}economy_sweeps (
    id              VARCHAR(36)    NOT NULL,
    kind            VARCHAR(16)    NOT NULL,
    scope           VARCHAR(16)    NOT NULL,
    mode            VARCHAR(16)    NOT NULL,
    currency        VARCHAR(32)    NOT NULL,
    amount          DECIMAL(20, 4) NOT NULL,
    reason          VARCHAR(32)    NOT NULL,
    actor           VARCHAR(36)    NOT NULL,
    actor_name      VARCHAR(64)    NOT NULL,
    started_at      BIGINT         NOT NULL,
    after_owner     VARCHAR(36)    NOT NULL,
    changed         INT            NOT NULL,
    skipped         INT            NOT NULL,
    gone            INT            NOT NULL,
    uncertain       INT            NOT NULL,
    state           VARCHAR(16)    NOT NULL,
    active_currency VARCHAR(32),
    pending_owners  TEXT,
    pending_by      VARCHAR(36),
    pending_at      BIGINT,
    last_error      VARCHAR(255),
    finished_at     BIGINT,
    CONSTRAINT ${prefix}pk_economy_sweeps PRIMARY KEY (id),
    CONSTRAINT ${prefix}uq_economy_sweeps_active UNIQUE (active_currency)
);

-- Serves the start-up read of the sweeps still running or paused.
CREATE INDEX ${prefix}idx_economy_sweeps_state ON ${prefix}economy_sweeps (state);

-- The item deliveries a sweep over an item currency queued: one row per player and sweep (the unique key keeps a
-- chunk that runs again from queueing twice), handed over in the player's inventory when they are online on any
-- server of the database. `kind` is GIVE (add `amount`) or RESET (set the held amount to `amount`).
--
-- A delivery claims a row before it hands the items over and changes or deletes it only after: `claimed_by` is the
-- claiming delivery's token and `claimed_at` when it claimed, both NULL while the row waits. A claim older than the
-- stale window can be taken again, so a server that stopped between the claim and the hand-over does not strand the
-- row. What did not fit stays as a GIVE of the rest with `held_back` 1, handed over on the player's next join rather
-- than on every pass over the players online. No foreign key to economy_owners: a player who never held a ledger
-- balance has no row there.
CREATE TABLE ${prefix}economy_item_deliveries (
    id          VARCHAR(36)    NOT NULL,
    sweep_id    VARCHAR(36)    NOT NULL,
    owner       VARCHAR(36)    NOT NULL,
    currency    VARCHAR(32)    NOT NULL,
    kind        VARCHAR(16)    NOT NULL,
    amount      DECIMAL(20, 4) NOT NULL,
    created_at  BIGINT         NOT NULL,
    held_back   INT            NOT NULL,
    claimed_by  VARCHAR(36),
    claimed_at  BIGINT,
    CONSTRAINT ${prefix}pk_economy_item_deliveries PRIMARY KEY (id),
    CONSTRAINT ${prefix}uq_economy_item_deliveries_sweep UNIQUE (sweep_id, owner)
);

-- Serves the read on join and the pass over the players online, which read a set of owners' rows.
CREATE INDEX ${prefix}idx_economy_item_deliveries_owner ON ${prefix}economy_item_deliveries (owner);
