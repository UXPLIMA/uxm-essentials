-- One table for "which account connected from which address", replacing the two that grew up separately: the
-- security context's V78 `security_ip` (token only) and the moderation context's V34 `moderation_ip_history`
-- (raw address only). Two captures on the same joins meant two schemas, two alt lookups, and a privacy claim on
-- the security side that the moderation side quietly contradicted.
--
-- The association is the keyed `ip_token`: every alt read (the /alts and /ipalts lookups, the join-time cap on
-- accounts per address) answers from tokens alone and never sees an address. `ip` is the optional raw address,
-- written only while the moderation module is enabled, because only moderation consumes it: /seenip renders it
-- and a STRICT ban IP-bans every address the target is known to have used. A server without moderation stores no
-- address here at all.
--
-- Same portability contract as V1-V82: VARCHAR(36) uuids, VARCHAR(45) for the widest IPv6 literal, instants as
-- epoch-millis BIGINTs, no dialect-specific datetime handling. jOOQ's DDLDatabase parses this file alongside its
-- predecessors at build time, so the generated IP_HISTORY class always matches the runtime schema.
CREATE TABLE ip_history (
    uuid        VARCHAR(36) NOT NULL,
    ip_token    VARCHAR(64) NOT NULL,
    ip          VARCHAR(45),
    first_seen  BIGINT      NOT NULL,
    last_seen   BIGINT      NOT NULL,
    CONSTRAINT pk_ip_history PRIMARY KEY (uuid, ip_token)
);

-- The alt lookups and the per-address account cap all match by token, so index the fan-out side.
CREATE INDEX idx_ip_history_token ON ip_history (ip_token);

-- Carry the security tokens over as they are: the address behind them was never stored, so their `ip` stays
-- empty and `first_seen` can only be what the old row knew.
INSERT INTO ip_history (uuid, ip_token, ip, first_seen, last_seen)
SELECT uuid, ip_token, NULL, last_seen, last_seen FROM security_ip;

DROP TABLE security_ip;

-- moderation_ip_history is deliberately left in place and untouched here: its addresses can only be tokenised
-- with the server's key, which SQL has no access to. The plugin folds those rows in on the next enable and
-- deletes them as it goes, so the raw copy does not outlive the move.
