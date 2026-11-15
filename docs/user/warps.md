# Warps and player warps

Two modules move players to named places. The warps module keeps server warps that staff set and everyone uses
with `/warp <name>`. The playerwarps module lets players set their own warps, decide who may visit them and charge
an entry fee. Both keep their warps in the plugin's database and teleport through the teleport module, so its
warmup, cooldown, jail and combat rules apply (see [teleport](teleport.md)).

## Files

| Module | Settings | Menus |
|---|---|---|
| Warps | `plugins/uxmEssentials/modules/warps/config.conf` | `modules/warps/gui/` |
| Player warps | `plugins/uxmEssentials/modules/playerwarps/config.conf` | `modules/playerwarps/gui/` |

Both modules ship enabled. Command names and aliases are in `commands.conf`: `/warp` answers to `/wp` and `/warps`
out of the box.

## Server warps

### Commands

| Command | Permission | What it does |
|---|---|---|
| `/warp <name>` | `uxmessentials.warp.use` | Teleports you to a warp. |
| `/warp <name> <password>` | `uxmessentials.warp.use` | Teleports you to a warp that has a password. |
| `/warp <name> <player>` | `uxmessentials.warp.others` | Sends an online player to the warp instead of you. |
| `/warp list` | `uxmessentials.warp.list` | Opens the warp menu, or the chat list with `list-display = "chat"`. The console always gets the chat list and must type `/warp list`. Bare `/warp` opens the same for a player with only `uxmessentials.warp.use`. |
| `/warp create <name>` | `uxmessentials.warp.set` | Sets a warp at your position. On an existing name it moves that warp. `/warp set <name>` does the same. |
| `/warp create <name> at <world> <x> <y> <z>` | `uxmessentials.warp.set` | The same at a given position; also `/warp createat <name> <world> <x> <y> <z>`. |
| `/warp move <name>` | `uxmessentials.warp.move` | Moves a warp to your position; `move <name> at <world> <x> <y> <z>` or `moveat` for a given one. |
| `/warp del <name>` | `uxmessentials.warp.delete` | Deletes a warp. |
| `/warp info <name>` | `uxmessentials.warp.info` | Shows the owner, creation time and cost. |
| `/warp lock <name>` | `uxmessentials.warp.lock` | Locks or unlocks a warp. |
| `/warp password <name> [password]` | `uxmessentials.warp.password` | Sets a password, or clears it when none is given. |
| `/warp rate <name> <1-5>` | `uxmessentials.warp.use` | Rates a warp. |
| `/warp rating <name>` | `uxmessentials.warp.use` | Shows a warp's average rating. |
| `/warp editor [name]` | `uxmessentials.warp.edit` | Opens the warp manager, or the editor of one warp. |
| `/warp setserver <name\|all> [server]` | `uxmessentials.warp.setserver` | Records the server a warp lives on, without moving it. See [Warps on a network](#warps-on-a-network). |

Warp names are lowercase, up to 64 characters. A subcommand word always wins over a warp of the same name, so a
warp called `list` or `info` is reached through the menu.

With the trailing argument of `/warp <name> <x>`, a sender who holds `uxmessentials.warp.others` and names an online
player sends that player; anything else is read as the password. A sent player must pass every check below
themselves and pays the cost.

### The editor

`/warp editor <name>` opens one warp: teleport there, set the icon from your main hand, file it under a category,
lock it, set a password, write welcome messages, pick departure and arrival sounds and particles, and give the warp
its own warmup and cooldown in seconds. `/warp editor` alone opens the manager, which lists every warp, creates new
ones and manages categories. A category can sit inside another one, and the warp menu groups warps by category.

When the editor sets no sound or particle, the `effects` block of the config plays. The two pickers offer the
entries of `selector-presets`, up to eighteen of each.

### Who may use a warp

The checks run in this order, and the first that fails stops the use with its own message:

1. The player holds `uxmessentials.warp.use.<warp>`, and the warp's required permission when it has one. The
   per-warp node defaults to every player, so nothing needs granting. Set it to false for a group to keep that group
   out of one warp, or set `uxmessentials.warp.use.*` to false and grant single warps back to close them all by
   default.
2. The warp is not locked, unless the player holds `uxmessentials.bypass.lock.warp`.
3. The right password was given, unless the warp has none or the player holds `uxmessentials.bypass.password.warp`.
4. The destination is safe: solid ground, and no solid block, lava or fire at the feet or head. A warp in a world
   that is not loaded counts as unsafe. `uxmessentials.bypass.safety.warp` skips this check.
5. The teleport module's jail, combat and cooldown checks.
6. The cost is taken.

### Costs and refunds

A warp can carry a cost, in any currency, and a required permission. Both are stored with the warp, but no command
or menu in this release sets them: a warp imported or created in game is free and open. They are the `cost`,
`cost_currency` and `required_permission` columns of the `warps` table (add your table prefix). Without the economy
module the cost is ignored and the warp is free.

The cost is taken after every check above passes, so a refused use costs nothing. It is taken before the warmup,
and given back once, in the currency it was taken in, if the player does not arrive:

- they move during the warmup (or take damage or interact, when the teleport module cancels on those);
- they start another teleport, which replaces the warmup;
- the hop after the warmup fails, or they log off before it lands;
- on another server, the hop does not land (see below).

The player reads a line when the money comes back.

### Warp signs

A player with `uxmessentials.warp.sign.create` writes `warp-sign-header` (`[Warp]`) on the first line of a sign and
a warp name on the second. Right-clicking the sign with `uxmessentials.warp.sign.use` runs `/warp <name>` for that
player, with every check and the cost. A sign that names a missing warp is broken and dropped, or with
`destroy-warp-sign-on-error = false` kept with its text cleared. Changing the header stops every sign written
under the old one.

### Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module and `/warp` on or off. |
| `list-display` | `"gui"` | `gui` opens the warp menu, `chat` prints the list. |
| `warp-sign-header` | `"[Warp]"` | The first line that makes a warp sign, matched without case or colour. |
| `destroy-warp-sign-on-error` | `true` | Breaks a new sign that names a missing warp. |
| `cross-server.enabled` | `false` | Sends a player to a warp of another server. |
| `effects.sound.departure.type`, `effects.sound.arrival.type` | `ENTITY_ENDERMAN_TELEPORT` | The sound when the editor set none. |
| `effects.particle.departure.type`, `effects.particle.arrival.type` | `PORTAL` | The particle when the editor set none. |
| `selector-presets.particles`, `selector-presets.sounds` | 14 each | What the editor's pickers offer. |

`world-blacklist`, a list of world names, is read but not in the shipped file: `/warp create` refuses a warp in one
of those worlds.

## Warps on a network

A warp remembers the server it was set or moved on, once the `network` block of `config.conf` is on and
`network.server-name` is not the shipped `server-1`. With `cross-server.enabled = true`, `/warp`, a warp sign or the
menu on another server sends the player to the warp's server through the proxy:

1. The lock, password and permission checks run here. The safety check does not: only the warp's server has its
   world, so it judges the spot when the player lands.
2. The jail, combat and cooldown checks run here, then the cost is taken. There is no warmup: the player is sent
   at once.
3. The warp's server moves the player `cross-server.arrival-delay-ticks` after they join (root `config.conf`).
4. The cost comes back if the proxy never moves them within `cross-server.request-ttl-seconds`, if the warp was
   deleted or moved to another server meanwhile, if its world is not loaded there, or if the spot is unsafe and the
   player lacks `uxmessentials.bypass.safety.warp`.

The refund is paid on the server the player reaches, so keep the economy module on everywhere. It needs a proxy, a
shared database, `network.server-name` equal to the proxy's name for each server, and the switch on every server.
With the switch off, every warp is used on the server it is opened from, whatever server it was set on. See
[network](network.md) for the shared parts.

A server still on `server-1` records no server on the warps it sets or moves, so they stay usable on whatever server
opens them. It claims nothing at start, and `/warp setserver` with no server named asks for a name there. A warp
recorded on another server is still sent there from it while the switch is on.

### Claiming and moving warps

A warp that records no server (set before servers were recorded, or on a server that records none) is claimed at
start by the first named server that has its world loaded. The log names each claim (`event=warp_server_claim`).
Fix a wrong claim or follow a renamed `network.server-name` with:

```
/warp setserver spawn lobby
/warp setserver all survival
/warp setserver arena unscoped
```

With no server named, the command means this server. `all` moves every warp of the network. `unscoped` clears the
record, so the server that has the warp's world claims it again at its next start. A name no running server answers
to is set anyway, with a warning, so you can prepare a server that is not up yet. Two servers that run copies of one
world folder share its `uid.dat`, and the first to start claims every warp in it; a world loaded after start is
claimed at the next restart.

## Player warps

### Commands

| Command | Permission | What it does |
|---|---|---|
| `/pwarp` | `uxmessentials.pwarp.use` | Opens the player warp browser. |
| `/pwarp <name> [password]` | `uxmessentials.pwarp.use` | Teleports to a player warp. |
| `/setpwarp <name>`, `/pwarp set <name>` | `uxmessentials.pwarp.set` | Creates a warp at your position, or moves your warp of that name. |
| `/pwarps [player]`, `/pwarp list [player]` | `uxmessentials.pwarp.list` | Lists your warps, or a player's public ones. |
| `/pwarp info <name>` | `uxmessentials.pwarp.info` | Owner, access, price, visits and rating. |
| `/pwarp edit <name>` | `uxmessentials.pwarp.edit` | Opens the warp's editor. |
| `/pwarp move <name>` | `uxmessentials.pwarp.move` | Moves the warp to your position. |
| `/pwarp rename <name> <newName>` | `uxmessentials.pwarp.rename` | Renames it. |
| `/pwarp del <name>` | `uxmessentials.pwarp.delete` | Removes it. |
| `/pwarp displayname\|description <name> [text]` | `uxmessentials.pwarp.displayname`, `.description` | Sets or clears the shown name or the description. |
| `/pwarp icon <name> [icon]`, `/pwarp category <name> [id]` | `uxmessentials.pwarp.icon`, `.category` | Sets or clears the icon or the category. |
| `/pwarp visibility public\|private <name>` | `uxmessentials.pwarp.public` | Opens the warp to everyone, or closes it. |
| `/pwarp access <name> <PUBLIC\|PASSWORD\|WHITELIST\|PRIVATE>` | `uxmessentials.pwarp.access` | Sets who may enter. |
| `/pwarp password <name> <password\|clear>` | `uxmessentials.pwarp.password` | Sets or clears the password, stored hashed. |
| `/pwarp price <name> <amount> [currency]` | `uxmessentials.pwarp.price` | Sets the entry fee; 0 is free. |
| `/pwarp members <name> add <player> <co-owner\|manager>`, `remove <player>` | `uxmessentials.pwarp.members` | Manages members. |
| `/pwarp whitelist <name> add\|remove <player>` | `uxmessentials.pwarp.whitelist` | Manages the guest list. |
| `/pwarp ban <name> <player> [duration] [reason]`, `/pwarp unban <name> <player>` | `uxmessentials.pwarp.ban` | Bars a player, for a time such as `7d`, `12h` or `30m`, or for good. |
| `/pwarp rate <name> <1-5>` | `uxmessentials.pwarp.rate` | Rates a warp. |
| `/pwarp favourite\|unfavourite <name>` | `uxmessentials.pwarp.favourite` | Stars or un-stars a warp. |
| `/pwarp transfer <name> <player>` | `uxmessentials.pwarp.transfer` | Hands the warp to another player. |
| `/pwarp withdraw <name>` | `uxmessentials.pwarp.withdraw` | Pays the warp's earnings to its owner. |
| `/pwarp sponsor <name> [days]` | `uxmessentials.pwarp.sponsor` | Buys a pinned slot in the browser, while `sponsor.enabled` is on. |
| `/pwarp admin restore\|purge <id>`, `setowner <id> <player>`, `reload` | `uxmessentials.pwarp.admin` | Staff tools by warp id. `purge` (also `delete`) asks for `confirm`. |

A player warp name is 3 to 32 characters of lowercase letters, digits, `_` and `-`, unique across the server. Words
the command uses, such as `set`, `list` or `rate`, cannot be names. A new warp starts private.

### Members, access and fees

The owner, co-owners and managers are members. A co-owner may do everything but delete, transfer, sponsor and manage
members; a manager may only edit the name, description, icon and category and manage the whitelist and bans.
`uxmessentials.pwarp.gui` lets staff manage every player's warps from the menus.

A visit runs these checks in order:

1. The warp is active. A warp suspended or archived for unpaid rent refuses everyone, its owner too.
2. The player is not banned, unless they hold `uxmessentials.bypass.ban.pwarp`.
3. A member skips the next step and the fee.
4. Access: `PUBLIC` lets everyone in, `PASSWORD` needs the password (`uxmessentials.bypass.password.pwarp` skips
   it), `WHITELIST` needs the guest list (`uxmessentials.bypass.whitelist.pwarp`), `PRIVATE` refuses.
5. The destination is safe, members included, unless the player holds `uxmessentials.bypass.safety.pwarp`.
6. The teleport module's jail, combat and cooldown checks.
7. The entry fee, unless the player holds `uxmessentials.bypass.cost.pwarp` or no economy is available.

The fee is taken before the warmup. The owner is paid only once the visitor lands, minus `payout.cut-percent` for
the server; until then the money is held. A visit that does not land (moved during the warmup, replaced by another
teleport, a failed hop, the visitor leaving) gives the visitor the whole fee back and pays the owner nothing. A
landing whose owner share cannot be banked gives the fee back too. Earnings wait in the warp's bank for
`/pwarp withdraw`, which pays the owner even when a co-owner runs it. With `payout.auto-payout = true` the owner is
paid at once, in a currency that can be paid to an offline player.

### Limits and categories

A player owns up to `default-limit` warps (3). `uxmessentials.pwarp.limit.<n>` raises it, the largest node held
wins, and `-1` removes the limit. `uxmessentials.pwarp.limit.<world>.<n>` sets the limit used when the new warp is
in that world, but it is compared with all of the owner's warps, in every world, not only those in that world.

Categories come from the warp categories made in `/warp editor`. While none exist, the browser offers `building`,
`shop`, `pvp`, `farm`, `redstone` and `misc`. The browser sorts by name, age, visits, unique visitors, rating,
number of ratings, favourites or distance. Ratings rank by a Bayesian score: `ratings.confidence` votes at the
server's average are added to each warp, so a single five-star vote does not top the list.

### Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module and its commands on or off. |
| `default-limit` | `3` | Warps per player without a `limit` node. |
| `payout.cut-percent` | `10` | The server's share of each entry fee, 0 to 100. |
| `payout.auto-payout` | `false` | Pays the owner at once instead of banking for `/pwarp withdraw`. |
| `ratings.confidence` | `10` | How strongly few votes are pulled toward the average. |
| `ratings.rewards.*` | off | Money or a console command for the rater (once per warp) and the owner (once per new rater). |
| `rent.*` | off | Charges `amount` every `period-days`, from the warp's bank first, then the owner. An unpaid warp is suspended for `grace-days`, then archived; `hard-delete-after-days` above 0 deletes it later. Owners get mail `reminder-hours` before a term ends. `exempt` lists players, categories and worlds that never pay. |
| `sponsor.*` | off | Sells `slots` pinned browser slots at `price` for up to `duration-days`, `max-concurrent-per-player` each, with `cooldown-days` before the same warp is sponsored again. |
| `cross-server.enabled` | `false` | Sends a visitor to a player warp of another server. |

Rent and sponsorship need an economy. An archived warp comes back with `/pwarp admin restore <id>`.

### Player warps on a network

A player warp records its server the same way as a server warp, and an unrecorded one is claimed at start by the
named server that has its world (`event=playerwarp_server_claim`). There is no `setserver` command: the owner moves
the warp with `/pwarp move` on the right server.

With `cross-server.enabled = true`, a visit to a warp of another server runs the checks above here, takes the fee and
sends the visitor through the proxy, with no warmup. The warp's server moves them on join, pays the owner then, and
refunds the visitor if they never arrive or the warp is gone, no longer active or moved. The safety check runs on the
server the visit starts from, before the visitor leaves, so a warp in a world that server does not have counts as
unsafe and only a player with `uxmessentials.bypass.safety.pwarp` gets through. This is a known issue: server warps
check the spot on the server the player lands on.

With the switch off, a visit to a warp of another server is refused as unavailable. The fee is taken first and given
straight back, and the jail, combat and cooldown checks do not run for it.

## Other permissions

| Node | Default | Effect |
|---|---|---|
| `uxmessentials.module.warps`, `uxmessentials.module.playerwarps` | op | Reload and inspect the module. |

The full lists are in [permissions-gameplay](permissions-gameplay.md).
