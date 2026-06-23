-- Warp categories: a server-wide set of named groups the warp browse menu drills into, and a nullable
-- category reference on each warp pointing at one of them by id (a null reference is an uncategorised warp,
-- rendered at the top level of the menu). Mirrors the kit category model.
CREATE TABLE warp_categories (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(256) NOT NULL,
    display_material VARCHAR(64),
    display_lore VARCHAR(2048),
    slot INT NOT NULL DEFAULT 0,
    parent_category_id VARCHAR(64)
);

ALTER TABLE warps ADD COLUMN category_id VARCHAR(64);
