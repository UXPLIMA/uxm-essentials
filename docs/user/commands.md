# Commands

<!-- Generated from the code catalogues by ./gradlew :core:generateReference. Edit the catalogue, not this page. -->

Every command the plugin ships, with the aliases it answers to on a fresh install and the node that gates it. A module that is switched off registers none of its commands, and a command whose feature is switched off in the module's file (the worth list, banknotes, the bank, loans) is left out too. `commands.conf` renames a command, changes its aliases or turns it off, and [permissions-gameplay.md](permissions-gameplay.md), [permissions-server.md](permissions-server.md) and [permissions-items.md](permissions-items.md) explain every node, including the ones a subcommand checks.

`/uxmess` is the administration root, gated by `uxmessentials.admin`: `/uxmess status` and `/uxmess help` show the state of the plugin, `/uxmess doctor` checks the setup (`doctor repair` fixes what it can, `uxmessentials.admin.doctor.repair`), and `/uxmess reload` reads every file again (`uxmessentials.admin.reload`). `/info`, `/motd` and `/rules` are the info pages a fresh install ships in `modules/communication/info-pages.conf`; a page an operator adds there registers a command of its own, as does every custom command and every menu with an open command.

| Command | Aliases | Module | What it does | Permission |
|---|---|---|---|---|
| `/backup` |  | shared | Snapshot the plugin's data directory on demand. | `uxmessentials.admin.backup` |
| `/help` |  | shared | List the commands you can use. | `uxmessentials.help` |
| `/lang` |  | shared | Set or clear your personal language override. | `uxmessentials.lang.use` |
| `/uxmess` | `/uxmessentials`, `/uxe` | shared | uxmEssentials administration root. | `uxmessentials.admin` |
| `/bossbars` |  | bossbar | Hide or show your boss bars, or announce one. | `uxmessentials.bossbar.use` |
| `/announce` |  | communication | Manage the rotating server announcer. | `uxmessentials.announce.admin` |
| `/broadcast` |  | communication | Send an announcement to all online players. | `uxmessentials.communication.broadcast` |
| `/broadcasttoggle` |  | communication | Toggle whether you receive server announcements. | `uxmessentials.communication.broadcasttoggle` |
| `/broadcastworld` | `/bcw` | communication | Send an announcement to players in your world. | `uxmessentials.communication.broadcastworld` |
| `/clearchat` | `/chatclear` | communication | Flush the chat for online players. | `uxmessentials.communication.clearchat` |
| `/communication` |  | communication | Open the communication admin panel. | `uxmessentials.communication.gui` |
| `/info` |  | communication | Show the info page. | `uxmessentials.communication.info.info` |
| `/me` |  | communication | Broadcast an action message about yourself. | `uxmessentials.communication.me` |
| `/motd` |  | communication | Show the message of the day. | `uxmessentials.communication.info.motd` |
| `/rules` |  | communication | Show the rules page. | `uxmessentials.communication.info.rules` |
| `/togglechat` | `/mutechat` | communication | Lock or unlock public chat for non-staff. | `uxmessentials.communication.togglechat` |
| `/customcmd` | `/customcommand` | customcommands | Manage operator-defined custom commands. | `uxmessentials.customcommand.admin` |
| `/menu` |  | custommenus | Open, list, reload, execute, dump, save, edit, capture into, and convert operator custom menus. | `uxmessentials.menu.use` |
| `/balance` | `/bal`, `/money` | economy | Show a wallet balance. | `uxmessentials.economy.balance` |
| `/baltop` | `/balancetop` | economy | View the top balances. | `uxmessentials.economy.baltop` |
| `/bank` |  | economy | Shared bank accounts management. | `uxmessentials.economy.bank` |
| `/deposit` |  | economy | Deposit physical banknotes into virtual balance. | `uxmessentials.economy.deposit` |
| `/eco` | `/economy` | economy | Eco-admin balance mutations. | `uxmessentials.economy.admin` |
| `/exchange` |  | economy | Exchange currencies or open the conversion GUI. | `uxmessentials.economy.exchange` |
| `/loan` |  | economy | Take out a loan, repay it and see your credit rating. | `uxmessentials.economy.loan` |
| `/pay` |  | economy | Transfer funds to another player. | `uxmessentials.economy.pay` |
| `/payall` |  | economy | Pay every online player at once. | `uxmessentials.economy.payall` |
| `/payconfirm` |  | economy | Confirm a large pending pay. | `uxmessentials.economy.pay` |
| `/paytoggle` |  | economy | Toggle accepting incoming pay. | `uxmessentials.economy.pay.toggle` |
| `/sell` |  | economy | Sell held items at their configured worth. | `uxmessentials.economy.sell` |
| `/sellall` |  | economy | Sell every sellable item in your inventory. | `uxmessentials.economy.sell` |
| `/setworth` |  | economy | Set an item's sell worth. | `uxmessentials.economy.setworth` |
| `/wallet` |  | economy | Open your visual wallet dashboard. | `uxmessentials.economy.wallet` |
| `/withdraw` |  | economy | Withdraw virtual money into a physical banknote item. | `uxmessentials.economy.withdraw` |
| `/worth` |  | economy | Report a held item's configured sell value. | `uxmessentials.economy.worth` |
| `/guard` | `/bolge` | guard | Protect areas with regions: rules, roles, behaviours and history. | `uxmessentials.guard.use` |
| `/hologram` |  | holograms | Create and manage native-Display holograms. | `uxmessentials.hologram.use` |
| `/home` | `/h`, `/homes` | homes | Open and manage your homes. | `uxmessentials.home.use` |
| `/invrestore` |  | invrollback | Open the inventory-snapshot restore GUI for a player, or export or teleport to a chosen snapshot. | `uxmessentials.invrollback.restore` |
| `/antioch` | `/grenade` | itemworld | Throw a primed TNT grenade. | `uxmessentials.antioch.use` |
| `/anvil` |  | itemworld | Open a virtual anvil. | `uxmessentials.workstation.anvil` |
| `/beezooka` | `/beecannon` | itemworld | Launch an angry bee. | `uxmessentials.beezooka.use` |
| `/bigtree` | `/largetree` | itemworld | Generate a large tree where you are looking. | `uxmessentials.tree.use` |
| `/book` |  | itemworld | Unlock a written book for editing. | `uxmessentials.book.use` |
| `/break` |  | itemworld | Break the block you are looking at. | `uxmessentials.break.use` |
| `/butcher` |  | itemworld | Purge nearby mobs. | `uxmessentials.butcher.use` |
| `/cartography` |  | itemworld | Open a virtual cartography table. | `uxmessentials.workstation.cartography` |
| `/condense` | `/compact` | itemworld | Recipe-stack inventory items. | `uxmessentials.condense.use` |
| `/copyinv` |  | itemworld | Copy a player's inventory into yours. | `uxmessentials.copyinv.use` |
| `/day` |  | itemworld | Set the time to day. | `uxmessentials.time.alias` |
| `/disenchant` |  | itemworld | Remove enchantments from the held item. | `uxmessentials.disenchant.use` |
| `/disposal` | `/trash` | itemworld | Open a throwaway window. | `uxmessentials.disposal.use` |
| `/editsign` |  | itemworld | Edit the sign you are looking at. | `uxmessentials.editsign.use` |
| `/enchant` |  | itemworld | Enchant the held item. | `uxmessentials.enchant.use` |
| `/enderchest` | `/echest` | itemworld | Open a virtual ender chest. | `uxmessentials.workstation.enderchest` |
| `/enderclear` | `/clearec` | itemworld | Clear an ender chest. | `uxmessentials.enderclear.use` |
| `/endercopy` |  | itemworld | Copy a player's ender chest into yours. | `uxmessentials.endercopy.use` |
| `/entitycount` |  | itemworld | Count nearby entities by type. | `uxmessentials.entitycount.use` |
| `/fireball` |  | itemworld | Launch a fireball. | `uxmessentials.fireball.use` |
| `/firework` |  | itemworld | Style or power the held firework rocket. | `uxmessentials.firework.use` |
| `/furnace` |  | itemworld | Open a virtual furnace. | `uxmessentials.workstation.furnace` |
| `/give` | `/i` | itemworld | Give an item to a player. | `uxmessentials.give.use` |
| `/giveall` |  | itemworld | Give an item to every online player. | `uxmessentials.giveall.use` |
| `/grindstone` |  | itemworld | Open a virtual grindstone. | `uxmessentials.workstation.grindstone` |
| `/hat` |  | itemworld | Wear the held item as a hat. | `uxmessentials.hat.use` |
| `/item` |  | itemworld | Give yourself an item. | `uxmessentials.item.use` |
| `/itemamount` | `/amount` | itemworld | Set the held stack's amount. | `uxmessentials.itemamount.use` |
| `/itemdamage` | `/durability` | itemworld | Set the held item's durability damage. | `uxmessentials.itemdamage.use` |
| `/itemdb` | `/idb` | itemworld | Look up an item's id. | `uxmessentials.itemdb.use` |
| `/itemedit` |  | itemworld | Edit the held item's name, lore and meta. | `uxmessentials.itemworld.itemedit` |
| `/itemflag` | `/iflag` | itemworld | Toggle an item meta flag. | `uxmessentials.itemflag.use` |
| `/iteminfo` |  | itemworld | Inspect the item you are holding. | `uxmessentials.iteminfo.use` |
| `/itemlore` | `/lore` | itemworld | Edit held-item lore. | `uxmessentials.itemlore.use` |
| `/itemmodel` | `/custommodeldata` | itemworld | Set the held item's custom model data. | `uxmessentials.itemmodel.use` |
| `/itemname` | `/iname` | itemworld | Rename the held item. | `uxmessentials.itemname.use` |
| `/itemworld` |  | itemworld | Open the itemworld utilities hub. | `uxmessentials.itemworld.gui` |
| `/kill` |  | itemworld | Kill a target. | `uxmessentials.kill.use` |
| `/killall` |  | itemworld | Purge entities world-wide. | `uxmessentials.killall.use` |
| `/kittycannon` |  | itemworld | Launch an exploding cat. | `uxmessentials.kittycannon.use` |
| `/lightning` | `/smite` | itemworld | Strike lightning. | `uxmessentials.lightning.use` |
| `/loom` |  | itemworld | Open a virtual loom. | `uxmessentials.workstation.loom` |
| `/more` |  | itemworld | Fill the held stack to max. | `uxmessentials.more.use` |
| `/night` |  | itemworld | Set the time to night. | `uxmessentials.time.alias` |
| `/nuke` |  | itemworld | Rain lightning over an area. | `uxmessentials.nuke.use` |
| `/potion` |  | itemworld | Add a potion effect to the held potion. | `uxmessentials.potion.use` |
| `/powertool` | `/pt` | itemworld | Bind a command to the held item. | `uxmessentials.powertool.use` |
| `/powertoollist` |  | itemworld | List the held item's powertool bindings. | `uxmessentials.powertool.use` |
| `/powertooltoggle` |  | itemworld | Toggle your powertool bindings. | `uxmessentials.powertool.toggle` |
| `/rain` |  | itemworld | Start rain. | `uxmessentials.weather.alias` |
| `/recipe` |  | itemworld | Show an item's crafting recipe. | `uxmessentials.recipe.use` |
| `/remove` |  | itemworld | Remove entities by type. | `uxmessentials.remove.use` |
| `/repair` |  | itemworld | Repair the held item. | `uxmessentials.repair.itemworld` |
| `/repairall` |  | itemworld | Repair the whole inventory. | `uxmessentials.repair.itemworld` |
| `/showitem` |  | itemworld | Broadcast the held item to chat. | `uxmessentials.showitem.use` |
| `/skull` |  | itemworld | Get a player-head skull. | `uxmessentials.skull.use` |
| `/smithingtable` |  | itemworld | Open a virtual smithing table. | `uxmessentials.workstation.smithingtable` |
| `/spawner` |  | itemworld | Set a spawner's mob type. | `uxmessentials.spawner.use` |
| `/spawnmob` |  | itemworld | Spawn mobs. | `uxmessentials.spawnmob.use` |
| `/stonecutter` |  | itemworld | Open a virtual stonecutter. | `uxmessentials.workstation.stonecutter` |
| `/sun` |  | itemworld | Clear the weather. | `uxmessentials.weather.alias` |
| `/thunder` |  | itemworld | Start a thunderstorm. | `uxmessentials.weather.alias` |
| `/time` |  | itemworld | Set or add world time. | `uxmessentials.time.use` |
| `/tree` |  | itemworld | Generate a tree where you are looking. | `uxmessentials.tree.use` |
| `/unbreakable` |  | itemworld | Toggle the held item's unbreakable flag. | `uxmessentials.unbreakable.use` |
| `/unlimited` |  | itemworld | Toggle unlimited block placement. | `uxmessentials.unlimited.use` |
| `/weather` |  | itemworld | Set the weather. | `uxmessentials.weather.use` |
| `/workbench` | `/craft` | itemworld | Open a virtual crafting table. | `uxmessentials.workstation.workbench` |
| `/kit` | `/k`, `/kits` | kits | Claim a kit; list, preview, create, remove, edit or reset kits. | `uxmessentials.kit.use` |
| `/helpop` |  | messaging | Open a staff support request. | `uxmessentials.helpop.use` |
| `/ignore` |  | messaging | Ignore a player's messages, or open your ignore list. | `uxmessentials.msg.ignore` |
| `/ignorelist` |  | messaging | Open your ignore list. | `uxmessentials.msg.ignore` |
| `/mail` |  | messaging | Open your mailbox, or read, send or clear your persistent mail. | `uxmessentials.mail.use` |
| `/mailclear` |  | messaging | Clear your mailbox. | `uxmessentials.mail.use` |
| `/msg` | `/m`, `/tell`, `/whisper`, `/w` | messaging | Send a private message to a player. | `uxmessentials.msg.use` |
| `/msgsettings` |  | messaging | Open your personal messaging settings panel. | `uxmessentials.msgsettings.use` |
| `/msgtoggle` |  | messaging | Refuse or accept incoming private messages. | `uxmessentials.msg.toggle` |
| `/reply` | `/r` | messaging | Reply to your last private conversation. | `uxmessentials.msg.reply` |
| `/rtoggle` |  | messaging | Toggle whether you receive replies. | `uxmessentials.msg.toggle` |
| `/socialspy` |  | messaging | Observe other players' private messages, globally or for one player. | `uxmessentials.msg.socialspy` |
| `/unignore` |  | messaging | Stop ignoring a player's messages. | `uxmessentials.msg.ignore` |
| `/alts` |  | moderation | List accounts sharing a player's IP. | `uxmessentials.moderation.seen` |
| `/ban` |  | moderation | Permanently ban a player (prefix the reason with -s to ban silently). | `uxmessentials.moderation.ban` |
| `/banhistory` |  | moderation | Review a player's ban history. | `uxmessentials.moderation.ban` |
| `/banip` |  | moderation | Ban a player or IP by address. | `uxmessentials.moderation.banip` |
| `/banlist` |  | moderation | Review currently banned players. | `uxmessentials.moderation.banlist` |
| `/checkban` |  | moderation | Check whether a player is banned. | `uxmessentials.moderation.check` |
| `/checkmute` |  | moderation | Check whether a player is muted. | `uxmessentials.moderation.check` |
| `/commandspy` | `/cspy` | moderation | Watch the commands other players run. | `uxmessentials.moderation.commandspy` |
| `/freeze` |  | moderation | Freeze a player in place. | `uxmessentials.moderation.freeze` |
| `/history` |  | moderation | Review a player's full sanction history. | `uxmessentials.moderation.history` |
| `/jail` |  | moderation | Confine a player to a jail, optionally for a duration. | `uxmessentials.moderation.jail` |
| `/jailedplayers` |  | moderation | List the players currently jailed. | `uxmessentials.moderation.jail` |
| `/jails` |  | moderation | List the configured jails. | `uxmessentials.moderation.jail` |
| `/kick` |  | moderation | Kick a player (prefix the reason with -s to kick silently). | `uxmessentials.moderation.kick` |
| `/kickall` |  | moderation | Kick all non-exempt players. | `uxmessentials.moderation.kick` |
| `/lockdown` |  | moderation | Lock the server so only bypass holders can join. | `uxmessentials.moderation.lockdown` |
| `/mod` |  | moderation | Open the moderation management GUI (active punishments + per-player history). | `uxmessentials.moderation.gui` |
| `/modstats` |  | moderation | Show staff punishment analytics. | `uxmessentials.moderation.stats` |
| `/mute` |  | moderation | Mute a player, optionally for a duration (prefix the reason with -s to mute silently). | `uxmessentials.moderation.mute` |
| `/mutehistory` |  | moderation | Review a player's mute history. | `uxmessentials.moderation.mute` |
| `/mutelist` |  | moderation | Review currently muted players. | `uxmessentials.moderation.mutelist` |
| `/punish` |  | moderation | Apply a configured punishment template to a player. | `uxmessentials.moderation.templates` |
| `/sanction` |  | moderation | Show a player's current mute, jail, ban and warning summary. | `uxmessentials.moderation.sanction` |
| `/seen` |  | moderation | Show when a player was last seen. | `uxmessentials.moderation.seen` |
| `/seenip` |  | moderation | Show a player's last IP and alts. | `uxmessentials.moderation.seen` |
| `/setjail` |  | moderation | Define a jail at your location. | `uxmessentials.moderation.jail` |
| `/staffhistory` |  | moderation | Review the sanctions a staff member issued. | `uxmessentials.moderation.staffhistory` |
| `/staffrollback` |  | moderation | Revoke a staff member's still-active sanctions. | `uxmessentials.moderation.staffrollback` |
| `/sudo` |  | moderation | Run a command as another player. | `uxmessentials.moderation.sudo` |
| `/tempban` |  | moderation | Ban a player for a duration (prefix the reason with -s to ban silently). | `uxmessentials.moderation.tempban` |
| `/tempbanip` |  | moderation | Ban an IP for a duration. | `uxmessentials.moderation.banip` |
| `/tempmute` |  | moderation | Mute a player for a duration (prefix the reason with -s to mute silently). | `uxmessentials.moderation.mute` |
| `/tempwarn` |  | moderation | Warn a player for a duration (prefix the reason with -s to warn silently). | `uxmessentials.moderation.warn` |
| `/togglejail` |  | moderation | Toggle a player's jail: release if jailed, otherwise jail them. | `uxmessentials.moderation.togglejail` |
| `/unban` |  | moderation | Lift a player's permanent ban. | `uxmessentials.moderation.ban` |
| `/unbanip` |  | moderation | Lift an IP ban. | `uxmessentials.moderation.banip` |
| `/unfreeze` |  | moderation | Release a frozen player. | `uxmessentials.moderation.freeze` |
| `/unjail` |  | moderation | Release a jailed player. | `uxmessentials.moderation.unjail` |
| `/unmute` |  | moderation | Lift a player's mute. | `uxmessentials.moderation.unmute` |
| `/unwarn` |  | moderation | Clear a player's warnings. | `uxmessentials.moderation.warn` |
| `/warn` |  | moderation | Warn a player (prefix the reason with -s to warn silently). | `uxmessentials.moderation.warn` |
| `/warns` |  | moderation | Review a player's warnings. | `uxmessentials.moderation.warn` |
| `/npc` |  | npc | Create and manage fake-player NPCs. | `uxmessentials.npc.admin` |
| `/air` |  | playerstate | Set a player's remaining air. | `uxmessentials.air.use` |
| `/biome` |  | playerstate | Show the biome you are standing in. | `uxmessentials.biome.use` |
| `/burn` |  | playerstate | Set a player on fire for some seconds. | `uxmessentials.burn.use` |
| `/clearinventory` | `/ci`, `/clear` | playerstate | Clear a player's inventory. | `uxmessentials.clearinventory.use` |
| `/clearinventoryconfirmtoggle` | `/citoggle` | playerstate | Toggle a confirmation before /clearinventory. | `uxmessentials.clearinventory.confirmtoggle` |
| `/compass` |  | playerstate | Show the direction you are facing. | `uxmessentials.compass.use` |
| `/depth` |  | playerstate | Show your height relative to sea level. | `uxmessentials.depth.use` |
| `/dimension` | `/dim` | playerstate | Show the dimension you are standing in. | `uxmessentials.dimension.use` |
| `/endersee` |  | playerstate | View a player's ender chest. | `uxmessentials.endersee.use` |
| `/exp` | `/xp` | playerstate | Get or set a player's experience. | `uxmessentials.exp.use` |
| `/ext` | `/extinguish` | playerstate | Put out a burning player. | `uxmessentials.extinguish.use` |
| `/feed` | `/eat` | playerstate | Restore hunger. | `uxmessentials.feed.use` |
| `/fly` |  | playerstate | Toggle flight. | `uxmessentials.fly.use` |
| `/flyspeed` |  | playerstate | Set fly speed. | `uxmessentials.speed.use` |
| `/foodlevel` |  | playerstate | Set a player's food level. | `uxmessentials.foodlevel.use` |
| `/gamemode` | `/gm` | playerstate | Set game mode. | `uxmessentials.gamemode.use` |
| `/getpos` | `/coords`, `/whereami` | playerstate | Show a player's coordinates. | `uxmessentials.getpos.use` |
| `/glow` |  | playerstate | Toggle a glowing outline, in a colour of your choice. | `uxmessentials.glow.use` |
| `/gma` |  | playerstate | Set game mode to adventure. | `uxmessentials.gamemode.use` |
| `/gmc` |  | playerstate | Set game mode to creative. | `uxmessentials.gamemode.use` |
| `/gms` |  | playerstate | Set game mode to survival. | `uxmessentials.gamemode.use` |
| `/gmsp` |  | playerstate | Set game mode to spectator. | `uxmessentials.gamemode.use` |
| `/god` | `/godmode` | playerstate | Toggle damage immunity. | `uxmessentials.god.use` |
| `/heal` |  | playerstate | Restore health. | `uxmessentials.heal.use` |
| `/health` |  | playerstate | Set a player's health. | `uxmessentials.health.use` |
| `/ice` |  | playerstate | Freeze a player for some seconds. | `uxmessentials.ice.use` |
| `/invsee` |  | playerstate | View a player's inventory. | `uxmessentials.invsee.use` |
| `/near` | `/nearby` | playerstate | List nearby players. | `uxmessentials.near.use` |
| `/nightvision` | `/nv` | playerstate | Toggle night vision. | `uxmessentials.nightvision.use` |
| `/ping` |  | playerstate | Show a player's ping. | `uxmessentials.ping.use` |
| `/playtime` |  | playerstate | Show, correct, import or reset a player's playtime. | `uxmessentials.playtime.use` |
| `/ptime` |  | playerstate | Set your personal time. | `uxmessentials.ptime.use` |
| `/pweather` |  | playerstate | Set your personal weather. | `uxmessentials.pweather.use` |
| `/rest` |  | playerstate | Reset a player's time-since-rest so phantoms stop. | `uxmessentials.rest.use` |
| `/seed` |  | playerstate | Show the seed of the world you are standing in. | `uxmessentials.seed.use` |
| `/speed` |  | playerstate | Set walk and fly speed. | `uxmessentials.speed.use` |
| `/suicide` |  | playerstate | Kill yourself. | `uxmessentials.suicide.use` |
| `/walkspeed` |  | playerstate | Set walk speed. | `uxmessentials.speed.use` |
| `/world` |  | playerstate | Show the world you are standing in. | `uxmessentials.world.use` |
| `/pwarp` |  | playerwarps | Teleport to a player warp, edit your own warps, or manage their access and price. | `uxmessentials.pwarp.use` |
| `/pwarps` |  | playerwarps | List your warps or a player's public warps. | `uxmessentials.pwarp.list` |
| `/setpwarp` |  | playerwarps | Create or move a player warp at your location. | `uxmessentials.pwarp.set` |
| `/bellyflop` |  | poses | Flop onto your front; run again to stand up. | `uxmessentials.bellyflop.use` |
| `/crawl` |  | poses | Crawl through a one-block gap; run again to stand up. | `uxmessentials.crawl.use` |
| `/lay` |  | poses | Lie down where you stand; run again to stand up. | `uxmessentials.lay.use` |
| `/poses` |  | poses | Open your poses panel; /poses toggle to allow or refuse others sitting on you. | `uxmessentials.poses.gui` |
| `/sit` |  | poses | Sit down where you stand or on the block you're looking at; run again to stand up. | `uxmessentials.sit.use` |
| `/spin` |  | poses | Spin in place; run again to stop. | `uxmessentials.spin.use` |
| `/afk` | `/away` | presence | Toggle your AFK state. | `uxmessentials.afk.use` |
| `/gc` | `/lag`, `/tps`, `/mem` | presence | Show server health: TPS, uptime and memory. | `uxmessentials.gc.use` |
| `/list` | `/who`, `/online`, `/playerlist` | presence | List online players. | `uxmessentials.list.use` |
| `/nick` |  | presence | Set or clear a player's display name. | `uxmessentials.nick.use` |
| `/presencesettings` |  | presence | Open your personal presence settings panel. | `uxmessentials.presencesettings.use` |
| `/realname` |  | presence | Look up a player's real name. | `uxmessentials.realname.use` |
| `/staff` |  | presence | List online staff. | `uxmessentials.staff.use` |
| `/whois` |  | presence | Show information about an online player. | `uxmessentials.whois.use` |
| `/prestige` |  | ranks | /prestige to reset to the first rank for a prestige level once you reach the top rank. | `uxmessentials.ranks.prestige` |
| `/ranks` |  | ranks | /ranks to open the ladder panel; /ranks setrank <player> <rank> to set a player's rank directly. | `uxmessentials.ranks.admin` |
| `/rankup` |  | ranks | /rankup to advance to the next rank when you meet its requirements. | `uxmessentials.ranks.rankup` |
| `/setrank` |  | ranks | /setrank <player> <rank> to set a player's rank directly. | `uxmessentials.ranks.admin` |
| `/scoreboard` | `/sb` | scoreboard | Toggle whether you see the scoreboard display. | `uxmessentials.scoreboard.use` |
| `/2fa` | `/twofactor` | security | /2fa setup, confirm <code>, or disable <code> to manage your authenticator second factor. | `uxmessentials.security.2fa` |
| `/clientinfo` |  | security | Show the client brand a player reported. | `uxmessentials.security.clientinfo` |
| `/ipalts` |  | security | List the accounts that share an IP with a player. | `uxmessentials.security.alts` |
| `/pin` |  | security | /pin set, change, remove or lock with your numeric PIN second factor. | `uxmessentials.security.pin` |
| `/security` |  | security | /security status, force or reset a player's second factors. | `uxmessentials.security.admin` |
| `/skin` | `/skins` | skin | Wear the skin of another account, an image on the web, or one of the server's own. | `uxmessentials.skin.use` |
| `/staffchat` | `/sc` | staff | Send a message on the staff channel. | `uxmessentials.staff.chat` |
| `/stafflist` |  | staff | List who is currently online and on staff. | `uxmessentials.staff.list` |
| `/staffmode` |  | staff | Toggle staff mode. | `uxmessentials.staff.mode` |
| `/autopickup` |  | survival | Toggle auto-pickup: mined drops go straight to your inventory. | `uxmessentials.survival.autopickup.toggle` |
| `/autosell` |  | survival | Toggle auto-sell: priced drops are sold for coin as you mine. | `uxmessentials.survival.autosell.toggle` |
| `/autosmelt` |  | survival | Toggle auto-smelt: ores are smelted as you mine them. | `uxmessentials.survival.autosmelt.toggle` |
| `/autotool` |  | survival | Toggle auto-tool: the best tool swaps to hand as you mine. | `uxmessentials.survival.autotool.toggle` |
| `/farmprotect` |  | survival | Toggle farmland protection: stop yourself trampling crops. | `uxmessentials.survival.farmprotect.toggle` |
| `/survival` |  | survival | Open your survival panel: toggle tree-feller, veinminer, auto-pickup and the rest. | `uxmessentials.survival.gui` |
| `/treefeller` |  | survival | Toggle tree-feller: fell a whole tree by breaking one log. | `uxmessentials.survival.treefeller.toggle` |
| `/veinminer` |  | survival | Toggle veinminer: break a whole ore vein by mining one block. | `uxmessentials.survival.veinminer.toggle` |
| `/ascend` |  | teleport | Teleport up to the next open space. | `uxmessentials.tp.vertical` |
| `/back` | `/return` | teleport | Return to your last location. | `uxmessentials.back.use` |
| `/bottom` |  | teleport | Teleport to the lowest safe block. | `uxmessentials.tp.vertical` |
| `/bring` |  | teleport | Pull a player to you. | `uxmessentials.tp.use` |
| `/deathback` | `/dback` | teleport | Return to your last death location. | `uxmessentials.back.use` |
| `/descend` |  | teleport | Teleport down to the next open space. | `uxmessentials.tp.vertical` |
| `/down` |  | teleport | Teleport down to the first block below. | `uxmessentials.tp.vertical` |
| `/goto` |  | teleport | Teleport to a player. | `uxmessentials.tp.use` |
| `/jump` |  | teleport | Teleport to the block you look at. | `uxmessentials.tp.vertical` |
| `/mirrorspawn` |  | teleport | Redirect this world's spawn to another world. | `uxmessentials.spawn.set` |
| `/removespawn` |  | teleport | Clear this world's own spawn. | `uxmessentials.spawn.set` |
| `/rtp` | `/wild` | teleport | Randomly teleport within the world. | `uxmessentials.rtp.use` |
| `/setmainspawn` |  | teleport | Set the main spawn for worlds without their own. | `uxmessentials.spawn.set` |
| `/setspawn` |  | teleport | Set this world's spawn. | `uxmessentials.spawn.set` |
| `/settpr` |  | teleport | Set the random-teleport zone for the running /rtp. | `uxmessentials.teleport.settpr` |
| `/spawn` |  | teleport | Teleport to spawn. | `uxmessentials.spawn.use` |
| `/thru` |  | teleport | Teleport through the wall you are facing. | `uxmessentials.tp.vertical` |
| `/top` |  | teleport | Teleport to the highest block above. | `uxmessentials.tp.vertical` |
| `/tp` |  | teleport | Teleport to a player. | `uxmessentials.tp.use` |
| `/tpa` |  | teleport | Request to teleport to a player. | `uxmessentials.tpa.use` |
| `/tpaall` |  | teleport | Request every online player to teleport to you. | `uxmessentials.tpa.all` |
| `/tpaccept` | `/tpyes` | teleport | Accept a teleport request. | `uxmessentials.tpa.use` |
| `/tpahere` |  | teleport | Ask a player to teleport to you. | `uxmessentials.tpahere.use` |
| `/tpalist` | `/tprequests` | teleport | List players waiting on your teleport reply. | `uxmessentials.tpa.use` |
| `/tpall` |  | teleport | Pull every online player to you. | `uxmessentials.tp.all` |
| `/tpauto` |  | teleport | Auto-accept incoming teleport requests. | `uxmessentials.tpa.auto` |
| `/tpblock` |  | teleport | Block a player's requests. | `uxmessentials.tpa.block` |
| `/tpcancel` | `/tpacancel` | teleport | Withdraw your outgoing request. | `uxmessentials.tpa.cancel` |
| `/tpdeny` | `/tpno` | teleport | Deny a teleport request. | `uxmessentials.tpa.use` |
| `/tphere` |  | teleport | Pull a player to you. | `uxmessentials.tp.use` |
| `/tpo` |  | teleport | Teleport to a player, overriding their teleport settings. | `uxmessentials.tp.others` |
| `/tpoff` |  | teleport | Refuse incoming teleport requests. | `uxmessentials.tpa.toggle` |
| `/tpoffline` |  | teleport | Teleport to a player's logout point. | `uxmessentials.tp.offline` |
| `/tpofflinehere` |  | teleport | Bring a player from their logout point. | `uxmessentials.tp.offline` |
| `/tpohere` |  | teleport | Pull a player to you, overriding their teleport settings. | `uxmessentials.tp.others` |
| `/tpon` |  | teleport | Accept incoming teleport requests. | `uxmessentials.tpa.toggle` |
| `/tppos` |  | teleport | Teleport to raw coordinates. | `uxmessentials.tp.position` |
| `/tprandomplayer` | `/tprp` | teleport | Teleport to a random online player. | `uxmessentials.tp.use` |
| `/tpsettings` |  | teleport | Open your personal teleport settings panel. | `uxmessentials.tpsettings.use` |
| `/tptoggle` | `/toggletp` | teleport | Refuse all incoming teleport requests. | `uxmessentials.tpa.toggle` |
| `/tpunblock` |  | teleport | Unblock a player's requests. | `uxmessentials.tpa.block` |
| `/up` |  | teleport | Teleport up onto a placed block. | `uxmessentials.tp.vertical` |
| `/trade` |  | trade | /trade <player> to request a trade; /trade accept or deny [player] to answer one. | `uxmessentials.trade.use` |
| `/vanish` | `/v` | vanish | Become invisible to other players. | `uxmessentials.vanish.use` |
| `/vault` |  | vaults | Open one of your vaults, delete a vault, or audit another player's vault. | `uxmessentials.vault.use` |
| `/villager` |  | villagers | Manage the villager you are looking at (/villager manager, /villager protect, /villager follow). | `uxmessentials.villagers.use` |
| `/vote` |  | vote | Show the server's vote links. | `uxmessentials.vote.use` |
| `/voteparty` |  | vote | Show progress towards the next vote party. | `uxmessentials.voteparty.use` |
| `/warp` | `/wp`, `/warps` | warps | Teleport to a server warp; list, create, delete, move, lock, edit or inspect warps. | `uxmessentials.warp.use` |
| `/worlds` |  | worlds | Manage worlds: create, import, load, unload, unregister, delete, list, info, spawn, tp, gui, pregen, backup, backups, restore, restoreconfirm. | `uxmessentials.world.use` |
