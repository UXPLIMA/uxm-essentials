-- A small Olzie PlayerWarps SQLite database: warps keyed by their rowid, and side tables pointing at that rowid.

CREATE TABLE playerwarps_warps (
    uuid TEXT,
    world TEXT,
    x REAL,
    y REAL,
    z REAL,
    yaw REAL,
    pitch REAL,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT,
    icon TEXT,
    visits INTEGER,
    date INTEGER,
    cost REAL,
    password TEXT,
    whitelist_enabled INTEGER,
    locked INTEGER
);
CREATE TABLE playerwarps_rates (warp_id INTEGER, uuid TEXT, rate INTEGER);
CREATE TABLE playerwarps_warps_whitelisted (warp_id INTEGER, player_uuid TEXT);
CREATE TABLE playerwarps_warps_managers (warp_id INTEGER, player_uuid TEXT);
CREATE TABLE playerwarps_warps_banned (warp_id INTEGER, player_uuid TEXT, time INTEGER, reason TEXT);
CREATE TABLE playerwarps_players_favourite_warps (warp_id INTEGER, player_uuid TEXT);
CREATE TABLE playerwarps_warps_visits (warp_id INTEGER, player_uuid TEXT);

-- rowid 1: a password warp with every side list filled in.
INSERT INTO playerwarps_warps VALUES ('11111111-1111-4111-8111-111111111111', 'world', 10.5, 64.0, -3.5, 90.0, 5.0,
    'Arena', ' Fight club ', 'pvp', 'DIAMOND_SWORD', 50, 1700000000000, 5.5, 'pw ', 0, 0);
-- rowid 2 and 3: a whitelist warp that is also locked, and a locked warp.
INSERT INTO playerwarps_warps VALUES ('22222222-2222-4222-8222-222222222222', 'world', 0.0, 70.0, 0.0, 0.0, 0.0,
    'Garden', NULL, NULL, NULL, 0, 1700000100000, 0.0, NULL, 1, 1);
INSERT INTO playerwarps_warps VALUES ('22222222-2222-4222-8222-222222222222', 'world', 5.0, 70.0, 5.0, 0.0, 0.0,
    'Den', '', '  ', NULL, 0, 0, 0.0, '', 0, 1);
-- rowid 4 and 5: a warp in a world the server no longer has, and a warp whose owner is not an account id.
INSERT INTO playerwarps_warps VALUES ('11111111-1111-4111-8111-111111111111', 'nether_old', 1.0, 2.0, 3.0, 0.0, 0.0,
    'Lost', NULL, NULL, NULL, 0, 0, 0.0, NULL, 0, 0);
INSERT INTO playerwarps_warps VALUES ('not-a-uuid', 'world', 1.0, 2.0, 3.0, 0.0, 0.0,
    'Ghost', NULL, NULL, NULL, 0, 0, 0.0, NULL, 0, 0);
-- rowid 6: the literal null password token and a visit counter that went below zero.
INSERT INTO playerwarps_warps VALUES ('11111111-1111-4111-8111-111111111111', 'world', 1.0, 2.0, 3.0, 0.0, 0.0,
    'Open', NULL, NULL, NULL, -3, 0, 0.0, 'NULL', 0, 0);

INSERT INTO playerwarps_rates VALUES (1, '22222222-2222-4222-8222-222222222222', 4);
INSERT INTO playerwarps_rates VALUES (1, '33333333-3333-4333-8333-333333333333', 7);
INSERT INTO playerwarps_rates VALUES (1, 'not-a-uuid', 3);
INSERT INTO playerwarps_warps_whitelisted VALUES (2, '33333333-3333-4333-8333-333333333333');
INSERT INTO playerwarps_warps_managers VALUES (1, '22222222-2222-4222-8222-222222222222');
INSERT INTO playerwarps_warps_banned VALUES (1, '33333333-3333-4333-8333-333333333333', 1700000500000, '  ');
INSERT INTO playerwarps_warps_banned VALUES (1, '44444444-4444-4444-8444-444444444444', 0, 'griefing');
INSERT INTO playerwarps_players_favourite_warps VALUES (1, '33333333-3333-4333-8333-333333333333');
INSERT INTO playerwarps_warps_visits VALUES (1, '22222222-2222-4222-8222-222222222222');
INSERT INTO playerwarps_warps_visits VALUES (1, '22222222-2222-4222-8222-222222222222');
INSERT INTO playerwarps_warps_visits VALUES (1, '33333333-3333-4333-8333-333333333333');
