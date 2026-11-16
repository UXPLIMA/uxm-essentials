# Teleport

The teleport module holds the commands that move players: teleport requests between players (`/tpa` and its
family), direct staff teleports (`/tp`), `/back`, `/spawn` and random teleport (`/rtp`). It also owns the warmup and
cooldown every voluntary teleport of the plugin waits through, homes and warps included.

## Files

Settings are in `plugins/uxmEssentials/modules/teleport/config.conf`, the random teleport tuning in
`modules/teleport/rtp.conf`, and the `/rtp` world picker and the `/tpsettings` panel in `modules/teleport/gui/`.
Command names and aliases are in `commands.conf`.

## Teleport requests

| Command | Alias | Permission | What it does |
|---|---|---|---|
| `/tpa <player>` | | `uxmessentials.tpa.use` | Asks to teleport to a player. |
| `/tpahere <player>` | | `uxmessentials.tpahere.use` | Asks a player to teleport to you. |
| `/tpaccept` | `/tpyes` | `uxmessentials.tpa.use` | Accepts the oldest request waiting for you. |
| `/tpdeny` | `/tpno` | `uxmessentials.tpa.use` | Denies it. |
| `/tpcancel` | `/tpacancel` | `uxmessentials.tpa.cancel` | Withdraws your own request. |
| `/tpalist` | `/tprequests` | `uxmessentials.tpa.use` | Lists the players waiting on your answer. |
| `/tptoggle` | `/toggletp` | `uxmessentials.tpa.toggle` | Refuses or accepts every incoming request. |
| `/tpon`, `/tpoff` | | `uxmessentials.tpa.toggle` | Accepts or refuses incoming requests. |
| `/tpauto` | | `uxmessentials.tpa.auto` | Accepts incoming requests at once. |
| `/tpblock <player>`, `/tpunblock <player>` | | `uxmessentials.tpa.block` | Refuses one player's requests, or stops refusing them. |
| `/tpaall` | | `uxmessentials.tpa.all` | Sends a `/tpahere` to every other player on this server. |
| `/tpsettings` | | `uxmessentials.tpsettings.use` | Opens a panel with the `/tptoggle` and `/tpauto` switches. |

A request is refused when the requester is jailed, combat tagged or asks themselves, when the target refuses
requests or blocked the requester. It waits `request-ttl-seconds` (60) for an answer, then expires for both. With
`single-request-displace = true`, a new request to a player replaces whatever was waiting for them; with `false`,
requests queue and `/tpaccept` takes the oldest.

On accept, the player who moves (the requester for `/tpa`, the target for `/tpahere`) stands through the warmup and
is then moved to where the other player stands at that moment. A mover who is jailed or combat tagged at that point is
told why and stays put; the request is used up. A request is not refused for a cooldown; the accepted
teleport starts one (see [Cooldowns](#cooldowns)). Requests cost nothing.

`/tptoggle` and `/tpauto` are kept in the player's data on that server and survive a relog. The `/tpblock` list is
kept in memory and empties on a restart.

## Staff teleports

| Command | Permission | What it does |
|---|---|---|
| `/tp <player>`, `/goto <player>` | `uxmessentials.tp.use` | Teleports you to a player. |
| `/tp <player> <destination>` | `uxmessentials.tp.use` | Teleports one player to another; works from the console. |
| `/tphere <player>`, `/bring <player>` | `uxmessentials.tp.use` | Pulls a player to you. |
| `/tp <x> <y> <z> [world]` | `uxmessentials.tp.use` and `uxmessentials.tp.position` | Teleports you to a position. |
| `/tp at <player> <world> <x> <y> <z> [yaw pitch]` | `uxmessentials.tp.use` and `uxmessentials.tp.position` | Teleports a player to a position; works from the console. |
| `/tppos <x> <y> <z> [world]` | `uxmessentials.tp.position` | Teleports you to a position. |
| `/tpo`, `/tpohere` | `uxmessentials.tp.others` | `/tp <player>` and `/tphere` that also reach a player who refuses teleports. |
| `/tpall` | `uxmessentials.tp.all` | Pulls every other player on this server to you. |
| `/tprandomplayer` | `uxmessentials.tp.use` | Teleports you to a random player you can see. Alias `/tprp`. |
| `/top`, `/bottom`, `/jump`, `/up`, `/down`, `/ascend`, `/descend`, `/thru` | `uxmessentials.tp.vertical` | Moves you up, down, to the block you look at, or through a wall. |
| `/tpoffline <player>` | `uxmessentials.tp.offline` | Teleports you to where an offline player left this server. |
| `/tpofflinehere <player>` | `uxmessentials.tp.offline` | Moves an offline player to where you stand when they next join this server. |

Staff teleports have no warmup, cooldown, cost, jail or combat check. `/tp`, `/tphere`, `/goto` and `/bring` stop
at a player who refuses teleports with `/tptoggle` and tell you to use `/tpo` or `/tpohere`, which go through; with
`/tp <player> <destination>`, both players are asked. The position forms move only the named player and are not
stopped. A position keeps your facing. When uxmParkour is installed it owns `/jump`.

`/tpoffline` reads the position this server saved in the player's data when they left it, so on a network it is
where they left this server. Data saved before the server recorded the world uid falls back to its dimension. An
ender pearl the player had in flight is saved with them and lands only after they join again, so the saved position
does not include where it would have taken them.

`/tpofflinehere` does not touch the player's data. It stores your spot in the database for this server, and once the
player has joined this server they are teleported there, a moment after the join. A later `/tpofflinehere` replaces
an earlier one, and a move left on one server waits for a join on that server. The teleport takes them off any mount
or vehicle, which stays where they logged out with everything on it. You must stand on solid ground: not flying,
gliding, swimming, in water or lava, or riding, since the spot is used exactly as it is. An ender pearl the player had
in flight comes back with them and can still carry them off when it lands.

The move waits, untouched, while the player is jailed, and while its world is not loaded; it is applied at a later
join. While a move waits for a player, their join's own move (first-join RTP or spawn, every-join spawn) runs a moment
after the join, and only if the waiting move is not applied. When the teleport misses, because the player left again
or another plugin cancelled it, the move is kept for the next join; after 3 misses it is dropped and the console says
so.

Both refuse a player online on this server whom you can see: use `/tp` or `/tphere`. A player hidden from you by
vanish is handled as if offline, so the answer does not give them away.

## /back

`/back` (alias `/return`) returns you to the place you left on your last teleport, or where you died, whichever came
last. `/deathback` (alias `/dback`) returns you to your last death point. Both need `uxmessentials.back.use`, and a
death point also needs `back.on-death = true` and `uxmessentials.back.ondeath`. `back.death-delay-seconds` makes a
player wait that long after dying. Teleports caused by `back.ignored-causes` (an ender pearl, a chorus fruit) do not
replace the return point. The point is kept in memory on the server the player is on and is lost on a restart.

`/back` goes through the warmup and cooldown like any other teleport.

## /spawn

| Command | Permission | What it does |
|---|---|---|
| `/spawn` | `uxmessentials.spawn.use` | Teleports you to this world's spawn. |
| `/spawn <name>` | `uxmessentials.spawn.named` | Teleports you to a named spawn. |
| `/setspawn [name]` | `uxmessentials.spawn.set` | Sets this world's spawn, or a named one, at your position; `/setspawn location <world> <x> <y> <z> [yaw pitch]` and `/setspawn named <name> <world> <x> <y> <z>` take a position. |
| `/setmainspawn [scope <scope>] [<world> <x> <y> <z>]` | `uxmessentials.spawn.set` | Sets the main spawn, used by worlds without their own. |
| `/removespawn` | `uxmessentials.spawn.set` | Clears this world's own spawn. |
| `/mirrorspawn <world>` | `uxmessentials.spawn.set` | Sends this world's `/spawn` to another world's spawn. |

A spawn is found in this order: the world's mirror, the world's own spawn, the main spawn, the vanilla spawn. The
main spawn has a scope: without one, `/setmainspawn` writes this server's; `scope network` writes the one every server
falls back to, and `server`, `server:<id>` or `set:<name>` write that scope's.

The `spawn` block sends new players to spawn (`first-join`), every player on login (`every-join`), and handles death
respawns (`respawn`) through `respawn-chain`, whose steps are `home`, `bed`, `anchor`, `spawn`, `warp:<name>` and
`rtp`. `respawn.chain.<world>` overrides the chain for deaths in one world, and an empty list there gives that world
vanilla respawns. `uxmessentials.bypass.join-spawn` leaves a player where they logged out. A jailed player gets no
join move: their jail decides where they go. A player with a [`/tpofflinehere`](#staff-teleports) move waiting gets it
instead of the join move.

## Random teleport

| Command | Permission | What it does |
|---|---|---|
| `/rtp` | `uxmessentials.rtp.use` | Opens the world picker, or with `command-opens-gui = false` teleports you in your world. Alias `/wild`. |
| `/rtp gui` | `uxmessentials.rtp.gui` | Opens the world picker. |
| `/rtp <world>` | `uxmessentials.rtp.use` | Teleports you to a random spot in that world. |
| `/rtp <player>` | `uxmessentials.rtp.others` | Sends an online player to a random spot in their own world. |
| `/rtp biome <biome>` | `uxmessentials.rtp.biome` | Lands you in a named biome of your world. |
| `/settpr <min> <max>` | `uxmessentials.teleport.settpr` | Changes the search ring of the running server, without writing it to `rtp.conf`. |

Spots are searched in a ring between `min-radius` and `max-radius` around the world border's centre, kept inside the
border, and checked before anyone is sent there: solid ground, no `avoid-blocks`, no `excluded-biomes`, and outside
land claims and guard or WorldGuard regions while `respect-claims` and `respect-worldguard` are on. Each world keeps
`queue-target-size` checked spots ready and refills below `queue-low-water`, so `/rtp` never waits on a search; with
`persist-pool` the spots survive a restart. The picker offers each loaded world that has a ring to search.
`fallback-world.<world> = "<other>"` sends `/rtp` from a world without one to another world.

`uxmessentials.rtp.radius.<blocks>` sets the outer edge of the ring for `/rtp biome`: the largest node held replaces
`max-radius`, and may reach beyond it. Plain `/rtp` draws from the shared ready spots and ignores it.

`cost` is charged in the default currency. A player who cannot pay is refused before anything is searched. The money
is taken when the warmup ends, and given back if the hop then fails; a warmup cancelled by moving costs nothing. With
`/rtp <player>`, the sent player pays and their cooldown counts, not the sender's. After landing, the player is
shielded for `grace-seconds` with resistance, slow falling and no fall damage. `rtp-on-respawn` and
`rtp-on-first-join` send players to a random spot with no warmup, cooldown or cost.

## Warmups

A voluntary teleport (`/home`, `/warp`, player warps, `/spawn`, `/back`, `/rtp`, an accepted request) makes the
player stand still for `default-warmup` seconds (3) first. `uxmessentials.tp.warmup.<seconds>` sets it per player,
the shortest node held wins and 0 removes it; `uxmessentials.bypass.warmup.tp` starts at once and cannot be cancelled.
A warp can set its own warmup in its editor.

| Key | Default | Meaning |
|---|---|---|
| `warmup.cancel-on-move` | `true` | Leaving the starting block cancels. |
| `warmup.move-threshold` | `0.0` | Blocks of drift allowed before a move cancels. |
| `warmup.cancel-on-damage` | `false` | Taking damage cancels. |
| `warmup.cancel-on-interact` | `false` | Clicking cancels. |
| `warmup.cancel-on-rotate` | `false` | Turning your head cancels. |

A player has one warmup at a time. A new teleport replaces the waiting one, which never lands, and the player is told
it was replaced. Logging off ends it too.

### Paid teleports that do not land

A paid home or warp, or a priced player warp, is charged before the warmup. If the teleport then does not happen (a
cancelled or replaced warmup, a hop that fails, the player leaving, a refusal after the charge), the money comes back
once, in the currency it was taken in, and the player is told. A player warp owner is paid only once the visitor
lands. `/rtp` charges after the warmup instead and refunds a failed hop. Details: [homes](homes.md),
[warps](warps.md).

## Cooldowns

After a teleport lands the player waits `default-cooldown` seconds (5) before the next one. The jail, combat and
cooldown checks run before any charge, so a refused teleport costs nothing.

| Node | Effect |
|---|---|
| `uxmessentials.tp.cooldown.<seconds>` | The wait after a teleport, in place of `default-cooldown`; the shortest node held wins, 0 removes it. |
| `uxmessentials.bypass.cooldown.tp` | No teleport cooldown. |
| `uxmessentials.rtp.cooldown.<seconds>` | The wait between random teleports, kept apart from the others, in place of the `/rtp` default. |
| `uxmessentials.bypass.cooldown.rtp` | No `/rtp` cooldown. |

The two bypass nodes are listed in the permission pages under the `uxmessentials.bypass.cooldown.<feature>` row.

By default every voluntary teleport shares one clock. `cooldowns.back`, `cooldowns.home`, `cooldowns.warp` and
`cooldowns.spawn` set to 0 or more give that command its own clock and wait; `-1` keeps the shared one. A warp with
its own cooldown in the editor uses the warp clock, one clock shared by every warp that sets one, with the warp's
value as the wait. `cooldowns.rtp` sets the default of the `/rtp` clock.

A `uxmessentials.tp.cooldown.<seconds>` node a player holds sets their wait on the shared clock, longer or shorter
than `default-cooldown`. On a command or warp with its own value the node can only lower that value, never raise
it: one node covers all of them, and a node given as a perk must not make a player wait on a command you set to 0.
The `/rtp` node sets the `/rtp` wait outright.

`cooldown-start-phase` decides what starts the clock. With `teleport`, the default, a landing does. With `accept`,
only an accepted request starts it, for the player who moves, and other teleports start none. With `request`, no
teleport starts one. Leave it at `teleport` unless you mean that.

## Safety

The teleport module does not check where `/tpa`, `/tp`, `/back` or `/spawn` land. `/rtp` only sends players to spots
its search checked. The `uxmessentials.bypass.safety.*` nodes belong to the destinations that do check:
`uxmessentials.bypass.safety.home`, `.warp`, `.pwarp` and `.world`. Jailed players cannot start a voluntary teleport,
and with `combat.block-teleport = true` neither can a player that CombatLogX or PvPManager has tagged.

## Teleports to another server

With `cross-server.enabled = true` in this module's config on every server, `/tpa`, `/tpahere`, `/tp`, `/tphere`
(and `/goto`, `/bring`, `/tpo`, `/tpohere`) reach a player on another server of the network. Type the player's plain
name: a selector only matches players on this server. A player of that name on this server always wins. It needs the
network bus and a proxy; see [network](network.md).

- **Requests.** The requester's server checks jail, combat and self; the target's server judges the request by the
  target's own `/tptoggle`, `/tpblock` and vanish, holds it, and lets the target list, accept, deny or let it expire
  with the usual commands. `/tpauto` accepts there too. The requester reads one line for how it was received and one
  for how it ended. A request no server answers within 5 seconds is given up as unreachable, which is also what a
  target server with the switch off causes.
- **The move.** Once accepted, the server the mover is on checks jail and combat, runs the warmup there, then sends
  them through the proxy. The target server places them where the other player stands when they arrive. For
  `/tpahere`, the target's server first asks the requester's server whether the request still stands.
- **Staff.** The server the moved player is on moves them, with no checks and no warmup; a player on another server
  is not stopped by their `/tptoggle`. A player vanished above the
  staff member's see level reads as offline, and nobody is moved.

Requests and staff teleports carry no money, so nothing is refunded. The arrival delay and the time a move may take
are the `cross-server` block of the root `config.conf`.

## Other configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module and its commands on or off. |
| `teleport-to-center` | `true` | Lands players in the middle of the block. |
| `arrival-title`, `arrival-effects` | `true` | Master switches for the arrival messages and effects. |
| `effects.<verb>` | | The sound and particle on arrival for `home`, `spawn`, `rtp`, `back` and `warp` (off: warps play their own). |
| `arrival-messages.<verb>` | a title; none for `warp` | Lines shown on arrival, each `TITLE`, `SUBTITLE`, `BOSS_BAR`, `CHAT` or `ACTION_BAR` with a message key or MiniMessage text. `[]` shows none. |

Other nodes: `uxmessentials.teleport.gui` (the settings panel on `/uxmess gui`, op) and
`uxmessentials.module.teleport` (reload and inspect the module, op). The full list is in
[permissions-gameplay](permissions-gameplay.md).
