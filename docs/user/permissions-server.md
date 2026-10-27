# Permissions: staff, server and item modules

Every node the plugin checks, by module, with the default it gets when no permission plugin grants it: `true` for every player, `op` for operators, `false` for nobody. A node with `<n>` takes a number; the comment says whether the highest or the lowest granted number wins. The gameplay modules are in [permissions-gameplay.md](permissions-gameplay.md).

Contents: [bossbar](#bossbar) · [commandcontrol](#commandcontrol) · [communication](#communication) · [customcommands](#customcommands) · [custommenus](#custommenus) · [holograms](#holograms) · [invrollback](#invrollback) · [itemworld](#itemworld) · [messaging](#messaging) · [moderation](#moderation) · [nametags](#nametags) · [npc](#npc) · [presence](#presence) · [ranks](#ranks) · [regions](#regions) · [scoreboard](#scoreboard) · [security](#security) · [servertweaks](#servertweaks) · [skin](#skin) · [staff](#staff) · [tablist](#tablist) · [vanish](#vanish) · [vote](#vote)

## bossbar

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.bossbar` | Hot-reload / inspect the bossbar module (bars that stay up, timed bars and announced bars). | op |
| `uxmessentials.bossbar.use` | /bossbars, /bossbars toggle\|on\|off to hide or show your own boss bars. | true |
| `uxmessentials.bossbar.others` | /bossbars toggle\|on\|off <player> to hide or show another player's boss bars. | op |
| `uxmessentials.bossbar.announce` | /bossbars announce <bar> <time> for everybody and /bossbars send <player> <bar> <time> for one player. | op |

## commandcontrol

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.commandcontrol.bypass` | Exempt from the command whitelist/blacklist gate and the tab-completion / plugin-hide scrub. | op |
| `uxmessentials.commandcontrol.channelhide.bypass` | Exempt from the plugin-channel hider - the full channel-registration list is sent to this player. | op |
| `uxmessentials.commandcontrol.spam.bypass` | Exempt from the command-spam rate limiter - commands are never counted and no spam action fires. | op |
| `uxmessentials.commandcontrol.viewplugins` | See the plugin-listing / help commands (/plugins, /pl, /help, ...) hidden by the plugin-hide feature. | op |
| `uxmessentials.module.commandcontrol` | Hot-reload / inspect the commandcontrol module (command whitelist, tab-completion filter, plugin-hide). | op |

## communication

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.announce.admin` | /announce reload\|list\|preview\|toggle to manage the rotating announcer. | op |
| `uxmessentials.announce.admin.editor` | /announce editor to author announcements in the GUI. | true |
| `uxmessentials.announce.admin.reload` | /announce reload to re-read announcer.conf and re-arm the rotation. | true |
| `uxmessentials.announce.admin.list` | /announce list to read the rotation and the channels each announcement pushes to. | true |
| `uxmessentials.announce.admin.preview` | /announce preview <id> to show one announcement to yourself alone. | true |
| `uxmessentials.announce.admin.toggle` | /announce toggle to stop or resume receiving the rotating announcements. | true |
| `uxmessentials.communication.broadcast` | /broadcast to send a one-off announcement to all online players. | op |
| `uxmessentials.communication.broadcasttoggle` | /broadcasttoggle to stop or resume receiving the rotating server announcements. | true |
| `uxmessentials.communication.broadcastworld` | /broadcastworld (alias /bcw) to send a one-off announcement only to players in your world. | op |
| `uxmessentials.communication.chat.bypass` | Keep chatting while public chat is locked by /togglechat. | op |
| `uxmessentials.communication.chat.format` | Use MiniMessage formatting in your own public chat messages (when allow-player-format is on). | false |
| `uxmessentials.communication.clearchat` | /clearchat (alias /chatclear) to flush the chat for online players. | op |
| `uxmessentials.communication.clearchat.exempt` | Keep your chat scrollback when staff run /clearchat. | false |
| `uxmessentials.communication.gui` | Open the communication admin panel (/communication gui and on the /uxmess gui hub). | op |
| `uxmessentials.communication.info.<page>` | Read one information page, such as /motd or /rules. | true |
| `uxmessentials.communication.info.info` | /info to read the shipped welcome / quick-start info page. | true |
| `uxmessentials.communication.info.motd` | /motd to read the shipped message-of-the-day info page. | true |
| `uxmessentials.communication.info.rules` | /rules to read the shipped server-rules info page. | true |
| `uxmessentials.communication.me` | /me to broadcast a third-person action message to all online players. | true |
| `uxmessentials.communication.togglechat` | /togglechat (alias /mutechat) to lock or unlock public chat for non-staff. | op |
| `uxmessentials.module.communication` | Hot-reload / inspect the communication module (connection messages, announcer, info pages). | op |

## customcommands

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.customcommands` | Hot-reload / inspect the customcommands module (operator-defined commands behind /customcmd). | op |
| `uxmessentials.customcommand.admin` | /customcmd to list, inspect, reload, test and run operator-defined commands. | op |
| `uxmessentials.customcommand.run.others` | /customcmd run <id> <player>: run an operator-defined command for somebody else. | op |
| `uxmessentials.customcommand.cooldown.bypass` | Run an operator-defined command without waiting out its cooldown. | op |
| `uxmessentials.customcommand.cost.bypass` | Run an operator-defined command that costs money without paying for it. | op |

## custommenus

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.menu.admin` | /menu reload to re-read the menus/ folder from disk. | op |
| `uxmessentials.menu.editor` | /menu editor (and the /uxmess gui hub entry) to create, duplicate, rename and delete custom menus in-game. | op |
| `uxmessentials.menu.open.others` | /menu open <menu> <player>: open a custom menu for somebody else. | op |
| `uxmessentials.menu.use` | /menu open <name> to open an operator custom menu, and /menu list to see the loaded menus. | true |
| `uxmessentials.module.custommenus` | Hot-reload / inspect the custommenus module (operator custom menus behind /menu). | op |

## holograms

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.hologram.use` | /hologram to create, edit, move, list and delete native-Display holograms. | op |
| `uxmessentials.hologram.create` | /hologram create and copy. Held by default alongside the base node; negate it to leave an operator editing only the holograms that already exist. | true |
| `uxmessentials.hologram.delete` | /hologram delete. Held by default alongside the base node; the capability most worth negating for build staff. | true |
| `uxmessentials.hologram.move` | /hologram movehere, moveto, center, teleport and rotate: change where a hologram sits. | true |
| `uxmessentials.hologram.appearance` | /hologram billboard, background, glow, opacity, shadow, linewidth, viewrange, alignment, seethrough, growup, item, block, head and entity: change how a hologram looks. | true |
| `uxmessentials.hologram.visibility` | /hologram visibility, visibilitydistance, show, hide, blacklist and unblacklist: change who sees a hologram. | true |
| `uxmessentials.hologram.action` | /hologram action and clickcommand: change what a hologram runs when clicked. | true |
| `uxmessentials.hologram.view` | /hologram list, info and nearby: read-only inspection. | true |
| `uxmessentials.hologram.edit` | The line and page content of a hologram (addline, setline, insertline, removeline, page, leaderboard, linknpc, unlinknpc). | true |
| `uxmessentials.holograms.gui` | /hologram (no args) opens the holograms management GUI. | op |
| `uxmessentials.module.holograms` | Hot-reload / inspect the holograms module (native-Display holograms behind /hologram). | op |

## invrollback

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.invrollback.export` | /invrestore export <player> <index> packs a snapshot into shulker boxes and gives them to you. | op |
| `uxmessentials.invrollback.restore` | /invrestore <player> opens the inventory-snapshot restore GUI for a player. | op |
| `uxmessentials.invrollback.teleport` | /invrestore tp <player> <index> teleports you to where the snapshot was captured. | op |
| `uxmessentials.module.invrollback` | Hot-reload / inspect the invrollback module (inventory snapshots and restore). | op |

## itemworld

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.book.use` | /book: unlock a written book for editing. | op |
| `uxmessentials.condense.use` | /condense (alias /compact) [all]: recipe-stack inventory items. | op |
| `uxmessentials.disenchant.use` | /disenchant [all\|<enchant>]: remove all or one enchantment from the held item. | op |
| `uxmessentials.editsign.use` | /editsign: edit the sign you are looking at (respects build access). | op |
| `uxmessentials.enchant.use` | /enchant <enchant> [level]: enchant the held item (level clamped at the boundary). | op |
| `uxmessentials.firework.use` | /firework <color\|clear\|power>: style or power the held firework rocket. | op |
| `uxmessentials.give.use` | /give <player> <item> [amount] (alias /i): give an item to a player; bulk gives are audited. | op |
| `uxmessentials.giveall.use` | /giveall <item> [amount]: give an item to every online player; bulk gives are audited per recipient. | op |
| `uxmessentials.hat.use` | /hat: wear the held item as a helmet (itemworld-owned; playerstate defers it). | op |
| `uxmessentials.item.use` | /item <item> [amount]: give an item to yourself. | op |
| `uxmessentials.itemamount.use` | /itemamount <amount> (/amount): set the held stack amount, clamped to the give cap. | op |
| `uxmessentials.itemdamage.use` | /itemdamage <damage> (/durability): set the held item's durability damage. | op |
| `uxmessentials.itemdb.use` | /itemdb [item]: look up an item's id / data. | op |
| `uxmessentials.itemflag.use` | /itemflag <flag> <on\|off>: toggle an item meta flag. | op |
| `uxmessentials.iteminfo.use` | /iteminfo: inspect the metadata of the item in your hand. | op |
| `uxmessentials.itemlore.use` | /itemlore <set\|add\|clear> [text]: edit the held item's lore. | op |
| `uxmessentials.itemmodel.use` | /itemmodel <id\|clear> (alias /custommodeldata): set or clear the held item's custom model data. | op |
| `uxmessentials.itemname.use` | /itemname <name>: rename the held item. | op |
| `uxmessentials.itemworld.enchant.<enchantment>` | Apply one specific enchantment through /enchant, when per-enchantment gating is switched on. | op |
| `uxmessentials.itemworld.give.<item>` | Give one specific item through /give, when per-item gating is switched on. | op |
| `uxmessentials.itemworld.gui` | Open the itemworld utilities hub (/itemworld gui and on the /uxmess gui hub). | op |
| `uxmessentials.itemworld.itemedit` | /itemedit <rename\|resetname\|lore\|enchant\|unenchant\|flag\|attribute\|durability\|repair\|unbreakable\|custommodeldata>: edit the held item's name, lore and meta (item-edit.enabled). | op |
| `uxmessentials.itemworld.itemedit.rename` | /itemedit rename and resetname. Defaults on for anyone who already holds the editor node; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.lore` | /itemedit lore add\|set\|insert\|remove\|clear. Defaults on for an editor-node holder; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.enchant` | /itemedit enchant and unenchant. Defaults on for an editor-node holder; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.flag` | /itemedit flag: toggle an item flag such as HIDE_ENCHANTS. Defaults on for an editor-node holder. | true |
| `uxmessentials.itemworld.itemedit.attribute` | /itemedit attribute add\|remove: edit attribute modifiers. Defaults on for an editor-node holder; the verb most worth withholding on a survival server. | true |
| `uxmessentials.itemworld.itemedit.durability` | /itemedit durability and repair. Defaults on for an editor-node holder; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.unbreakable` | /itemedit unbreakable: mark the held item unbreakable. Defaults on for an editor-node holder. | true |
| `uxmessentials.itemworld.itemedit.model` | /itemedit custommodeldata: set or clear the resource-pack model selector. Defaults on for an editor-node holder. | true |
| `uxmessentials.itemworld.itemedit.gui` | A bare /itemedit, which opens the click-driven editor. Defaults on for an editor-node holder; negate it to leave only the typed verbs. | true |
| `uxmessentials.itemworld.shulker` | Right-click a shulker box in the inventory to open its contents in place (shulkers.enabled). | op |
| `uxmessentials.itemworld.spawnmob.<mob>` | Spawn one specific mob through /spawnmob, when per-mob gating is switched on. | op |
| `uxmessentials.module.itemworld` | Hot-reload / inspect the itemworld module and its sub-feature groups. | op |
| `uxmessentials.more.use` | /more: fill the held stack to max (itemworld-owned; playerstate defers it). | op |
| `uxmessentials.potion.use` | /potion <effect> [duration] [amplifier]: add a potion effect to the held potion. | op |
| `uxmessentials.recipe.use` | /recipe [item]: show an item's crafting recipe. | op |
| `uxmessentials.repair.itemworld` | /repair and /repairall in the itemworld surface (itemworld-owned; playerstate defers them). | op |
| `uxmessentials.showitem.use` | /showitem: broadcast the held item to chat for everyone online. | true |
| `uxmessentials.skull.use` | /skull [player]: get a player-head skull. | op |
| `uxmessentials.unbreakable.use` | /unbreakable [true\|false]: toggle or set the held item's unbreakable flag. | op |
| `uxmessentials.unlimited.use` | /unlimited: toggle unlimited placement of held blocks. | op |
| `uxmessentials.butcher.use` | /butcher [radius]: purge nearby mobs (audit-logged). | op |
| `uxmessentials.entitycount.use` | /entitycount [radius]: tally nearby entities by type for lag diagnosis. | op |
| `uxmessentials.kill.use` | /kill [player\|entity]: kill a target (audit-logged). | op |
| `uxmessentials.killall.use` | /killall [type]: purge entities world-wide (audit-logged). | op |
| `uxmessentials.kittycannon.use` | /kittycannon: launch an exploding cat (audit-logged). | op |
| `uxmessentials.remove.use` | /remove <type> [radius]: remove entities by type (audit-logged). | op |
| `uxmessentials.spawner.use` | /spawner <type>: set a spawner's mob type (audit-logged). | op |
| `uxmessentials.spawnmob.use` | /spawnmob <type> [amount]: spawn mobs (audit-logged). | op |
| `uxmessentials.copyinv.use` | /copyinv <player>: copy a player's inventory into yours. | op |
| `uxmessentials.disposal.use` | /disposal (alias /trash): open a throwaway GUI. | op |
| `uxmessentials.enderclear.use` | /enderclear (alias /clearec) [player]: clear an ender chest. | op |
| `uxmessentials.endercopy.use` | /endercopy <player>: copy a player's ender chest into yours. | op |
| `uxmessentials.workstation.anvil` | /anvil: open a virtual anvil. | op |
| `uxmessentials.workstation.cartography` | /cartography: open a virtual cartography table. | op |
| `uxmessentials.workstation.enderchest` | /enderchest (alias /echest): open your ender chest. | op |
| `uxmessentials.workstation.furnace` | /furnace: open a virtual furnace. | op |
| `uxmessentials.workstation.grindstone` | /grindstone: open a virtual grindstone. | op |
| `uxmessentials.workstation.loom` | /loom: open a virtual loom. | op |
| `uxmessentials.workstation.others` | Open any virtual workstation on another player with the [player] target form. | op |
| `uxmessentials.workstation.smithingtable` | /smithingtable: open a virtual smithing table. | op |
| `uxmessentials.workstation.stonecutter` | /stonecutter: open a virtual stonecutter. | op |
| `uxmessentials.workstation.workbench` | /workbench (alias /craft): open a virtual crafting table. | op |
| `uxmessentials.antioch.use` | /antioch (alias /grenade): throw a primed TNT grenade (audit-logged). | op |
| `uxmessentials.beezooka.use` | /beezooka (alias /beecannon): launch an angry bee (audit-logged). | op |
| `uxmessentials.break.use` | /break: instantly break the block you are looking at (audit-logged). | op |
| `uxmessentials.fireball.use` | /fireball: launch a fireball (audit-logged). | op |
| `uxmessentials.lightning.use` | /lightning (alias /smite) [player]: strike lightning (audit-logged). | op |
| `uxmessentials.nuke.use` | /nuke [player]: rain lightning over an area (audit-logged). | op |
| `uxmessentials.tree.use` | /tree <type>: generate a tree of the given type where you are looking (audit-logged). | op |
| `uxmessentials.powertool.toggle` | /powertooltoggle: enable/disable your powertool bindings. | op |
| `uxmessentials.powertool.use` | /powertool <command> (alias /pt): bind a command to the held item. | op |
| `uxmessentials.time.alias` | /day / /night quick time aliases. | op |
| `uxmessentials.time.use` | /time <set\|add> <value>: per-world time. | op |
| `uxmessentials.weather.alias` | /sun / /rain / /thunder quick weather aliases. | op |
| `uxmessentials.weather.use` | /weather <clear\|rain\|thunder> [duration]. | op |

## messaging

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.helpop.receive` | Receive /helpop requests (staff side). | op |
| `uxmessentials.helpop.use` | /helpop <text> to open a player-to-staff support request. | true |
| `uxmessentials.mail.sendall` | /mail sendall <text> to broadcast mail to every online player (staff). | op |
| `uxmessentials.mail.use` | /mail read / /mail send <player> <text> / /mail clear (/mailclear): persistent offline mail. | true |
| `uxmessentials.messaging.gui` | See and open the messaging settings panel and mailbox on the /uxmess gui management hub. | op |
| `uxmessentials.module.messaging` | Hot-reload / inspect the messaging module (private messages, mail and social spy). | op |
| `uxmessentials.msg.color` | Render MiniMessage tags in PM/mail bodies (default plain text). | op |
| `uxmessentials.msg.ignore` | /ignore <player> / /unignore <player> / /ignorelist to manage and view your own ignore list. | true |
| `uxmessentials.msg.reply` | /reply <text> to answer your last conversation (reply-TTL bounded). | true |
| `uxmessentials.msg.socialspy` | /socialspy to observe other players' private messages (staff). | op |
| `uxmessentials.msg.toggle` | /msgtoggle to refuse incoming /msg / /reply; /rtoggle to refuse only incoming /reply routing (mail still delivers). | true |
| `uxmessentials.msg.use` | /msg <player> <text> to send a private message. | true |
| `uxmessentials.msgsettings.use` | /msgsettings opens your personal messaging settings panel (accept-messages, social spy). | true |

## moderation

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.moderation.<sanction>.maxduration.<seconds>` | The longest ban, mute or jail you may hand out, in seconds; the largest tier held wins. | op |
| `uxmessentials.moderation.ban` | /ban <player> [reason] and /unban <player>: permanent UUID ban and its lift; /banhistory <player> reviews a player's full ban/unban history. | op |
| `uxmessentials.moderation.banip` | /banip <player\|ip> [reason] / /unbanip <ip>: IP ban with stored-IP alt detection. | op |
| `uxmessentials.moderation.banlist` | /banlist to review the players currently banned. | op |
| `uxmessentials.moderation.freeze` | /freeze <player> / /unfreeze <player>: pin a player in place pending review. | op |
| `uxmessentials.moderation.jail` | /jail <player> <jail> [duration] [reason]; /jails lists configured jails; /jailedplayers lists who is jailed; /setjail <name> defines a jail at your location; /jail del <name> removes a defined jail. | op |
| `uxmessentials.moderation.kick` | /kick <player> [reason]; /kickall [reason] to clear non-exempt players. | op |
| `uxmessentials.moderation.lockdown` | /lockdown [on\|off]: refuse every login except holders of the lockdown bypass; the flag survives restart. | op |
| `uxmessentials.moderation.lockdown.bypass` | Join the server while it is locked down (/lockdown). Held by staff who must stay reachable during a lockdown. | op |
| `uxmessentials.moderation.mute` | /mute <player> [duration] [reason] (/tempmute is the explicit duration alias); /mutehistory <player> reviews a player's full mute/unmute history. | op |
| `uxmessentials.moderation.mutelist` | /mutelist to review the players currently muted. | op |
| `uxmessentials.moderation.sanction` | /sanction <player>: aggregated read-only punishment summary: current mute, jail, ban state and active warning count. | op |
| `uxmessentials.moderation.sudo` | /sudo <player> <command>: run a command as another player. | op |
| `uxmessentials.moderation.tempban` | /tempban <player> <duration> [reason]. | op |
| `uxmessentials.moderation.templates` | /punish <player> <template>: apply a configured punishment template (a preset reason + optional duration) as a ban or tempban. | op |
| `uxmessentials.moderation.togglejail` | /togglejail <player> [jail] [reason]: release the target if jailed, otherwise jail them in the named jail (or the first configured jail). | op |
| `uxmessentials.moderation.unjail` | /unjail <player>. | op |
| `uxmessentials.moderation.unmute` | /unmute <player>. | op |
| `uxmessentials.moderation.warn` | /warn <player> [reason], /tempwarn <player> <duration> [reason], /warns <player> and /unwarn <player>: issue (standing or timed), review and clear warning history. | op |
| `uxmessentials.moderation.broadcast.receive` | Receive the staff sanction broadcast: the one-line announcement a non-silent /ban /mute /kick /warn emits. The -s flag (or broadcast.silent-by-default) suppresses it. Duration tiers ride numbered nodes: uxmessentials.moderation.ban.maxduration.<seconds> and uxmessentials.moderation.mute.maxduration.<seconds> cap how long a ban/mute that holder may issue (highest held wins; no node = unlimited). | op |
| `uxmessentials.moderation.check` | /checkban <player> and /checkmute <player>: report whether a player is currently banned or muted. | op |
| `uxmessentials.moderation.commandspy` | /commandspy (/cspy) to watch the commands other players run. | op |
| `uxmessentials.moderation.exempt` | Cannot be muted/jailed/tempbanned/kicked/warned/IP-banned/frozen by lower staff. | op |
| `uxmessentials.moderation.gui` | /mod opens the moderation management GUI (active punishments + per-player history). | op |
| `uxmessentials.moderation.history` | /history <player>: review a player's full disciplinary record (ban/mute/warn/kick) newest-first. | op |
| `uxmessentials.moderation.seen` | /seen, /seenip and /alts <player>: last-seen / last-IP lookup, surfaces alts. | op |
| `uxmessentials.moderation.staffhistory` | /staffhistory <staff>: review the sanctions a staff member has issued, newest-first. | op |
| `uxmessentials.moderation.staffrollback` | /staffrollback <staff> [limit]: revoke a staff member's still-active sanctions (un-ban/un-mute/clear-warns the targets they sanctioned). | op |
| `uxmessentials.moderation.stats` | /modstats [staff] [days]: staff punishment analytics: a most-active-staff leaderboard or a single staff member's breakdown, optionally over the last N days. | op |
| `uxmessentials.module.moderation` | Hot-reload / inspect the moderation module (bans, mutes, jails, warnings and history). | op |

## nametags

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.nametags` | Hot-reload / inspect the nametags module (the name shown above a player). | op |

## npc

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.npc` | Hot-reload / inspect the npc module (server-wide fake-player NPCs behind /npc). | op |
| `uxmessentials.npc.admin` | /npc to create, delete, list, move, re-skin, and bind the click command of fake-player NPCs. | op |
| `uxmessentials.npc.create` | /npc create and /npc copy. Held by default alongside the admin node; negate it to leave an operator editing only the NPCs that already exist. | true |
| `uxmessentials.npc.delete` | /npc delete. Held by default alongside the admin node; the capability most worth negating for build staff. | true |
| `uxmessentials.npc.move` | /npc movehere, moveto, teleport, center and fix: change where an NPC stands. | true |
| `uxmessentials.npc.appearance` | /npc skin, skinslim, type, equip, glow, pose, scale and displayname: change how an NPC looks. | true |
| `uxmessentials.npc.action` | /npc command and /npc action: change what an NPC runs when clicked. | true |
| `uxmessentials.npc.view` | /npc list, info, nearby and help: read-only inspection. | true |
| `uxmessentials.npc.edit` | Every remaining /npc setting (data, state, cooldown, mirror, collidable, showintab, view and turn distance). | true |
| `uxmessentials.npc.gui` | /npc (no args) opens the NPC management GUI. | op |
| `uxmessentials.npc.limit.<n>` | How many NPCs you may own; the largest tier held wins. | op |

## presence

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.afk.use` | /afk [reason] to toggle your AFK state (auto-AFK also applies on idle). | true |
| `uxmessentials.gc.use` | /gc to show server health: TPS, uptime, memory and loaded chunks. | op |
| `uxmessentials.list.use` | /list to see who is online. | true |
| `uxmessentials.module.presence` | Hot-reload / inspect the presence module (AFK, nicknames and vanish state). | op |
| `uxmessentials.nick.others` | /nick <player> <name> to set another player's display name. | op |
| `uxmessentials.nick.use` | /nick <name> \| off to set or clear your display name. | true |
| `uxmessentials.presence.gui` | Show the presence settings panel on the /uxmess gui hub. | op |
| `uxmessentials.presencesettings.use` | /presencesettings opens your personal presence settings panel. | true |
| `uxmessentials.realname.use` | /realname <player> to look up a player's real account name. | true |
| `uxmessentials.staff.member` | Marks a player as staff so they appear in /staff. | op |
| `uxmessentials.staff.use` | /staff to list online staff members. | op |
| `uxmessentials.whois.use` | /whois <player> to view an online player's account, identity and status. | op |

## ranks

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.ranks` | Hot-reload / inspect the ranks module (rank ladders, prestige and rank-up costs). | op |
| `uxmessentials.ranks.admin` | /ranks setrank <player> <rank> to set a player's rank directly. | op |
| `uxmessentials.ranks.gui` | /ranks to open the ladder panel (config-gated; registered only when the GUI is enabled). | true |
| `uxmessentials.ranks.prestige` | /prestige to reset to the first rank for a prestige level once you reach the top rank. | true |
| `uxmessentials.ranks.rankup` | /rankup to advance to the next rank when you meet its requirements. | true |

## regions

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.regions` | Hot-reload / inspect the regions module (WorldGuard region management). | op |
| `uxmessentials.regions.admin` | /regions priority <id> <value>: set a WorldGuard region priority. | op |
| `uxmessentials.regions.create` | /regions create <id> (and /regions pos1\|pos2): define a cuboid WorldGuard region. | op |
| `uxmessentials.regions.flags` | /regions flags <id>: open the per-region flag editor GUI. | op |
| `uxmessentials.regions.list` | /regions [world]: open the WorldGuard region-list GUI for a world. | op |
| `uxmessentials.regions.members` | /regions members <id> and /regions addmember\|addowner <id> <player>: manage a region roster. | op |

## scoreboard

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.scoreboard` | Hot-reload / inspect the scoreboard module (per-player sidebar and tablist on uxmlib-hud). | op |
| `uxmessentials.scoreboard.gui` | /scoreboard gui (and the scoreboard entry on the /uxmess gui hub) to open the per-player scoreboard settings panel: the show/hide toggle. | true |
| `uxmessentials.scoreboard.use` | /scoreboard (alias /sb) to toggle whether you see the scoreboard display. | true |

## security

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.security` | Hot-reload / inspect the security module (2FA, op-protection, IP/alt guard). | op |
| `uxmessentials.security.2fa` | /2fa: set up, confirm or disable an authenticator second factor on your own account. | true |
| `uxmessentials.security.admin` | /security: inspect and manage another player's second factors. | op |
| `uxmessentials.security.alts` | /ipalts: list the accounts that share an IP with a player. | op |
| `uxmessentials.security.alts.notify` | Receive the staff notice when a join shares an IP with other accounts or reports a flagged client. | op |
| `uxmessentials.security.bypass` | Exempt from the two-factor join verification and op-command re-auth checks. | op |
| `uxmessentials.security.clientinfo` | /clientinfo: show the client brand a player reported. | op |
| `uxmessentials.security.force` | /security force <player>: force a player to re-verify their second factor on their next action or join. | op |
| `uxmessentials.security.pin` | /pin: set, change or remove a numeric PIN second factor on your own account. | true |
| `uxmessentials.security.pin.required` | Must set a PIN before playing. | false |
| `uxmessentials.security.reset` | /security reset <player> [totp\|pin\|all]: clear a factor a player can no longer prove. | op |

## servertweaks

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.servertweaks` | Hot-reload / inspect the servertweaks module (the small server-behaviour switches). | op |

## skin

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.skin` | Hot-reload / inspect the skin module (the skin a player wears). | op |
| `uxmessentials.skin.use` | /skin <name> and /skin clear: wear another account's skin, or go back to your own. | true |
| `uxmessentials.skin.url` | /skin url <link>: wear the skin drawn in an image published on the web. | true |
| `uxmessentials.skin.file` | /skin file <name>: wear one of the skins the operator dropped in the server's skin folder. | op |
| `uxmessentials.skin.update` | /skin update: re-fetch your skin, for an account whose skin changed at the source. | true |
| `uxmessentials.skin.other` | /skin set <name> <player> and /skin clear <player>: dress or undress somebody else. | op |
| `uxmessentials.skin.drop` | /skin drop <player>: delete a stored skin, so their next join derives one afresh. | op |
| `uxmessentials.skin.info` | /skin info <player>: which skin somebody wears, from where, and when it was set. | op |
| `uxmessentials.skin.purge` | /skin purge <name>: forget a cached texture so the next lookup goes back to the source. | op |
| `uxmessentials.skin.name.<skin>` | Wear one skin listed under limits.restricted-skins; a skin nobody restricted is open to everyone. | true |

## staff

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.staff` | Hot-reload / inspect the staff module (staff mode, its loadout and staff chat). | op |
| `uxmessentials.staff.chat` | /staffchat (alias /sc) to send and receive lines on the staff-only chat channel. | op |
| `uxmessentials.staff.list` | /stafflist to open the online-staff GUI (vanish-aware) and click a head to teleport to that staff member. | op |
| `uxmessentials.staff.mode` | /staffmode [player] to flip into staff mode: your real loadout is saved and swapped for the gadget hotbar (and you vanish); leaving restores it. The EXAMINE gadget opens a player's inventory. | op |

## tablist

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.tablist` | Hot-reload / inspect the tablist module (the player list header, footer and rows). | op |

## vanish

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.vanish` | Hot-reload / inspect the vanish module. | op |
| `uxmessentials.vanish.list` | /vanish list to see the hidden players you are permitted to see (scoped to your see level). | op |
| `uxmessentials.vanish.others` | /vanish <player> to toggle another player's vanish. | op |
| `uxmessentials.vanish.persist` | Remain vanished across a relog instead of reappearing on join. | op |
| `uxmessentials.vanish.see` | See other vanished players (staff-among-staff visibility) and target them with /tp. | op |
| `uxmessentials.vanish.see.level<n>` | How deeply you see: a viewer sees a vanished player when their see level reaches that player's use level; the largest level held wins and plain .see is level 1. | op |
| `uxmessentials.vanish.use.level<n>` | How deeply you vanish: only a viewer whose see level reaches this level finds you; the largest level held wins and plain .use is level 1. | op |
| `uxmessentials.vanish.silent` | /vanish -s to vanish or reappear without the fake join/quit broadcast. | op |
| `uxmessentials.vanish.use` | /vanish to become invisible to other players; suppresses fake join/quit. | op |

## vote

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.vote` | Hot-reload / inspect the vote module (Votifier vote rewards and vote party). | op |
| `uxmessentials.vote.admin` | /vote admin givevote <player> [amount] and /vote admin reset <player>: inject or clear votes for any player. | op |
| `uxmessentials.vote.testreward` | /vote testreward to simulate a vote for yourself and verify the configured rewards. | op |
| `uxmessentials.vote.top` | /vote top [period] to see the vote leaderboard for the given period. | true |
| `uxmessentials.vote.use` | /vote to see the server's vote links. | true |
| `uxmessentials.voteparty.admin` | /voteparty force\|set\|add: force the party or adjust the party counter. | op |
| `uxmessentials.voteparty.use` | /voteparty to see progress towards the next vote party. | true |
