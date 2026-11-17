-- On an offline-mode server each spelling of a name is its own account, and a mute or a jail follows its player into
-- a new account under another spelling as a copy. One row per copy ever made: `target` holds the copy, `origin` the
-- account whose sanction it copies, `kind` MUTE or JAIL, and `issued_at` (epoch millis) the issue instant the copy
-- shares with the original, which with `origin` and `kind` names the sanction it came from.
--
-- The row outlives the copy on purpose. Lifting the original lifts the copies it made, found through `origin`; a copy
-- staff lift on its own account stays lifted, because a sanction is copied onto an account only once; and a copy is
-- never itself copied, so it cannot come back to the account it came from.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX statements SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}moderation_case_copies (
    target     VARCHAR(36)  NOT NULL,
    kind       VARCHAR(8)   NOT NULL,
    origin     VARCHAR(36)  NOT NULL,
    issued_at  BIGINT       NOT NULL,
    CONSTRAINT ${prefix}pk_moderation_case_copies PRIMARY KEY (target, kind, origin, issued_at)
);

CREATE INDEX ${prefix}idx_moderation_case_copies_origin ON ${prefix}moderation_case_copies (origin, kind);
