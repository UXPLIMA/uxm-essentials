-- MySQL and MariaDB cap a TEXT column at 64 KB, and a full vault, an inventory snapshot or a kept batch of filled
-- shulker boxes can be larger than that as base64 text: a TEXT column refuses such a write, or cuts it short with
-- strict mode off. Every column that holds serialized item stacks becomes LONGTEXT here, whose limit is far above anything a window or an
-- inventory can hold; what bounds a write now is the server's max_allowed_packet. SQLite and PostgreSQL store a TEXT of
-- any practical length, so this script runs on MySQL and MariaDB only, from their own location beside the shared
-- migrations (see FlywayMigrationRunner), and the shared schema the other backends and the jOOQ classes read is
-- unchanged.
--
-- MODIFY restates the whole column, so each keeps the nullability it was created with: NOT NULL where the shared
-- migration wrote it, nullable elsewhere. Widening never cuts a value, and running a statement again on a column that
-- is already LONGTEXT changes nothing, so a run that fails half way is safe to repeat. One column per statement keeps
-- the script in the form the schema test parses; the server rebuilds the table for each, which only takes a while on
-- a large inv_snapshots table, altered once.
ALTER TABLE ${prefix}vaults MODIFY contents LONGTEXT;

ALTER TABLE ${prefix}vault_item_returns MODIFY contents LONGTEXT NOT NULL;

ALTER TABLE ${prefix}trade_item_returns MODIFY contents LONGTEXT NOT NULL;

ALTER TABLE ${prefix}trade_escrow MODIFY item_data LONGTEXT;

ALTER TABLE ${prefix}inv_snapshots MODIFY contents LONGTEXT;

ALTER TABLE ${prefix}staff_loadout MODIFY inventory LONGTEXT NOT NULL;
ALTER TABLE ${prefix}staff_loadout MODIFY armor LONGTEXT NOT NULL;
ALTER TABLE ${prefix}staff_loadout MODIFY offhand LONGTEXT NOT NULL;

ALTER TABLE ${prefix}npc MODIFY equip_mainhand_b64 LONGTEXT;
ALTER TABLE ${prefix}npc MODIFY equip_offhand_b64 LONGTEXT;
ALTER TABLE ${prefix}npc MODIFY equip_head_b64 LONGTEXT;
ALTER TABLE ${prefix}npc MODIFY equip_chest_b64 LONGTEXT;
ALTER TABLE ${prefix}npc MODIFY equip_legs_b64 LONGTEXT;
ALTER TABLE ${prefix}npc MODIFY equip_feet_b64 LONGTEXT;
