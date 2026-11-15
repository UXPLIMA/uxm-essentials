# Worlds and dimensions

The worlds module manages the worlds of a server: it creates, imports, loads, unloads and teleports to them, and
keeps their settings. `/worlds list` shows the worlds it manages, not every folder on disk.

## How Paper 26.2 keeps worlds

Every world is a dimension of the level named in `server.properties`. Its files are under
`<level>/dimensions/<namespace>/<path>` and the game addresses it as `namespace:path`. The server also gives each
world a name, and the module accepts either form:

| Dimension | Folder | World name | Also accepted |
|---|---|---|---|
| The overworld | `world/dimensions/minecraft/overworld` | `world` | `minecraft:overworld`, `overworld` |
| The nether | `world/dimensions/minecraft/the_nether` | `world_nether` | `minecraft:the_nether`, `the_nether` |
| The end | `world/dimensions/minecraft/the_end` | `world_the_end` | `minecraft:the_end`, `the_end` |
| A world made as `games` | `world/dimensions/minecraft/games` | `games` | `minecraft:games` |
| A datapack dimension `mypack:mining` | `world/dimensions/mypack/mining` | `mypack_mining` | `mypack:mining` |

The table assumes the level is called `world`. A dimension whose path has a `/` in it is not supported.

## Bringing a dimension under management

When the module enables, it takes every world the server has already loaded under management
(`auto-adopt-loaded` in `modules/worlds/config.conf`). A dimension that exists on disk but is not loaded is added
with `/worlds import`:

```
/worlds import world_nether
/worlds import minecraft:the_end
/worlds import mypack:mining
```

The environment is read from the dimension: `the_nether` is a nether, `the_end` is an end, and any other is
normal. Give it as a second argument to choose it yourself, for example `/worlds import mypack:mining nether`.
Tab completion lists the dimensions on disk that are not managed yet.

An imported world loads again on every start while `auto-load-registered` is on, from the same namespace it was
imported from.

## Other commands

Every command that takes a world takes either form: `/worlds tp the_nether`, `/worlds info minecraft:the_end` and
`/worlds tp mypack_mining` all work.

## Worlds on a network

Every server of a network keeps its own worlds in the shared database, recorded under its `network.server-name`. Two
servers may each run a world called `world`, each with its own settings, and `auto-adopt-loaded` and
`auto-load-registered` touch only the worlds of the server they run on. `/worlds list` and the world picker show every
server's worlds with the server that runs them; a world of another server is managed on that server.

With `cross-server.enabled` on in `modules/worlds/config.conf`, `/worlds spawn <world>` and `/worlds tp <world>` reach
a world another server runs by sending the player there, to that world's spawn. A world of this server wins over one
of the same name elsewhere, and `<world>@<server>` picks one when several servers run it:

```
/worlds spawn arena@minigames
/worlds tp world_nether@survival Alex
```

The entry fee is taken once the jail, combat and cooldown checks pass, and given back if the player does not land:
the proxy never moves them, the world is gone, full or will not load, or its spawn is unsafe. A player holding
`uxmessentials.world.bypass.safety` lands on an unsafe spawn anyway.

A world from before servers were recorded is claimed, on start, by the server that has it: a world loaded under the
recorded uid, or a world folder of that name when no world of another uid is loaded under it. One no server has is
listed as unclaimed. `/worlds claim <world>` on the server it belongs to takes it there; `/worlds load <world>` then
loads it.
