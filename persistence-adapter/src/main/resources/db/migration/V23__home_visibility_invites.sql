-- Home visibility (public/private; default private) + per-home invite list.
-- A public home is reachable by any player; a private home is reachable only
-- by its owner and any player on its invite list. Existing homes are all private
-- after this migration (public = 0), which is the safe conservative default.

ALTER TABLE homes ADD COLUMN public INTEGER NOT NULL DEFAULT 0;

CREATE TABLE home_invites (
    owner    VARCHAR(36) NOT NULL,
    slot     INTEGER     NOT NULL,
    invited  VARCHAR(36) NOT NULL,
    CONSTRAINT pk_home_invites PRIMARY KEY (owner, slot, invited)
);
CREATE INDEX idx_home_invites_owner ON home_invites (owner);
