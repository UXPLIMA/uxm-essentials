-- Adds two more text-display properties to the V13 hologram store: a
-- glowing-outline colour and a text opacity. Both columns are nullable with no DEFAULT clause so the ALTER
-- stays portable across SQLite, MySQL/MariaDB and PostgreSQL (SQLite gates a few DEFAULT forms behind
-- pragmas); an absent value reads back as NULL and the mapper resolves it to the matching Appearance sentinel
-- (no glow / vanilla fully-opaque text), so existing rows keep their current look with no data migration.
--
-- Types mirror the V35 / V48 conventions: INT for the packed colour and the 0-255 opacity.
--
--   glow_argb      the glowing-outline colour as a packed ARGB int (NULL = no glow)
--   text_opacity   the text opacity 0-255 (NULL = the vanilla fully-opaque text)

ALTER TABLE holograms ADD COLUMN glow_argb INT;
ALTER TABLE holograms ADD COLUMN text_opacity INT;
