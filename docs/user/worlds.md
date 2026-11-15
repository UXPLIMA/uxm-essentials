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

A server on a network under its own `network.server-name` keeps its worlds in the shared database recorded under that
name. Two servers may each run a world called `world`, each with its own settings, and `auto-adopt-loaded` and
`auto-load-registered` touch only the worlds of the server they run on. A server off a network, or still under the
shipped name `server-1`, records no server and keeps its worlds exactly as before. `/worlds list` and the world picker
show this server's worlds, and every other server's while `cross-server.enabled` is on; a world of another server is
managed on that server.

With `cross-server.enabled` on in `modules/worlds/config.conf`, `/worlds spawn <world>` and `/worlds tp <world>` reach
a world another server runs by sending the player there, to that world's spawn. A world of this server wins over one
of the same name elsewhere, and `<world>@<server>` picks one when several servers run it:

```
/worlds spawn arena@minigames
/worlds tp world_nether@survival Alex
```

The entry fee is taken once the jail, combat and cooldown checks pass, and given back, in the currency it was taken
in, if the player does not land: the proxy never moves them, the world is gone, full or will not load, or its spawn is
unsafe. A player holding `uxmessentials.bypass.safety.world` lands on an unsafe spawn anyway.

A world that records no server is claimed by every named server that has it, at that server's first start under its
name: a world loaded there, or a world folder of that name. Each such server gets its own copy of the world and its
settings, recorded under the uid of its own world, so servers that all ran a copy of one world before servers were
recorded each keep the settings it had, even one that only starts months later. A `world_server_claim_conflict`
warning names the worlds whose shared settings belonged to a different world of the same name. The claim runs once
per server name, so a world you unregister or delete does not come back on the next start; a world that arrives on a
server after its first start is taken with `/worlds claim <world>`, which copies it there, and `/worlds load <world>`
then loads it. A world loaded on a server after its first start (by `bukkit.yml` or another plugin) is adopted with
the shared copy's settings while `auto-adopt-loaded` is on, not with defaults. One no server has copied is listed as
unclaimed. If the world folders cannot be read at the first start, nothing is claimed, a `world_server_claim_failed`
warning says why, and the next start tries again.

The shared world stays in the database, hidden from `/worlds list` once a server has copied it, for servers that get
the world later. Nothing removes it on its own: `/worlds setserver <server> unscoped` replaces it with that server's
worlds, and one no server will ever need can be deleted by hand (add your table prefix to the table names):

```sql
DELETE FROM world_setting WHERE server_id = 'unscoped' AND world_name = 'arena';
DELETE FROM world WHERE server_id = 'unscoped' AND name = 'arena';
```

A server on a network that never set its own `network.server-name` shares the unclaimed worlds with every other server
in that state. It warns about it on start (`world_server_claim_skipped`) and does not create an auto-load world it has
no folder for.

`/worlds setserver <from> <to>` moves every world recorded on one server to another, settings included, without
touching any files. Use it after renaming `network.server-name`, or when a world was claimed by the wrong server.
`unscoped` as the new server hands the worlds back to the claim, replacing the shared copy of the same name, and the old
server claims again at its next start, so its worlds come back to it and auto-load as before; `unscoped` cannot be the
old one. A move to a new name carries the record that the claim has run, so the renamed server does not claim again,
and a later server under the old name does. `server-1` cannot be the new one: a server still under that name records no server, so no server would
see those worlds.
