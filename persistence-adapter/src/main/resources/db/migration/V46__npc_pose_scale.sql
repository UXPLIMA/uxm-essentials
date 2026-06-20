-- Adds the body pose and the size multiplier an NPC renders with (V38-V45 are the name row, look toggle,
-- equipment/glow, the action chain, the entity type and the widened equipment payload columns). An NPC can now
-- be frozen in a pose (STANDING by default, or SITTING/SLEEPING/…) and resized (1.0 is the natural size),
-- the pose + scale surface.
--
-- pose is the uppercase pose NAME, the same string the domain carries, so the render adapter resolves it to the
-- packet layer's pose without the domain ever touching Bukkit; NOT NULL with a STANDING default means every
-- existing row reads back upright exactly as before. scale is the size multiplier as a REAL (the same column
-- type yaw/pitch already use), NOT NULL DEFAULT 1.0 so an older row reads back at its natural size. Both columns
-- are purely additive and leave every stored NPC unchanged.
--
-- Same portability contract as V1-V45: plain ALTER TABLE ADD COLUMN statements with a constant DEFAULT in the
-- subset SQLite (the default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's
-- DDLDatabase parses this file alongside V1-V45 at build time, so the generated Npc record gains the pose and
-- scale columns with no extra configuration.

ALTER TABLE npc ADD COLUMN pose  VARCHAR(32) NOT NULL DEFAULT 'STANDING';
ALTER TABLE npc ADD COLUMN scale REAL        NOT NULL DEFAULT 1.0;
