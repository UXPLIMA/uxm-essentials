-- The second-factor state every backend of one database shares, so a network applies one limit and one session.
--
-- `security_attempts` holds one row per account with wrong proofs on record. `failures` counts the wrong PINs and
-- codes since the last success or lockout, on any backend and any surface (the join keypad, the op re-auth keypad,
-- /2fa disable, /pin change, /pin remove), so moving to another backend does not start a fresh budget. When the count
-- reaches the configured limit the row is locked: `locked_until` holds when the lockout ends and `failures` goes back
-- to 0. `locked_until` is NULL while no lockout is set. A failure is added by one UPDATE inside a transaction, so two
-- backends counting at once each see the other's failure.
--
-- `security_sessions` holds one row per account that proved its factor and may carry that proof to another backend
-- of the network without proving it again. `held_by` is the backend the player is on, `connection_id` the proxy's id
-- for the connection the proof was made on (NULL without the proxy plugin), and `ip_hash` the one-way hash of the
-- address it was made from (the raw address is never stored). `expires_at` is NULL while the player is on
-- `held_by`; the backend they leave sets it to the moment the session may no longer be taken over, and a backend
-- that starts sets it on every row it still held, since nobody is on a backend that just started.
--
-- Timestamps are epoch milliseconds in a BIGINT, uuids the canonical 36-character text, server names as wide as the
-- other server columns. Same portability contract as the earlier migrations: plain CREATE TABLE in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}security_attempts (
    uuid         VARCHAR(36) NOT NULL,
    failures     INT         NOT NULL,
    locked_until BIGINT,
    CONSTRAINT ${prefix}pk_security_attempts PRIMARY KEY (uuid)
);

CREATE TABLE ${prefix}security_sessions (
    uuid          VARCHAR(36) NOT NULL,
    held_by       VARCHAR(64) NOT NULL,
    connection_id VARCHAR(36),
    ip_hash       VARCHAR(64),
    expires_at    BIGINT,
    CONSTRAINT ${prefix}pk_security_sessions PRIMARY KEY (uuid)
);
