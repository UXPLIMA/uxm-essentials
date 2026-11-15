-- A small AxPlayerWarps database: players and lookup tables referenced by id, the warps, and their side lists.

CREATE TABLE axplayerwarps_players (id INT PRIMARY KEY, uuid VARCHAR(36) NOT NULL, name VARCHAR(16) NOT NULL);
CREATE TABLE axplayerwarps_worlds (id INT PRIMARY KEY, world VARCHAR(128) NOT NULL);
CREATE TABLE axplayerwarps_categories (id INT PRIMARY KEY, category VARCHAR(128) NOT NULL);
CREATE TABLE axplayerwarps_materials (id INT PRIMARY KEY, material VARCHAR(128) NOT NULL);
CREATE TABLE axplayerwarps_currencies (id INT PRIMARY KEY, currency VARCHAR(128) NOT NULL);
CREATE TABLE axplayerwarps_warps (
    id INT PRIMARY KEY,
    owner_id INT NOT NULL,
    world_id INT,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    category_id INT,
    icon_id INT,
    created BIGINT NOT NULL,
    currency_id INT,
    price DOUBLE NOT NULL,
    earned_money DOUBLE NOT NULL,
    access INT NOT NULL
);
CREATE TABLE axplayerwarps_ratings (reviewer_id INT NOT NULL, warp_id INT NOT NULL, stars INT NOT NULL, date BIGINT NOT NULL);
CREATE TABLE axplayerwarps_whitelisted (player_id INT NOT NULL, warp_id INT NOT NULL, date BIGINT NOT NULL);
CREATE TABLE axplayerwarps_blacklisted (player_id INT NOT NULL, warp_id INT NOT NULL, date BIGINT NOT NULL);
CREATE TABLE axplayerwarps_favorites (player_id INT NOT NULL, warp_id INT NOT NULL, date BIGINT NOT NULL);
CREATE TABLE axplayerwarps_visits (visitor_id INT NOT NULL, warp_id INT NOT NULL, date BIGINT NOT NULL);

INSERT INTO axplayerwarps_players VALUES (1, '11111111-1111-4111-8111-111111111111', 'Alice');
INSERT INTO axplayerwarps_players VALUES (2, '22222222-2222-4222-8222-222222222222', 'Bob');
INSERT INTO axplayerwarps_players VALUES (3, '33333333-3333-4333-8333-333333333333', 'Carol');
INSERT INTO axplayerwarps_players VALUES (4, 'not-a-uuid', 'Mallory');

INSERT INTO axplayerwarps_worlds VALUES (1, 'world');
INSERT INTO axplayerwarps_worlds VALUES (2, 'lost_world');
INSERT INTO axplayerwarps_categories VALUES (1, 'shops');
INSERT INTO axplayerwarps_materials VALUES (1, 'CHEST');
INSERT INTO axplayerwarps_currencies VALUES (1, 'Vault');

-- A public shop with every side list filled in.
INSERT INTO axplayerwarps_warps VALUES
    (1, 1, 1, 10.5, 64.0, -3.5, 90.0, 5.0, 'Market', 'Cheap tools', 1, 1, 1700000000000, 1, 12.5, 40.0, 0);
-- A whitelist warp and a private warp.
INSERT INTO axplayerwarps_warps VALUES
    (2, 2, 1, 0.0, 70.0, 0.0, 0.0, 0.0, 'Secret', NULL, NULL, NULL, 1700000100000, NULL, 0.0, 0.0, 1);
INSERT INTO axplayerwarps_warps VALUES
    (5, 3, 1, 5.0, 70.0, 5.0, 0.0, 0.0, 'Hideout', NULL, NULL, NULL, 1700000200000, NULL, 0.0, 0.0, 2);
-- A warp in a world the server no longer has, and a warp whose owner row is gone.
INSERT INTO axplayerwarps_warps VALUES
    (3, 1, 2, 1.0, 2.0, 3.0, 0.0, 0.0, 'Gone', NULL, NULL, NULL, 1700000000000, NULL, 0.0, 0.0, 0);
INSERT INTO axplayerwarps_warps VALUES
    (4, 99, 1, 1.0, 2.0, 3.0, 0.0, 0.0, 'Orphan', NULL, NULL, NULL, 1700000000000, NULL, 0.0, 0.0, 0);

INSERT INTO axplayerwarps_ratings VALUES (2, 1, 4, 1700000300000);
INSERT INTO axplayerwarps_ratings VALUES (3, 1, 9, 1700000400000);
INSERT INTO axplayerwarps_ratings VALUES (4, 1, 3, 1700000400000);
INSERT INTO axplayerwarps_ratings VALUES (77, 1, 3, 1700000400000);
INSERT INTO axplayerwarps_whitelisted VALUES (3, 2, 1700000500000);
INSERT INTO axplayerwarps_blacklisted VALUES (2, 1, 1700000500000);
INSERT INTO axplayerwarps_favorites VALUES (3, 1, 1700000600000);
INSERT INTO axplayerwarps_visits VALUES (2, 1, 1700000700000);
INSERT INTO axplayerwarps_visits VALUES (2, 1, 1700000800000);
INSERT INTO axplayerwarps_visits VALUES (3, 1, 1700000900000);
