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
