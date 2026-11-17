-- Votes a vote site sent for a name no account answers to yet, kept until a player with that name first joins a
-- server that shares this database, and then credited with the time they came. Nothing here is keyed by a uuid made
-- up from the name, which on a Bedrock bridge or an online-mode server would be someone else.
--
-- One row per parked vote, keyed by a client-minted UUID. `name` is the name as the site spelled it, `name_lower`
-- the same lower-cased, which the join reads by. `exact_case` is 1 when only that exact spelling may claim the vote
-- (an offline-mode server already had other spellings of the name as separate accounts) and 0 otherwise, a SMALLINT
-- for the portability reason V111 gives. `voted_at` is the vote's epoch milliseconds. A join deletes each row before
-- it credits the vote and credits only the rows its own delete removed, so a vote is credited once.
--
-- Same portability contract as the earlier migrations: plain CREATE TABLE and CREATE INDEX in the subset SQLite,
-- MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}vote_parked (
    id          VARCHAR(36)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    name_lower  VARCHAR(255)  NOT NULL,
    service     VARCHAR(255)  NOT NULL,
    voted_at    BIGINT        NOT NULL,
    exact_case  SMALLINT      NOT NULL,
    CONSTRAINT ${prefix}pk_vote_parked PRIMARY KEY (id)
);

-- Serves the read at join, which reads the rows of one or two names, and the sweep of rows nobody claimed.
CREATE INDEX ${prefix}idx_vote_parked_name ON ${prefix}vote_parked (name_lower);
CREATE INDEX ${prefix}idx_vote_parked_voted_at ON ${prefix}vote_parked (voted_at);
