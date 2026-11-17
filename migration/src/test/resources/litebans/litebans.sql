-- A small LiteBans database: the columns LiteBans keeps in its H2 file, a few rows of each kind of punishment,
-- and the rows a long-running server collects that must not be imported.

CREATE TABLE litebans_bans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048),
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    removed_by_uuid VARCHAR(36),
    removed_by_name VARCHAR(128),
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    server_scope VARCHAR(32),
    server_origin VARCHAR(32),
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
    removed_by_uuid VARCHAR(36),
    removed_by_name VARCHAR(128),
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    server_scope VARCHAR(32),
    server_origin VARCHAR(32),
    silent BOOLEAN NOT NULL,
    ipban BOOLEAN NOT NULL,
    ipban_wildcard BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL
);

CREATE TABLE litebans_warnings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048),
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    removed_by_uuid VARCHAR(36),
    removed_by_name VARCHAR(128),
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    server_scope VARCHAR(32),
    server_origin VARCHAR(32),
    silent BOOLEAN NOT NULL,
    ipban BOOLEAN NOT NULL,
    ipban_wildcard BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    warned BOOLEAN NOT NULL
);

-- A permanent ban by a moderator.
INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('11111111-1111-4111-8111-111111111111', '10.0.0.1', 'griefing', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Moderator',
        1700000000000, -1, FALSE, FALSE, FALSE, TRUE);
-- A temporary ban from the console.
INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('22222222-2222-4222-8222-222222222222', NULL, 'spam', 'CONSOLE', 'Console',
        1700000000000, 1800000000000, FALSE, FALSE, FALSE, TRUE);
-- A ban a moderator lifted by hand.
INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('33333333-3333-4333-8333-333333333333', NULL, 'appealed', 'CONSOLE', 'Console',
        1700000000000, -1, FALSE, FALSE, FALSE, FALSE);
-- A permanent IP ban that also names the account it was issued against.
INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('44444444-4444-4444-8444-444444444444', '10.0.0.5', 'alts', 'CONSOLE', NULL,
        1700000000000, 0, FALSE, TRUE, FALSE, TRUE);
-- A wildcard IP ban: a range, with no single address to key on.
INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('#offline#', '10.0.*.*', 'proxy range', 'CONSOLE', 'Console',
        1700000000000, -1, FALSE, TRUE, TRUE, TRUE);
-- A ban against a name LiteBans never resolved to an account.
INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('#offline#', NULL, 'unknown player', 'CONSOLE', 'Console',
        1700000000000, -1, FALSE, FALSE, FALSE, TRUE);
-- A ban whose issuer column holds neither CONSOLE nor an account id.
INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('55555555-5555-4555-8555-555555555555', NULL, '  ', 'not-a-uuid', 'WebPanel',
        1700000000000, 1800000000000, FALSE, FALSE, FALSE, TRUE);

-- A permanent mute and a timed mute that was lifted.
INSERT INTO litebans_mutes (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('22222222-2222-4222-8222-222222222222', NULL, 'toxicity', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Moderator',
        1700000100000, -1, FALSE, FALSE, FALSE, TRUE);
INSERT INTO litebans_mutes (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active)
VALUES ('11111111-1111-4111-8111-111111111111', NULL, 'caps', 'CONSOLE', 'Console',
        1700000100000, 1700000200000, FALSE, FALSE, FALSE, FALSE);

-- A warning that stands until cleared and one that lapses on its own.
INSERT INTO litebans_warnings (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active, warned)
VALUES ('11111111-1111-4111-8111-111111111111', NULL, 'language', 'CONSOLE', 'Console',
        1700000200000, 0, FALSE, FALSE, FALSE, TRUE, TRUE);
INSERT INTO litebans_warnings (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, ipban_wildcard, active, warned)
VALUES ('11111111-1111-4111-8111-111111111111', NULL, 'language again', 'CONSOLE', 'Console',
        1700000300000, 1700086700000, FALSE, FALSE, FALSE, TRUE, FALSE);

-- Every login LiteBans saw: the account and the name it used. A rename leaves the older name behind it.
CREATE TABLE litebans_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date TIMESTAMP NOT NULL,
    name VARCHAR(16),
    uuid VARCHAR(36),
    ip VARCHAR(45)
);

INSERT INTO litebans_history (date, name, uuid, ip)
    VALUES ('2023-01-01 00:00:00', 'OldGriefer', '11111111-1111-4111-8111-111111111111', '10.0.0.1');
INSERT INTO litebans_history (date, name, uuid, ip)
    VALUES ('2024-01-01 00:00:00', 'Griefer', '11111111-1111-4111-8111-111111111111', '10.0.0.1');
INSERT INTO litebans_history (date, name, uuid, ip)
    VALUES ('2024-01-01 00:00:00', '.Spammer', '22222222-2222-4222-8222-222222222222', '10.0.0.2');
INSERT INTO litebans_history (date, name, uuid, ip)
    VALUES ('2024-01-01 00:00:00', 'not a name', '66666666-6666-4666-8666-666666666666', '10.0.0.6');
INSERT INTO litebans_history (date, name, uuid, ip)
    VALUES ('2024-01-01 00:00:00', 'Ghost', '#offline#', '10.0.0.7');
