# Kits

A kit is a set of items a player claims with `/kit <name>`. Each kit can wait between claims, be claimable once,
need a permission, cost money, pay money out, run only on some days or hours, and run sounds, particles and
commands when it is claimed.

## Files

`plugins/uxmEssentials/modules/kits/config.conf` holds two switches; every kit is its own file under
`modules/kits/kits/<name>.conf`, and kit categories are under `modules/kits/categories/`. The menus are in
`modules/kits/gui/`.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module and `/kit` on or off. |
| `list-display` | `"gui"` | `/kit list` opens a menu (`gui`) or prints a clickable list (`chat`). The console always gets the list. |
| `showkit-display` | `"gui"` | `/kit show` opens a read-only preview (`gui`) or prints the contents (`chat`). |

## Making a kit

Fill your inventory with the items and run `/kit create <name>`. The kit is written to its file with your items.
`/kit editor` opens the kit manager, `/kit editor <name>` opens the kit's contents for editing, and
`/kit editor <name> save` replaces the contents with your current inventory. `/kit del <name>` removes the kit.

The rules of a kit are keys in its file:

| Key | Meaning |
|---|---|
| `cooldown` | Seconds between two claims by the same player. 0 means no wait. |
| `one-time` | `true`: each player claims it once. `/kit reset` gives the claim back. |
| `permission` | `true`: claiming needs `uxmessentials.kit.<name>`, or the node in `permission-node`. |
| `permission-cooldowns` | A shorter wait per group, for example `{ vip = 3600 }`: a player with `uxmessentials.kit.cooldown.vip` waits one hour. The shortest wait the player holds wins. |
| `cost` | The price, as `"<amount> <currency>"`, for example `"250 coins"`. Empty is free. |
| `claim-money`, `claim-money-currency` | Money paid to the player on every claim. |
| `stock` | How many times the kit may be claimed in total, across all players. 0 means no limit. |
| `on-full` | `drop` (the default) drops what does not fit at the player's feet; `deny` refuses the claim. |
| `unlock-once` | `true`: the cost is paid on the first claim only. |
| `schedule` | When the kit can be claimed: `days` (for example `["saturday", "sunday"]`), `daily-start` and `daily-end` (`"18:00"`), `from` and `until` (`"2030-12-24T00:00"`). |
| `first-join` | `true`: new players get the kit when they first join. |
| `display-name`, `display-material`, `display-lore` | How the kit looks in `/kit list`. |

A daily window includes its start and ends just before its end; a window whose end is earlier than its start, such
as `22:00` to `06:00`, runs past midnight. `until` is the first moment the kit is closed.

## What happens on a claim

The checks run in this order, and the first that fails stops the claim with its own message: the kit exists, it is
inside its schedule, the player has the permission, a one-time kit was not claimed yet, the cooldown is over, any
requirements are met, the inventory has room when `on-full = "deny"`, stock is left, and the player can pay. Only
then is the cost taken, the items given, the cooldown started and the claim money paid. A refused claim costs
nothing and starts no cooldown.

## Commands

| Command | Permission | Default | What it does |
|---|---|---|---|
| `/kit <name>` | `uxmessentials.kit.use` | everyone | Claims a kit. |
| `/kit list` | `uxmessentials.kit.use` | everyone | Lists the kits you can claim. |
| `/kit show <name>` | `uxmessentials.kit.preview` | everyone | Shows what is in a kit. |
| `/kit <name> <player>` | `uxmessentials.kit.others` | operators | Gives a kit to a player. The player's own cooldown, cost and permission apply. |
| `/kit create <name>` | `uxmessentials.kit.edit` | operators | Makes a kit from your inventory. |
| `/kit del <name>` | `uxmessentials.kit.edit` | operators | Deletes a kit. |
| `/kit editor [name [save]]` | `uxmessentials.kit.edit` | operators | Edits kits in a menu. |
| `/kit reset <player> [kit]` | `uxmessentials.kit.reset` | operators | Clears a player's claims and cooldowns, for one kit or all. |

`uxmessentials.kit.cooldown.bypass` skips cooldowns and lets a one-time kit be claimed again.
`uxmessentials.oversizedstacks` lets a player receive stacks larger than the item normally allows.

## Placeholders

| Placeholder | Value |
|---|---|
| `%uxmessentials_kits_list%` | The kits the player may claim, comma separated. |

## Examples

**A daily reward.** `/kit create daily`, then in `kits/daily.conf` set `cooldown = 86400`. Players claim it once a
day with `/kit daily`.

**A starter kit for new players.** `/kit create starter`, then set `one-time = true` and `first-join = true`.

**A weekend kit that costs money.** Set `cost = "500 coins"` and `schedule { days = ["saturday", "sunday"] }`.
