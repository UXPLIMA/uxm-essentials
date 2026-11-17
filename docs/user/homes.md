# Homes

The homes module gives every player a grid of numbered home slots. `/home` opens the grid: an empty slot sets a
home at the player's position, a filled one opens a menu to teleport, rename, relocate, delete, change the icon or
make the home public. Homes are kept in the plugin's database, one row per player and slot.

## Turning it on

The module ships enabled. Its settings are in `plugins/uxmEssentials/modules/homes/config.conf` and the menus in
`modules/homes/gui/`. After an edit, `/uxmess reload` reads them again.

## How many homes a player gets

A player may keep `default-limit` homes (3 out of the box). The grid shows that many slots, so slot numbers run
from 1 to the limit. A permission raises the limit for a player:

| Node | Effect |
|---|---|
| `uxmessentials.home.limit.<n>` | up to `n` homes |
| `uxmessentials.home.limit.<world>.<n>` | up to `n` homes in `<world>`, counting only the homes there; the limit on all homes still applies |
| `uxmessentials.home.limit.-1` | no limit; the grid stops at `unlimited-max` slots |

With `limit-mode = "highest"` the largest tier the player holds wins: a player with `limit.5` and `limit.10` keeps
10 homes. With `limit-mode = "stack"` the tiers add up, so the same player keeps 15. A world node is checked on its
own, the largest one held for that world winning in either mode: a player with `limit.10` and
`limit.world_nether.2` keeps up to 10 homes, at most 2 of them in `world_nether`. Moving a home into a world counts
toward that world's limit as setting one there does.

A player who loses a rank keeps the homes they already have. They cannot set a new one until they are under the
new limit again, even if a lower slot is empty; the chat line names the limit.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module and its commands on or off. |
| `default-limit` | `3` | Homes for a player with no `limit` node. |
| `limit-mode` | `"highest"` | How several `limit` nodes combine: `highest` or `stack`. |
| `unlimited-max` | `1000` | The size of the grid for a player with no limit. |
| `date-format` | `"dd/MM/yyyy HH:mm"` | How the creation time of a home reads in its tooltip. |
| `disabled-worlds` | `[]` | Worlds where homes cannot be set or relocated. |
| `block-unsafe-sethome` | `true` | Refuses a home in air, lava or inside blocks. |
| `consider-midair-unsafe` | `true` | Also refuses a spot with no solid ground below, so nobody sets a home while flying. |
| `midair-ground-depth` | `5` | How many blocks down the midair check looks for ground. |
| `confirm-delete` | `true` | Asks yes or no before a home is deleted from the menu. |
| `confirm-relocate` | `false` | Asks before a home is moved to the player's position. |
| `confirm-unsafe-teleport` | `true` | Warns before a teleport to a home that is no longer safe. |

### Costs

The `economy` block charges for home actions when an economy is available. With `enabled = false`, or a cost of 0,
the action is free.

| Key | Meaning |
|---|---|
| `economy.enabled` | Charges the costs below. |
| `economy.create-cost` | Charged when a new home is set. |
| `economy.relocate-cost` | Charged when a home is moved. |
| `economy.teleport-cost` | Charged on every teleport to a home. |
| `economy.currency` | The currency id; `default` is the server's main currency. |

The cost is taken only after every other check passes, so a refused home never costs money. A player who cannot
pay gets no home. `uxmessentials.bypass.cost.home` makes every action free.

### Land claims

When a supported claim plugin is installed, the `claims` block decides where homes may go. The supported plugins
and their switches are in the `claims` block of the root `config.conf`.

| Key | Default | Meaning |
|---|---|---|
| `claims.enabled` | `true` | Follows the claim plugin; `false` ignores claims. |
| `claims.require-claim` | `false` | A home may only be set inside a claim the player is trusted in. |
| `claims.block-foreign-claims` | `true` | A home may not be set inside another player's claim. |
| `claims.foreign-claim-chunk-distance` | `0` | Also refuses this many chunks around a foreign claim; 0 turns it off. |
| `claims.check-teleport-access` | `true` | Refuses a teleport to a home in a claim the player is no longer trusted in. |

A GriefPrevention admin claim has no owner, so it counts as foreign: players set homes there only when an
administrator has trusted them.

### Homes on other servers

A home remembers the server it was set or relocated on, and its tooltip in the grid and in its menu names that
server. A server records its name on homes only while the `network` block of `config.conf` is on and
`network.server-name` is not the shipped `server-1`, so a standalone server never ties its homes to a name it drops
when it joins a network.

With `cross-server.enabled = true`, a teleport to a home of another server (from the grid, `/home visit` or
`/home admin <player> tp`) sends the player there through the proxy. The jail, combat and cooldown checks run before
anything is charged, the teleport cost is taken only once they pass, and it is refunded if the player does not arrive.
When the owner travels to their own home, the claim access check runs on arrival, and so does the unsafe spot check
while `confirm-unsafe-teleport` is on: there is nobody to confirm with on arrival, so an unsafe home refuses the
player, with a refund, unless they hold `uxmessentials.bypass.safety.home`. A visit or a staff teleport skips those
checks, as it does on one server. A respawn at home never changes server; it uses the lowest-slot home of the server
the player respawns on.

This needs a proxy in front, a shared database, and `network.server-name` in `config.conf` set to the name the proxy
gives each server. The refund is paid on the server the player reaches, so the economy module must be on there too,
on every server a home hop can land on. The timings are in the `cross-server` block of the root `config.conf`. With
the switch off, which is the default, every home is used on the server it is opened from, as before.

Homes from before servers were recorded are claimed on start by the server that has their world loaded. Two servers
that run a copy of the same world folder share its `uid.dat`, so the first to start claims every home in it; give each
copy its own `uid.dat`. `/home setserver <from> <to>` moves every home recorded on one server to another, after a
renamed `network.server-name` or to undo a wrong claim. `unscoped` as the new server clears it, so the server that
has each home's world claims it again at its next start; it is refused as the old server, since those homes are the
claim's to take. `server-1` is refused as the new server: a server still under that name records no server, so no
server would reach a home recorded on it. A home relocated on a server that records no server is cleared the same
way.

| Key | Default | Meaning |
|---|---|---|
| `cross-server.enabled` | `false` | Sends a player to a home of another server through the proxy. |

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/home` | `uxmessentials.home.use` | Opens the grid of your homes. |
| `/home visit <player> [slot]` | `uxmessentials.home.visit` | Teleports to another player's public home, or one you were invited to. Without a slot, the first home. Visiting your own home costs the teleport cost, as from the grid. |
| `/home invite <player> [slot]` | `uxmessentials.home.invite` | Lets a player visit one of your private homes. |
| `/home uninvite <player> [slot]` | `uxmessentials.home.invite` | Takes that access back. |
| `/home admin <player> list` | `uxmessentials.home.admin` | Lists a player's homes. |
| `/home admin <player> tp <slot>` | `uxmessentials.home.admin` | Teleports you to one of them. |
| `/home admin <player> set [slot]` | `uxmessentials.home.admin` | Sets a home for them at your position. |
| `/home admin <player> del <slot>` | `uxmessentials.home.admin` | Deletes one of their homes. |
| `/home admin <player> clear` | `uxmessentials.home.admin` | Deletes all of their homes. |
| `/home admin <player> info <slot>` | `uxmessentials.home.admin` | Shows the details of one home. |
| `/home setserver <from> <to>` | `uxmessentials.home.setserver` | Moves every home recorded on one server to another, without moving any home. |

Slots in commands count from 1, as in the grid. The `<player>` of the admin commands, of `visit` and of the invites may be offline or on another server. Other nodes: `uxmessentials.home.icon` (pick an icon, everyone),
`uxmessentials.bypass.safety.home` (no warning before an unsafe teleport, and landing at an unsafe home on another
server, operators).

## Placeholders

| Placeholder | Value |
|---|---|
| `%uxm_essentials_homes_count%` | How many homes the player has. |
| `%uxm_essentials_homes_limit%` | How many the player may keep. |
| `%uxm_essentials_homes_left%` | How many more the player may set. |
| `%uxm_essentials_homes_list%` | The names of the player's homes, comma separated. |
| `%uxm_essentials_homes_<n>_world%` | The world of the nth home; `_x`, `_y` and `_z` give its block. |

## Examples

**VIPs get five homes, the nether gets none.** Grant `uxmessentials.home.limit.5` to the VIP group and set
`disabled-worlds = ["world_nether"]`.

**Homes cost money to set but are free to use.** Set `economy.enabled = true` and `economy.create-cost = 500`,
leave the other costs at 0, and grant `uxmessentials.bypass.cost.home` to staff.

**A shared base.** A player opens their home in `/home`, clicks the visibility dye to make it public, and others
reach it with `/home visit <player> <slot>`. To keep it private, use `/home invite <player> <slot>` instead.
