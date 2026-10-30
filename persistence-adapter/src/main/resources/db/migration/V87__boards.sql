-- The boards that uxmLib's shared leaderboard module keeps: one row per board, period, window and player. A window
-- is named by the moment it starts, in epoch milliseconds; all time is the window that starts at zero. The store
-- adds to a row in the database itself, so an event for a player who is away is kept.
--
-- The shape is the one every UXM plugin with boards has, so the module's SQL store reads it unchanged.
--
-- Same portability contract as V1-V86: plain DDL that SQLite, MySQL/MariaDB and PostgreSQL accept.
CREATE TABLE ${prefix}board (
    board_name   VARCHAR(16) NOT NULL,
    board_period VARCHAR(16) NOT NULL,
    window_start BIGINT      NOT NULL,
    player_id    VARCHAR(36) NOT NULL,
    score        BIGINT      NOT NULL,
    reached_at   BIGINT      NOT NULL,
    CONSTRAINT ${prefix}pk_board PRIMARY KEY (board_name, board_period, window_start, player_id)
);

-- A board sorts one window by score, and counts a window by score for the places.
CREATE INDEX ${prefix}idx_board_rank ON ${prefix}board (board_name, board_period, window_start, score);

-- A player's own scores are read by player when they log in.
CREATE INDEX ${prefix}idx_board_player ON ${prefix}board (player_id);

-- The name a player was last let in under, so a board names a player who is away.
CREATE TABLE ${prefix}board_name (
    player_id VARCHAR(36) NOT NULL,
    name      VARCHAR(32) NOT NULL,
    CONSTRAINT ${prefix}pk_board_name PRIMARY KEY (player_id)
);
