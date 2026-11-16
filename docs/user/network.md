# Network

uxmEssentials runs on one server or on every backend of a network. On a network the backends keep their data in
one shared database and tell each other about changes over a bus, so a balance, a home or a ban is the same
wherever the player is. This page says what a network needs, how the bus is carried, and what each module shares
or does across servers.

## What a network needs

| What | Where | Why |
|---|---|---|
| A shared MySQL, MariaDB or PostgreSQL database | `storage` in `config.conf`, the same on every backend | Every shared row lives there. SQLite is a file of one server and cannot be shared. |
| The same `storage.table-prefix` everywhere | `config.conf` | Backends with different prefixes read different tables. |
| `network.enabled = true` | `config.conf` on every backend | Off, a backend runs alone and records no server on anything. |
| A unique `network.server-name` per backend, equal to the name the proxy gives it | `config.conf` | See [the server name](#the-server-name). |
| A carrier for the bus | the `uxmEssentials-velocity` jar on the proxy, or the `uxmEssentials-redis` jar on every backend | See [carriers](#carriers). |
| The same release on every backend and the proxy | `plugins/` | See [upgrading a network](#upgrading-a-network). |

A server of the network that has no business in the shared database (a creative or build server) keeps the same
`storage` block and sets `storage.local-only = true`: it stays on its own SQLite file. Moving an existing SQLite
server to the shared database is `storage.copy-from-file = true` with the new type, for one restart; the log says
how many rows were copied, and tables that already hold rows are left alone.

Every `network` key is read on start. A change needs a restart.

## The server name

```hocon
network {
  enabled = true
  server-name = "survival-1"
}
```

Every backend ships with `server-name = "server-1"`, so on a network it must be changed on each one. The name
matters in three ways:

- **The bus drops frames stamped with its own name**, so a write never echoes back. Two backends under one name
  drop each other's frames and sync between them stops with no other symptom. A backend that hears another one
  announce its name logs a warning, and `/uxmess doctor` fails its server name line. The shipped `server-1` on an
  enabled network is a doctor warning.
- **The player is sent by that name.** A cross-server warp, home, world, jail or teleport asks the proxy to connect
  the player to the server named on the target. The name must be the key under `[servers]` in `velocity.toml` (or
  the server's name in the BungeeCord config). A name the proxy does not know leaves the player where they are, and
  a paid move is refunded.
- **Rows record it.** Warps, player warps, homes, jail locations and managed worlds remember the server they were set
  on. A backend records its name only while `network.enabled` is on and the name is not `server-1`; a standalone
  server records nothing, so it never ties its rows to a name it will drop when it joins a network. Rows that record
  no server are claimed at a later start by the backend that has their world loaded.

`unscoped` is reserved for rows of no known server and should not be a server name: a backend under it starts, but
the log names it as a problem and it claims no warps, homes or jails (`reason=reserved_server_name`). A name may use
letters, digits, `_`, `.` and `-`, at most 64 characters; the enable log names one that cannot be used in a scope.

### Renaming a server, or fixing a wrong claim

Renaming `network.server-name` leaves every row on the old name. Move them with:

| Command | Moves |
|---|---|
| `/warp setserver <warp\|all> [server]` | One warp, or every warp, to a server. With no server it means this one. |
| `/home setserver <from> <to>` | Every home recorded on one server. |
| `/worlds setserver <from> <to>` | Every managed world of one server, settings included. See [worlds](worlds.md#worlds-on-a-network). |

Naming `unscoped` as the new server clears the record, so the backend that has the world loaded claims it again at
its next start. `server-1` cannot be the new server: a backend still under that name records no server, so no backend
would reach the rows. Two backends running copies of one world folder share its `uid.dat`, and the first to start
claims every warp and home in it: give each copy its own `uid.dat`, or set the server afterwards. A world loaded
after the server finished starting is claimed at the next restart. The log names each claim
(`event=warp_server_claim`, `event=home_server_claim`).

## Carriers

`network.transport` picks how frames move between backends. Frames only announce a change; the data itself is
always read from the shared database.

### Velocity (`transport = "velocity"`, the default)

Put `uxmEssentials-velocity` in the proxy's `plugins/`. It needs no configuration: it registers the bus channel,
relays each frame to every other backend the proxy knows, and sends each heartbeat back to the backend it came from,
so a backend can tell that the proxy plugin is there. A BungeeCord proxy does not relay the bus; use Redis there.

Plugin messages ride a player's connection, which sets the limits:

- A frame leaves a backend only while a player is online on it, and reaches only backends with a player online.
  The proxy keeps no queue, so a backend that sat empty misses what changed meanwhile. It reloads every shared cache
  when its first player joins, and warps, spawns, worlds, NPCs, holograms and the vote counter also reload every
  `network.cache-refresh-seconds` (default 300; `0` turns the timer off). Balances, homes and other per-player data
  expire on their own after a few minutes.
- Frames waiting for a player are kept in a queue of `network.bus.outbound-queue-size` (default 256). When it is
  full the oldest frame is dropped.
- A frame larger than 32767 bytes is never sent; the log reports the drop, at most once a minute.
- `network.bus-channel` must match the proxy. Leave it as shipped.

### Redis (`transport = "redis"`)

Put `uxmEssentials-redis` in `plugins/` of every backend and fill `network.redis` (`host`, `port`, `password`,
`channel`; `db` has no effect on pub/sub). Redis delivers to an empty backend too, so there is no gap and only the
refresh timer runs: prefer it on a busy network. Without the jar the bus runs local only and the log says so.

Redis moves frames, not players. Cross-server warps, homes, worlds, jails and teleports still need a BungeeCord or
Velocity proxy in front to connect the player.

### Both (`transport = "both"`)

Every frame goes out on both carriers, for a network where some backends are reached only through the proxy and
others only through Redis. A backend reached over both receives each frame twice. Frames that act carry an id or a
sequence and act once (for example jail, private message, staff chat, helpop, teleport requests, presence, world
changes, trade signals and vote parties), and a second cache reload is harmless.

This holds only once every backend runs the same version. During a rolling upgrade, a backend on an older version,
or a vote party sent by one, can still pay a vote party twice. A backend remembers the ids it acted on in memory
only, so a copy that arrives very late, after that backend restarted, can act again. Use `both` only while moving
from one carrier to the other.

## The proxy plugin

`uxmEssentials-velocity` does two things: it relays bus frames, and, with `command-control.enabled = true` in its
`config.conf`, it hides and gates the proxy's own commands (`/server`, `/glist`, `/send` and proxy plugins'
commands) by LuckPerms group. It stores no data and does not track players: each backend announces its own players.
Backend commands are gated by the `commandcontrol` module on each backend.

## Checking a network

`/uxmess doctor` on a backend shows:

- the bus line: disabled, or one verdict per carrier. Velocity reads connected only while frames come back through
  the proxy plugin, at least once in three heartbeats. Frames ride a player's connection, so with nobody online on
  the backend it reads `idle` and says so. Otherwise it reads `NOT ANSWERING`: install `uxmEssentials-velocity`, or
  update it to the same release, since an older proxy jar does not answer. Redis reads connected while its
  connection is up, `DISCONNECTED` otherwise. A carrier not working is a warning, since the backends reached only
  through it are cut off. Idle is a warning on Velocity alone, but not with `both` while Redis is connected;
- the peers line: how many other backends sent a heartbeat recently (`network.heartbeat-seconds`, default 30; a peer
  drops out after three missed beats) and how long ago. This is the line that shows frames arrive: on a working
  network it counts every other backend that has players (or every other backend, with Redis);
- the server name line: a failure when another backend uses this name, a warning while it is still `server-1`.

## What each module does on a network

"Shared" means the data is in the shared database and every backend reads the same rows. Settings files are never
shared: each backend reads its own `config.conf` and module files, so keep them alike where they should agree.

| Module | Shared | Acts across servers | Switch |
|---|---|---|---|
| Economy | Balances, transactions, daily reward stamp | none: `/pay` needs the target on this server | none |
| Homes | Homes, recorded per server | Teleport to a home on another server | `homes` `cross-server.enabled` |
| Warps | Warps, recorded per server | `/warp` to a warp on another server | `warps` `cross-server.enabled` |
| Player warps | Player warps, recorded per server | Visiting a player warp on another server | `playerwarps` `cross-server.enabled` |
| Worlds | Managed worlds and settings, per server | `/worlds tp` and `/worlds spawn` to another server | `worlds` `cross-server.enabled` |
| Teleport | Pending moves | `/tpa`, `/tpahere`, staff `/tp` and `/tphere` | `teleport` `cross-server.enabled` |
| Moderation | Bans, mutes, warnings, IP bans, jails, history | A ban or IP ban kicks everywhere; a jail sends the player to the jail's server | none |
| Messaging | Mail, ignore lists | `/msg`, `/reply`, `/helpop`; staff chat in the staff module | none |
| Vanish | Vanish state | Hidden on every server | `vanish` `cross-server` |
| Kits | One-time claims and cooldowns | none | none |
| Ranks | Each player's rank and prestige | none | none |
| Vaults | Vault contents | none | none |
| Holograms, NPCs, main spawn | Definitions, with a scope | Edits show on every server in scope | `network.shared-sets` |
| Guard | Regions, with a scope | Edits apply on every server in scope | none; shared sets in `modules/guard/config.conf` |
| Trade | Escrow | Two players on different servers trade | `trade` `cross-server` |
| Vote | Party counter | A party pays players on every server | `voteparty.network-rewards` in `vote`, default on |
| Playtime | Daily rows | Time on every server adds up | none |

### Cross-server moves

Homes, warps, player warps, worlds and teleport requests share one way of moving a player. The origin runs its
checks (jail, combat, cooldown), takes the cost, records the move in the shared database and asks the proxy to
connect the player. The server they join moves them `cross-server.arrival-delay-ticks` after the join (default 20).
Both keys are in the root `config.conf` and read on start.

- **Money.** If the player is not moved within `cross-server.request-ttl-seconds` (default 30), the origin gives the
  money back. If the target refuses them on arrival (the warp or home is gone, the world is full or will not load,
  the spot is unsafe), the server they reached gives it back, in the currency it was taken in; keep the economy module
  on wherever a move can land. A player warp's fee goes to its owner only when the visitor arrives.
- **Safety.** Warps, player warps, homes and worlds check the spot on the server the player lands on, the only one
  that has its world.
- **Warmup.** A warp or player warp on another server sends the player right after the checks and the charge, with
  no warmup. An accepted teleport request runs the usual warmup on the mover's server first.
- **Switches.** Turn each module's `cross-server` switch on on every backend. With it off, a warp or home is used on
  the server it is opened from, only this server's worlds can be reached, and a player warp of another server is
  refused. Staff `/tp` and `/tphere` across servers follow the teleport switch and run no checks and no warmup.

Details: [warps](warps.md), [homes](homes.md#homes-on-other-servers), [worlds](worlds.md#worlds-on-a-network),
[teleport](teleport.md).

### Worlds

Each backend keeps its own managed worlds in the shared database, under its server name, so two servers may each
run a world called `world` with its own settings. Worlds from before servers were recorded are kept under
`unscoped` as templates. At its first start under a real name, a backend copies the template of each world it has,
once; the `world_claim` table records that the claim ran, so a world removed later does not come back. A world that
arrives later is taken with `/worlds claim <world>`. The full rules are in [worlds](worlds.md#worlds-on-a-network).

### Jail

A jail made with `/setjail` records the server it was set on. A player jailed anywhere is sent to that server
without a charge and without the usual teleport checks, and a release frees them on the server they are on. When
the jail's server does not answer, the jail is applied here if its world is loaded here; otherwise the player stays
where they are, kept from teleporting, staff are told, and the move is tried again with a growing delay. A frozen
player is not sent until the freeze is lifted. A jail from before servers were recorded is claimed on start by the
server that has its world loaded (`event=jail_server_claim`); there is no setserver command for jails, so a jail on
the wrong server is set again there. A jail written in `modules/moderation/config.conf` records no server and is
used on whichever server holds the player, when that server has its world. Details: [moderation](moderation.md).

### Messages, presence and vanish

Every backend knows which server each online player is on, from what the others announce with their heartbeats.
With the network on, `/msg` and `/reply` reach a player on another server; that server applies the recipient's own
message toggle, reply setting, ignore list and vanish, and social spies on every server see the message once.
`/helpop` and staff chat reach the staff of every server with the sender's server in the line. `/seen` reports a
player connected elsewhere as online there.

A vanished player is hidden on every server only with `cross-server = true` in `modules/vanish/config.conf` on
every backend; a backend with it off shows them. Vanish is kept in the database, so a player switching servers stays
hidden while the switch takes less than `hop-window-seconds` (default 30). A vanished player the sender may not see
reads as offline to `/msg` and `/seen`.

### Moderation

Bans, mutes, warnings and IP bans are in the shared database and enforced on every server. A ban or an IP ban kicks
the player, or everyone on that address, wherever they are; a mute is read on the next chat line. `/kick` and
`/freeze` on a player connected to another server are refused with that server's name, since this server cannot
reach them; `/unfreeze` always runs. `/lockdown` is one row in the shared database, so it closes every server on
it to new joins, but its announcement goes only to the server it was run on. Freeze, command spy and staff
broadcasts stay on one server.

### Kits, ranks and vaults

With the network on, kit one-time claims and cooldowns live in the shared database; [kits](kits.md#on-a-network)
lists the edge cases. The kits themselves are files under `modules/kits/kits/` on each server: copy them.

A player's rank pointer is shared and a rankup is seen on the other servers at once, but the ladder in `ranks.conf`
and the commands a rankup runs are per server: keep the file the same everywhere, and give a rankup's permission
commands a permission plugin that shares its storage.

Vault contents are shared and each save tells the other servers to reload that vault. One window per vault is
enforced on each server only: the owner on one server and a staff member auditing the same vault on another can both
open it. The first window to close is saved. The later one is not written over it: the log shows
`event=vault_write_conflict` and `event=vault_items_unwritten` with that window's items, which stay in that server's
memory (a reopen there takes them over) and are lost when it stops. Compare them with the vault and give back what is
missing by hand.

### Holograms, NPCs and spawns

Holograms, NPCs and the main spawn are shared. A scope decides where each applies: none means every server,
`server:<name>` one server, `set:<name>` every server that lists the set in `network.shared-sets`. Set it with
`/hologram scope`, `/npc scope` and `/setmainspawn scope <scope>`; a plain `/setmainspawn` on a network writes this
server's own scope, and `scope network` writes the one every server falls back to. A hologram or NPC edit is shown on
the other servers at once; a main spawn reaches them at their next cache refresh. A world's own spawn is kept by the
world's uid, and named spawns are shared by name but only work on a server that has their world. `/spawn` never
changes server.

Guard regions are scoped the same way but keep their own list of shared sets, `network.shared-sets` in
`modules/guard/config.conf`, and `network.new-region-scope` there picks the scope of a new region. Guard has no
network switch: an edit is announced over the bus, and each server also reads the regions from the database every
`network.poll-seconds` (10), which is how edits arrive without Redis when a frame was missed. See [guard](guard.md).

### Economy

Balances live in the shared database, so a player has the same balance on every server. A transfer is one
transaction on the shared rows, and each change tells the other servers to drop their cached balance. `/pay` finds
its target among the players on this server, so it cannot pay a player who is on another one. The daily reward
stamp is shared, so switching servers does not pay it twice. Keep `gui.timezone` in `modules/economy/config.conf` the
same everywhere so transaction times agree.

## What does not cross servers

- Public chat, broadcasts, join and quit lines, the tab list, scoreboards and `/list`: each server shows its own.
  Use a proxy chat plugin for network chat.
- AFK state, `/back`, `/rtp`, `/spawn` and respawns: they stay on the server the player is on.
- `/kick` and `/freeze` of a player on another server, and `/invsee` or `/endersee` of one.
- Warps, homes, player warps, worlds and teleports to another server while that module's `cross-server` switch is
  off.
- Settings files, message files and menus. Copy them to every backend that should behave the same.
- Command control: configure the `commandcontrol` module on each backend; the proxy jar gates only proxy commands.

## Upgrading a network

1. Stop every backend and upgrade them together, with the proxy jar in the same window. A backend on an older
   release ignores frame types it does not know, and the proxy relays frames it does not know, but mixed releases
   are not supported.
2. Back up the database first. Migrations run on the first backend that starts.
3. Before that start, give every backend its own `network.server-name`, equal to its proxy name. A release that
   records servers clears warps and player warps recorded under `server-1`, and each backend claims its rows by world
   at its first start under a real name.
4. Start one backend, read its log, then start the others. Check the claim lines (`event=warp_server_claim`,
   `event=playerwarp_server_claim`, `event=home_server_claim`, `event=jail_server_claim`, `event=world_server_claim`,
   `world_server_claim_conflict`, `world_server_claim_skipped`) and fix a wrong claim with the
   [setserver commands](#renaming-a-server-or-fixing-a-wrong-claim), or `/setjail` for a jail.
5. Run `/uxmess doctor` on each backend once players are on: a connected bus line, the expected peer count and no
   server name failure.
6. Move permission grants and placeholders to the new names as [upgrading](upgrading.md) describes. That release
   renames every exemption node to `uxmessentials.bypass.*` and every placeholder to `%uxm_essentials_<path>%`.

### Recovering a failed world migration on MySQL or MariaDB

The migration that keeps managed worlds per server rebuilds the `world` and `world_setting` tables. MySQL and MariaDB
do not roll DDL back, so a run that fails half way leaves the tables partly renamed, and the next start fails on the
first rename. Look at which tables exist, with your table prefix:

- **`world_setting_v1_legacy` does not exist:** the copy finished and only the last drop failed. Drop
  `world_v1_legacy` and `world_claim` only, and start the server again. Do not drop `world` or `world_setting`: they
  hold the copied rows. The start repairs the migration history and runs the step once more.
- **`world_setting_v1_legacy` exists:** drop `world`, `world_setting` and `world_claim` if the migration created
  them, rename `world_v1_legacy` and `world_setting_v1_legacy` back to `world` and `world_setting`, fix the cause and
  start the server again.

SQLite and PostgreSQL roll a failed migration back on their own.
