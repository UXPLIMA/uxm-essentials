# Overview

uxmEssentials is split into the modules `api`, `bukkit-api`, `core`, `persistence-adapter`, `migration`, `bukkit-adapter`, `velocity-adapter`, `discord-adapter`, `redis-adapter`, `rest-adapter`. The Paper plugin in `bukkit-adapter` follows the hexagonal layout of the UXPLIMA plugin template under `com.uxplima.uxmessentials`:

- `bootstrap`
- `bossbar`
- `commandcontrol`
- `communication`
- `customcommands`
- `custommenus`
- `economy`
- `holograms`
- `homes`
- `invrollback`
- `itemworld`
- `kits`
- `messaging`
- `migration`
- `moderation`
- `nametags`
- `npc`
- `playerstate`
- `playerwarps`
- `poses`
- `presence`
- `ranks`
- `regions`
- `scoreboard`
- `security`
- `servertweaks`
- `shared`
- `skin`
- `staff`
- `survival`
- `tablist`
- `teleport`
- `trade`
- `vanish`
- `vaults`
- `villagers`
- `vote`
- `warps`
- `worlds`

- `domain` holds values and rules and imports nothing from Paper.
- `application` holds the use cases and the ports they need.
- `adapter` turns commands, events and menus into use case calls and implements the ports with Paper,
  uxmLib and storage.
- `bootstrap` is the composition root: every object is built there and receives its collaborators through
  its constructor.

Configuration is read into immutable snapshots and swapped whole on reload. Work that touches files or a
database runs on the uxmLib `Scheduler` async lane; work on a player or a block runs on its region thread.
