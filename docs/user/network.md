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
| The same `network.bus.secret` on every backend, for the Velocity carrier | `config.conf` | Without it the Velocity carrier is off. See [signed frames](#signed-frames). |
| The same release on every backend and the proxy | `plugins/` | See [upgrading a network](#upgrading-a-network). |

A server of the network that has no business in the shared database (a creative or build server) keeps the same
`storage` block and sets `storage.local-only = true`: it stays on its own SQLite file. Moving an existing SQLite
server to the shared database is `storage.copy-from-file = true` with the new type, for one restart; the log says how many rows were copied, and tables that already hold rows are left alone.

A proxy in offline mode (a cracked network) makes `Steve` and `steve` two players, but a backend reads the proxy's mode from Paper's proxy settings, which default to online. With `lookup.name-case = "auto"` (the default) the first offline-mode join corrects that, logs the warning `event=name_case_detected` and keeps it in `name-case.state` in the plugin folder, so the next start is right before anybody joins. Set `lookup.name-case = "sensitive"` in `config.conf` on every backend to have it right from the first join; `"insensitive"` forgets a kept switch.

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
  when its first player joins, and warps, spawns, worlds, NPCs, holograms (with who may see them) and the vote
  counter also reload every `network.cache-refresh-seconds` (default 300; `0` turns the timer off). Balances, homes
  and other per-player data expire on their own after a few minutes.
- Frames waiting for a player are kept in a queue of `network.bus.outbound-queue-size` (default 256). Only the
  newest heartbeat waits. When the queue is full, a frame is dropped in this order: the waiting heartbeat, then the
  oldest ordinary change (warps, homes, holograms and the like), and only when nothing else is waiting the oldest
  balance, vault, ban, mute, jail, freeze or vanish change. The log reports drops, at most once a minute with the
  running count. Redis has no such queue: `transport = redis` sends each frame at once.
- A frame larger than 32767 bytes is never sent; the log reports the drop, at most once a minute.
- `network.bus-channel` must match the proxy. Leave it as shipped.

#### Signed frames

A player's client can send messages on the bus channel too. The proxy plugin stops them, but a proxy without it, a
BungeeCord proxy, or a backend players can join directly passes them on. So every backend signs the frames it sends
through the proxy with `network.bus.secret` and acts only on frames signed with the same secret:

```hocon
network {
  bus {
    secret = "<output of openssl rand -hex 32>"
  }
}
```

- Use 32 or more random characters, for example the output of `openssl rand -hex 32`, and the same value on every
  backend. The proxy needs none: it relays signed frames unchanged. Keep it out of anything you share.
- While it is empty, shorter, the example above, made of fewer than 8 different characters, or one pattern of up to
  16 characters repeated, `transport = "velocity"` and `"both"` send and accept nothing through the proxy. The start
  log says so, and `/uxmess doctor` reads `proxy OFF`. Redis needs no secret: only the backends reach it.
- A frame signed with another secret, or more than 60 seconds away from this backend's clock, is refused and logged
  at most once a minute, and the doctor reads `REFUSED`. Keep every backend on the same secret and release, and their
  clocks in time. A client sending forged frames shows the same way.
- The signed time also stops a captured frame from being replayed later. Within the 60 seconds, private messages,
  staff chat, helpop, teleport requests, jails, presence, world changes and vote parties carry an id and act once,
  trade signals are settled by the shared escrow, and a repeated cache update is harmless. A vanish change carries no
  order, so without the proxy plugin a client could hold one back or repeat it within that time.
- Signing proves who sent a frame; it does not hide what a frame says. Without the proxy plugin, or on a backend
  players can join directly, the client carrying the frames can read them: private messages, staff chat, helpop and
  IP bans among them. Install the proxy plugin to keep them private.

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
only, so a copy that arrives very late, after that backend restarted, can act again. Vote parties are the exception:
a party sent over both carriers carries the time it fired, and a backend that is itself on both carriers drops one
that fired more than 60 seconds before it started, so the late proxy copy of a party it already paid does not pay
again. The 60 seconds cover a Redis copy sent while the backend was still starting and a sender whose clock runs a
little ahead; a backend that restarts within 60 seconds of a party can still pay its late proxy copy again. A party
sent on the proxy alone, or received by a backend on the proxy alone, is never dropped this way.

The rule has one cost. If a party's Redis copy never reached a backend (Redis was down, or the backend was) and that
backend restarted while the proxy copy was still queued, the proxy copy arrives after the restart and is dropped, so
the party is not paid on that backend. Use `both` only while moving from one carrier to the other.

## The proxy plugin

`uxmEssentials-velocity` relays bus frames, and, with `command-control.enabled = true` in its `config.conf`, hides
and gates the proxy's own commands (`/server`, `/glist`, `/send` and proxy plugins' commands) by LuckPerms group. With
`bus.secret` in its `config.conf` set to the backends' `network.bus.secret`, it also tells each backend a player
reaches whether they switched servers or logged in (see [second factor](#second-factor)). It stores no data: each
backend announces its own players.
Backend commands are gated by the `commandcontrol` module on each backend.

## Checking a network

`/uxmess doctor` on a backend shows:

- the bus line: disabled, or one verdict per carrier. Velocity reads `proxy OFF` without a usable
  `network.bus.secret`, and connected only while signed frames come back through the proxy plugin, at least once in
  three heartbeats. Frames ride a player's connection, so with nobody online on
  the backend it reads `idle` and says so. Otherwise it reads `NOT ANSWERING`: install `uxmEssentials-velocity`, or
  update it to the same release, since an older proxy jar does not answer. Redis reads connected while its
  connection is up, `DISCONNECTED` otherwise. Frames refused for a wrong secret or clock in the same three heartbeats
  add `REFUSED` and the count. A carrier not working, or refusing frames, is a warning, since the backends reached only
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
| Moderation | Bans, mutes, warnings, IP bans, jails, history | A ban or IP ban kicks everywhere; a jail sends the player to the jail's server; a freeze holds everywhere | none |
| Messaging | Mail, ignore lists | `/msg`, `/reply`, `/helpop`; staff chat in the staff module | none |
| Vanish | Vanish state | Hidden on every server | `vanish` `cross-server` |
| Kits | One-time claims and cooldowns | none | none |
| Ranks | Each player's rank and prestige | none | none |
| Vaults | Vault contents | none | none |
| Security | Wrong PINs and codes, lockouts, proofs to carry | A lockout holds everywhere; a proof carries across a switch | `security` `join-verification.switch-grace-seconds` |
| Holograms, NPCs, main spawn | Definitions, with a scope | Edits show on every server in scope | `network.shared-sets` |
| Guard | Regions, with a scope | Edits apply on every server in scope | none; shared sets in `modules/guard/config.conf` |
| Trade | Escrow | Two players on different servers trade | `trade` `cross-server` |
| Vote | Party counter | A party pays players on every server | `voteparty.network-rewards` in `vote`, default on |
| Playtime | Daily rows | Time on every server adds up | none |
| Skin | Each player's chosen skin | A change is worn at once on the server the player is on | none |

### Cross-server moves

Homes, warps, player warps, worlds and teleport requests share one way of moving a player. The origin runs its
checks (jail, combat, cooldown), takes the cost, records the move in the shared database and asks the proxy to
connect the player. The server they join moves them `cross-server.arrival-delay-ticks` after the join (default 20).
Both keys are in the root `config.conf` and read on start.

- **Money.** If the player is not moved within `cross-server.request-ttl-seconds` (default 30), the origin gives the
  money back. If the target refuses them on arrival (the warp or home is gone, the world is full or will not load,
  the spot is unsafe), the server they reached gives it back, in the currency it was taken in; keep the economy module
  on wherever a move can land. A server that stops after taking the player in but before moving them gives the money
  back as it stops. A player warp's fee goes to its owner only when the visitor arrives.
- **Money at shutdown.** A stopping server waits up to 10 seconds for refunds and payments already under way. What
  is still running after that is not waited for: the log has `event=cross_server_hop_drain_incomplete`, and a refund
  or payment that then fails is logged as `event=cross_server_hop_refund_failed` or `cross_server_hop_settle_failed`
  and is not paid. A refund can only reach an economy that is still running. A currency kept by another
  plugin, through Vault or Treasury, is given back only if that plugin stops after uxmEssentials; if it stopped
  first, the log has `event=cross_server_hop_refund_failed` with the player and the amount. A refund for a move the
  server had already started logs `outcome=refunded_after_move_started`: the server may still finish that move, so
  check whether the player arrived as well.
- **Safety.** Warps, player warps, homes and worlds check the spot on the server the player lands on, the only one
  that has its world.
- **Currencies.** Define the same currency ids on every server. A paid hop records the currency it was taken in,
  `default` resolved on the server that charged it, and is refunded, or paid to a player warp's owner, in exactly
  that one. A server that does not know it neither refunds it nor pays it in another currency: it logs
  `event=cross_server_hop_refund_failed` with the player, the amount and the currency.
- **Warmup.** A warp or player warp on another server sends the player right after the checks and the charge, with
  no warmup. An accepted teleport request runs the usual warmup on the mover's server first. Sending a player
  replaces a warmup still running on the server they leave, as a new local teleport does, and refunds it if it was
  paid.
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

Bans, mutes, warnings and IP bans are in the shared database and enforced on every server. A ban or an IP ban kicks the
player, or everyone on that address, wherever they are; a mute is read on the next chat line, and reaches private
messages and blocked commands through the bus (without a bus carrier, within a minute: every server reads its players'
mutes again once a minute, and again after `/uxmess import`). `/kick` and `/freeze` on a player connected to another
server are refused with that server's name, since this server cannot reach them. Whether a target is exempt is read as
[punishment exemption](punishment-exemption.md) says. A freeze is announced over the bus and held on every server, so a
frozen player who switches server stays frozen, and `/unfreeze` on any server lifts it everywhere. Over Velocity a
server that was empty when the freeze was announced never heard it, so the proxy plugin keeps a frozen player off other
servers and sends one who logs out frozen back to the server that froze them. Redis reaches every server, empty or not.
A server that restarts, or reloads its moderation module, holds no freeze any more, and the proxy lets go of the players
frozen there once that server has a player online to carry the news. `/lockdown` is one row in the shared database, so
it closes every server on it to new joins, but its announcement goes only to the server it was run on. Command spy and
staff broadcasts stay on one server. The shared database holds bans (a permanent ban is stored as one that ends far in
the future), mutes, warnings, jails and the jails made with `/setjail`, IP bans, last-seen records and the address
history, the sanction history and the lockdown flag.

### Second factor

Wrong PINs and authenticator codes, and the lockout they end in, are kept in the shared database: a player gets
`max-attempts` tries across the whole network, not on each server, and a lockout holds on every server. A player who
proved their factor keeps the proof when they switch servers, so they are not asked again on each one:

- Once any server has had the word of `uxmEssentials-velocity` about a player who joined it, which the shared
  database remembers, a proof is carried only across a switch the proxy confirms, on the connection the proof was
  made over. Every new login asks again. The proxy says so over the player's own connection, signed with its
  `bus.secret`, so it arrives with any `network.transport` as long as `network.bus.secret` is set to the same value;
  without that, every switch asks again. After removing the proxy plugin, `/security forget-proxy` lets proofs carry
  on the address again.
- On a network that has never had the proxy plugin's word, a player who arrives from the address the proof was made
  from is let in unasked within `switch-grace-seconds` (30) of leaving a server, or while that server still lists
  them. That includes logging in again from the same address within that time, by the player or by anyone behind
  the same address (a shared home or NAT) who can log in to the account: the trade-off of having no proxy to tell a
  switch from a new login. Install the proxy plugin where that matters.
- `switch-grace-seconds = 0` asks on every server. `/2fa force`, a factor reset and a lockout drop a carried proof.
  A proof that cannot be read (the database is down) costs no attempt, up to three times per lockout window.

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
3. Before that start, give every backend its own `network.server-name`, equal to its proxy name, and, for the
   Velocity carrier, the same `network.bus.secret` (see [signed frames](#signed-frames)). A release that
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
