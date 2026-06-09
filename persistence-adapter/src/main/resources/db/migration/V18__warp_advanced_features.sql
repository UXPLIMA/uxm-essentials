-- Migration script for warp advanced features: welcome messages, effects, overrides, and ratings.

-- Add new columns to warps table
ALTER TABLE warps ADD COLUMN welcome_message VARCHAR(512);
ALTER TABLE warps ADD COLUMN welcome_message_type VARCHAR(32) DEFAULT 'CHAT' NOT NULL;
ALTER TABLE warps ADD COLUMN departure_sound VARCHAR(128);
ALTER TABLE warps ADD COLUMN arrival_sound VARCHAR(128);
ALTER TABLE warps ADD COLUMN departure_particle VARCHAR(128);
ALTER TABLE warps ADD COLUMN arrival_particle VARCHAR(128);
ALTER TABLE warps ADD COLUMN warmup_seconds DOUBLE;
ALTER TABLE warps ADD COLUMN cooldown_seconds DOUBLE;
ALTER TABLE warps ADD COLUMN icon_material VARCHAR(64);

-- Add new columns to player_warps table
ALTER TABLE player_warps ADD COLUMN welcome_message VARCHAR(512);
ALTER TABLE player_warps ADD COLUMN welcome_message_type VARCHAR(32) DEFAULT 'CHAT' NOT NULL;
ALTER TABLE player_warps ADD COLUMN departure_sound VARCHAR(128);
ALTER TABLE player_warps ADD COLUMN arrival_sound VARCHAR(128);
ALTER TABLE player_warps ADD COLUMN departure_particle VARCHAR(128);
ALTER TABLE player_warps ADD COLUMN arrival_particle VARCHAR(128);
ALTER TABLE player_warps ADD COLUMN warmup_seconds DOUBLE;
ALTER TABLE player_warps ADD COLUMN cooldown_seconds DOUBLE;
ALTER TABLE player_warps ADD COLUMN icon_material VARCHAR(64);

-- Create table to track ratings for admin warps
CREATE TABLE warp_ratings (
    warp_name VARCHAR(64) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    rating DOUBLE NOT NULL,
    PRIMARY KEY (warp_name, player_uuid)
);

-- Create table to track ratings for player warps
CREATE TABLE player_warp_ratings (
    owner_uuid VARCHAR(36) NOT NULL,
    warp_name VARCHAR(64) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    rating DOUBLE NOT NULL,
    PRIMARY KEY (owner_uuid, warp_name, player_uuid)
);
