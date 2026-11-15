-- A LiteBans database whose warnings table is missing, as on an install that never issued a warning.

CREATE TABLE litebans_bans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048),
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    silent BOOLEAN NOT NULL,
    ipban BOOLEAN NOT NULL,
    ipban_wildcard BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL
);

CREATE TABLE litebans_mutes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048),
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    silent BOOLEAN NOT NULL,
    ipban BOOLEAN NOT NULL,
    ipban_wildcard BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL
);

INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('11111111-1111-4111-8111-111111111111', NULL, 'griefing', 'CONSOLE', 'Console',
        1700000000000, -1, FALSE, FALSE, FALSE, TRUE);
