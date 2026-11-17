# Management hub

`/uxmess gui` opens the management hub, a grid with one tile for each panel a module offers. Opening the hub
takes `uxmessentials.gui` (op by default), and each tile then shows only to a viewer who holds that tile's own
node, the same node as the command that opens the panel. A module that is switched off shows no tile.

The tiles come from the modules, not from a file: `modules/management/gui/hub.conf` sets only the hub's rows,
icons for the page arrows and filler, and the slots tiles fill. A tile added in a new release appears in an
existing hub with no edit, on a second page when the slots are full.

## Tiles

| Module | Tile | Opens | Node |
|---|---|---|---|
| communication | communication | the chat lock, clear chat, broadcast and announcer panel, as `/communication gui` | `uxmessentials.communication.gui` |
| custommenus | custommenus | the custom menu editor | `uxmessentials.menu.editor` |
| economy | economy | your `/wallet` | `uxmessentials.economy.wallet` |
| guard | guard | the region menu, as `/guard menu` | `uxmessentials.guard.menu` |
| holograms | holograms | the hologram list | `uxmessentials.holograms.gui` |
| homes | homes | your home grid, as `/home` | `uxmessentials.home.use` |
| invrollback | invrollback | a player picker, then that player's snapshots, as `/invrestore <player>` | `uxmessentials.invrollback.restore` |
| itemworld | itemworld | the item tools, as `/itemworld gui` | `uxmessentials.itemworld.gui` |
| kits | kits | the kits you can claim | `uxmessentials.kit.use` |
| messaging | messaging-settings, messaging-mailbox | your message settings, and your mailbox | `uxmessentials.messaging.gui` |
| moderation | moderation | the active punishments list | `uxmessentials.moderation.gui` |
| npc | npc | the NPC list | `uxmessentials.npc.gui` |
| playerstate | playerstate-invsee | a player picker, then that player's inventory, as `/invsee <player>` | `uxmessentials.invsee.use` |
| playerstate | playerstate-endersee | a player picker, then that player's ender chest, as `/endersee <player>` | `uxmessentials.endersee.use` |
| playerstate | playerstate-playtime | your own playtime panel, as `/playtime` | `uxmessentials.playtime.use` |
| playerwarps | playerwarps | the player warp list | `uxmessentials.pwarp.gui` |
| poses | poses | your pose status and whether others may sit on you, as `/poses` | `uxmessentials.poses.gui` |
| presence | presence | your AFK and vanish settings | `uxmessentials.presence.gui` |
| ranks | ranks | the rank ladder panel, while `gui.enabled` is on | `uxmessentials.ranks.gui` |
| scoreboard | scoreboard | your scoreboard settings | `uxmessentials.scoreboard.gui` |
| staff | staff | the online staff list, as `/stafflist` | `uxmessentials.staff.list` |
| survival | survival | your survival mechanic toggles, as `/survival` | `uxmessentials.survival.gui` |
| teleport | teleport | your teleport request settings | `uxmessentials.teleport.gui` |
| trade | trade | a player picker, then a trade request to that player, as `/trade <player>` | `uxmessentials.trade.use` |
| vaults | vaults | your vault selector | `uxmessentials.vault.use` |
| villagers | villagers | the trade manager of the villager in reach, as `/villager manager`, while `trade-manager.enabled` is on | `uxmessentials.villagers.manager` |
| vote | vote | the vote sites, while `gui.list-display` is `gui` | `uxmessentials.vote.use` |
| warps | warps | the warps you can use | `uxmessentials.warp.use` |
| worlds | worlds | the world list, as `/world gui` | `uxmessentials.world.gui` |

A tile that picks a player first acts on the pick exactly as its command acts on a name: the name button in the
picker takes a player who is offline, the invsee and endersee tiles open somebody else only for a viewer who also
holds `uxmessentials.invsee.others` or `uxmessentials.endersee.others` (or `uxmessentials.playerstate.others`), and
a trade request to a player on another server goes out as a cross-server request. The villagers tile opens the
villager you look at, or the nearest one within reach, and otherwise says there is none.

## Modules without a tile

These modules have no panel of their own, so the hub has nothing to open for them.

| Module | Why there is no tile |
|---|---|
| bossbar, nametags, tablist | What they show is set in their config files; `/bossbars` sends a one-off bar. |
| commandcontrol, customcommands | Their rules are config files, and `/customcmd` creates and tests custom commands in chat. A custom command can open a custom menu, which the custommenus tile edits. |
| migration | It runs `/uxmess import` once, from the console or chat. |
| security | Its PIN keypad is the login check, shown to the player it verifies (a Bedrock player types the PIN into a prompt instead, and that prompt shows the digits as they are typed, because neither a Bedrock form nor the anvil, sign or chat fallback can hide them); it is not a panel to open on purpose. `/pin` and `/2fa` manage your own factors. |
| servertweaks | Its tweaks are config switches. |
| skin | It is driven by `/skin`. |
| vanish | Your vanish switch is on the presence tile. |
