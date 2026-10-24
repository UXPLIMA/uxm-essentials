-- Accumulated vote counts per player across every tracked period. The periodic
-- buckets (daily/weekly/monthly) each carry a companion "key" column that
-- identifies which calendar window they cover; the application resets a bucket
-- when the key no longer matches the current window, so leaderboards show the
-- current period without a scheduled job. Alltime never resets.

CREATE TABLE ${prefix}vote_totals (
    player     VARCHAR(36) NOT NULL,
    alltime    BIGINT      NOT NULL DEFAULT 0,
    daily      BIGINT      NOT NULL DEFAULT 0,
    weekly     BIGINT      NOT NULL DEFAULT 0,
    monthly    BIGINT      NOT NULL DEFAULT 0,
    day_key    BIGINT      NOT NULL DEFAULT 0,
    week_key   BIGINT      NOT NULL DEFAULT 0,
    month_key  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ${prefix}pk_vote_totals PRIMARY KEY (player)
);
CREATE INDEX ${prefix}idx_vote_totals_alltime ON ${prefix}vote_totals (alltime);
CREATE INDEX ${prefix}idx_vote_totals_monthly ON ${prefix}vote_totals (monthly);
CREATE INDEX ${prefix}idx_vote_totals_weekly  ON ${prefix}vote_totals (weekly);
CREATE INDEX ${prefix}idx_vote_totals_daily   ON ${prefix}vote_totals (daily);
