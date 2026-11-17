-- The per-player messaging settings every backend of one database shares, so a player keeps them when they move to
-- another backend of the network. Each backend holds the settings of the players online on it in memory, read when
-- they log in and written back when they change; these rows are what the next backend reads.
--
-- `player_switches` holds one row per player and switch the player has set: whether they take private messages
-- (`/msgtoggle`), replies (`/rtoggle`) and the rotating announcements (`/broadcasttoggle`), and whether they spy on
-- every private message (`/socialspy`). `switch_name` is the stable name of the switch and `switch_on` its state as a
-- SMALLINT (0/1), so there is no dialect-specific boolean handling. A switch with no row is at its default.
--
-- `messaging_spy_targets` holds one row per staff member and player whose private messages they watch
-- (`/socialspy <player>`).
--
-- `messaging_reply_partners` holds each player's last private message partner, the one `/reply` answers. The name is
-- the partner's name when the message was sent, `touched_at` when it was sent, in epoch milliseconds, so the reply
-- window counts from there on every backend.
--
-- UUIDs are the canonical 36-character text. Same portability contract as the earlier migrations: plain CREATE TABLE
-- in the subset SQLite, MySQL/MariaDB and PostgreSQL all accept.
CREATE TABLE ${prefix}player_switches (
    player_uuid VARCHAR(36) NOT NULL,
    switch_name VARCHAR(32) NOT NULL,
    switch_on   SMALLINT    NOT NULL,
    CONSTRAINT ${prefix}pk_player_switches PRIMARY KEY (player_uuid, switch_name)
);

CREATE TABLE ${prefix}messaging_spy_targets (
    spy_uuid    VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    CONSTRAINT ${prefix}pk_messaging_spy_targets PRIMARY KEY (spy_uuid, target_uuid)
);

CREATE TABLE ${prefix}messaging_reply_partners (
    owner_uuid   VARCHAR(36) NOT NULL,
    partner_uuid VARCHAR(36) NOT NULL,
    partner_name VARCHAR(64) NOT NULL,
    touched_at   BIGINT      NOT NULL,
    CONSTRAINT ${prefix}pk_messaging_reply_partners PRIMARY KEY (owner_uuid)
);
