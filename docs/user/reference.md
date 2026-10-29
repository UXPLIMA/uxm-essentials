# Reference

Contents: [Commands](#commands) · [Permissions](#permissions) · [Placeholders](#placeholders) · [Files](#files)

## Commands

The top level commands each module registers, with the permission that gates them. A module that is switched off registers none of its commands.

`/uxmess` is the administration root, gated by `uxmessentials.admin`: `/uxmess status` and `/uxmess help` show the state of the plugin, `/uxmess doctor` checks the setup (`doctor repair` fixes what it can, `uxmessentials.admin.doctor.repair`), and `/uxmess reload` reads every file again (`uxmessentials.admin.reload`).

| Command | Module | What it does | Permission |
|---|---|---|---|
| `/broadcast` | communication | Send an announcement to all online players | `uxmessentials.communication.broadcast` |
| `/broadcasttoggle` | communication | Toggle whether you receive server announcements | `uxmessentials.communication.broadcasttoggle` |
| `/broadcastworld` | communication | Send an announcement to players in your world | `uxmessentials.communication.broadcastworld` |
| `/clearchat` | communication | Flush the chat for online players | `uxmessentials.communication.clearchat` |
| `/me` | communication | Broadcast an action message about yourself | `uxmessentials.communication.me` |
| `/togglechat` | communication | Lock or unlock public chat for non-staff | `uxmessentials.communication.togglechat` |
| `/balance` | economy | Show a wallet balance | `uxmessentials.economy.balance` |
| `/baltop` | economy | View the top balances | `uxmessentials.economy.baltop` |
| `/bank` | economy | Shared bank accounts management | `uxmessentials.economy.bank` |
| `/deposit` | economy | Deposit physical banknotes into virtual balance | `uxmessentials.economy.deposit` |
| `/eco` | economy | Eco-admin balance mutations | `uxmessentials.economy.admin` |
| `/loan` | economy | Debtor loans and credit rating | `uxmessentials.economy.loan` |
| `/pay` | economy | Transfer funds to another player | `uxmessentials.economy.pay` |
| `/payall` | economy | Pay every online player | `uxmessentials.economy.payall` |
| `/payconfirm` | economy | Confirm a large pending pay | `uxmessentials.economy.pay` |
| `/paytoggle` | economy | Toggle accepting incoming pay | `uxmessentials.economy.pay.toggle` |
| `/sell` | economy | Sell held items at their configured worth | `uxmessentials.economy.sell` |
| `/sellall` | economy | Sell every sellable item in your inventory | `uxmessentials.economy.sell` |
| `/setworth` | economy | Set an item's sell worth | `uxmessentials.economy.setworth` |
| `/wallet` | economy | Show balances across all currencies | `uxmessentials.economy.wallet` |
| `/withdraw` | economy | Withdraw virtual balance into a physical check | `uxmessentials.economy.withdraw` |
| `/worth` | economy | Report a held item's configured sell value | `uxmessentials.economy.worth` |
| `/hologram` | holograms | Create and manage Display-entity holograms | `uxmessentials.hologram.use` |
| `/home` | homes | Open and manage your homes | `uxmessentials.home.use` |
| `/kit` | kits | Claim a kit | `uxmessentials.kit.use` |
| `/helpop` | messaging | Open a staff support request | `uxmessentials.helpop.use` |
| `/ignore` | messaging | Ignore a player | `uxmessentials.msg.ignore` |
| `/ignorelist` | messaging | List the players you ignore | `uxmessentials.msg.ignore` |
| `/mail` | messaging | Read, send or clear your mail | `uxmessentials.mail.use` |
| `/mailclear` | messaging | Clear your mailbox | `uxmessentials.mail.use` |
| `/msg` | messaging | Send a private message | `uxmessentials.msg.use` |
| `/msgtoggle` | messaging | Refuse incoming private messages | `uxmessentials.msg.toggle` |
| `/reply` | messaging | Reply to your last conversation | `uxmessentials.msg.reply` |
| `/rtoggle` | messaging | Toggle whether you receive replies | `uxmessentials.msg.toggle` |
| `/socialspy` | messaging | Observe private messages, globally or for one player | `uxmessentials.msg.socialspy` |
| `/unignore` | messaging | Stop ignoring a player | `uxmessentials.msg.ignore` |
| `/mute` | moderation | Run a command as another player | `uxmessentials.moderation.mute` |
| `/npc` | npc | Create and manage fake-player NPCs | `uxmessentials.npc.admin` |
| `/air` | playerstate | Set remaining air | `uxmessentials.air.use` |
| `/biome` | playerstate | Show the biome you are standing in | `uxmessentials.biome.use` |
| `/burn` | playerstate | Set a player on fire | `uxmessentials.burn.use` |
| `/clearinventory` | playerstate | Clear a player's inventory | `uxmessentials.clearinventory.use` |
| `/clearinventoryconfirmtoggle` | playerstate | Toggle a confirmation before /clearinventory | `uxmessentials.clearinventory.confirmtoggle` |
| `/compass` | playerstate | Show the direction you are facing | `uxmessentials.compass.use` |
| `/depth` | playerstate | Show how far you are above or below sea level | `uxmessentials.depth.use` |
| `/dimension` | playerstate | Show the dimension you are standing in | `uxmessentials.dimension.use` |
| `/endersee` | playerstate | View a player's ender chest | `uxmessentials.endersee.use` |
| `/exp` | playerstate | Get or set experience | `uxmessentials.exp.use` |
| `/ext` | playerstate | Put out a burning player | `uxmessentials.extinguish.use` |
| `/feed` | playerstate | Restore hunger | `uxmessentials.feed.use` |
| `/fly` | playerstate | Toggle flight | `uxmessentials.fly.use` |
| `/flyspeed` | playerstate | Set fly speed | `uxmessentials.speed.use` |
| `/foodlevel` | playerstate | Set a player's food level | `uxmessentials.foodlevel.use` |
| `/gamemode` | playerstate | Set game mode | `uxmessentials.gamemode.use` |
| `/getpos` | playerstate | Show a player's coordinates | `uxmessentials.getpos.use` |
| `/glow` | playerstate | Toggle a glowing outline | `uxmessentials.glow.use` |
| `/god` | playerstate | Toggle damage immunity | `uxmessentials.god.use` |
| `/heal` | playerstate | Restore health | `uxmessentials.heal.use` |
| `/health` | playerstate | Set a player's health | `uxmessentials.health.use` |
| `/ice` | playerstate | Freeze a player | `uxmessentials.ice.use` |
| `/invsee` | playerstate | View a player's inventory | `uxmessentials.invsee.use` |
| `/near` | playerstate | List nearby players | `uxmessentials.near.use` |
| `/nightvision` | playerstate | Toggle night vision | `uxmessentials.nightvision.use` |
| `/ping` | playerstate | Show a player's ping | `uxmessentials.ping.use` |
| `/playtime` | playerstate | Show a player's playtime breakdown | `uxmessentials.playtime.use` |
| `/ptime` | playerstate | Set your personal time | `uxmessentials.ptime.use` |
| `/pweather` | playerstate | Set your personal weather | `uxmessentials.pweather.use` |
| `/rest` | playerstate | Reset time-since-rest so phantoms stop | `uxmessentials.rest.use` |
| `/seed` | playerstate | Show the seed of the world you are standing in | `uxmessentials.seed.use` |
| `/speed` | playerstate | Set walk and fly speed | `uxmessentials.speed.use` |
| `/suicide` | playerstate | Kill yourself | `uxmessentials.suicide.use` |
| `/walkspeed` | playerstate | Set walk speed | `uxmessentials.speed.use` |
| `/world` | playerstate | Show the world you are standing in | `uxmessentials.world.use` |
| `/pwarp` | playerwarps | Teleport to a player warp | `uxmessentials.pwarp.use` |
| `/pwarps` | playerwarps | List your or a player's public warps | `uxmessentials.pwarp.list` |
| `/setpwarp` | playerwarps | Create or move a player warp | `uxmessentials.pwarp.set` |
| `/afk` | presence | Toggle your AFK state | `uxmessentials.afk.use` |
| `/gc` | presence | Show server health | `uxmessentials.gc.use` |
| `/list` | presence | List online players | `uxmessentials.list.use` |
| `/nick` | presence | Set or clear a player's display name | `uxmessentials.nick.use` |
| `/realname` | presence | Look up a player's real name | `uxmessentials.realname.use` |
| `/staff` | presence | List online staff | `uxmessentials.staff.use` |
| `/whois` | presence | Show information about an online player | `uxmessentials.whois.use` |
| `/staffchat` | staff | sc | `uxmessentials.staff.chat` |
| `/stafflist` | staff | List who is currently in staff mode | `uxmessentials.staff.list` |
| `/staffmode` | staff | Toggle staff mode | `uxmessentials.staff.mode` |
| `/ascend` | teleport | Teleport up to the next open space | `uxmessentials.tp.vertical` |
| `/back` | teleport | Return to your last location | `uxmessentials.back.use` |
| `/bottom` | teleport | Teleport to the lowest safe block | `uxmessentials.tp.vertical` |
| `/bring` | teleport | Pull a player to you | `uxmessentials.tp.use` |
| `/deathback` | teleport | dback | `uxmessentials.back.use` |
| `/descend` | teleport | Teleport down to the next open space | `uxmessentials.tp.vertical` |
| `/down` | teleport | Teleport down to the first block below | `uxmessentials.tp.vertical` |
| `/goto` | teleport | Teleport to a player | `uxmessentials.tp.use` |
| `/jump` | teleport | Teleport to the block you look at | `uxmessentials.tp.vertical` |
| `/mirrorspawn` | teleport | Redirect this world's spawn to another world | `uxmessentials.spawn.set` |
| `/removespawn` | teleport | Clear this world's own spawn | `uxmessentials.spawn.set` |
| `/rtp` | teleport | wild | `uxmessentials.rtp.use` |
| `/setmainspawn` | teleport | Set the global main spawn | `uxmessentials.spawn.set` |
| `/setspawn` | teleport | Set the server spawn | `uxmessentials.spawn.set` |
| `/settpr` | teleport | Set the random-teleport zone for /rtp | `uxmessentials.teleport.settpr` |
| `/spawn` | teleport | Teleport to spawn | `uxmessentials.spawn.use` |
| `/thru` | teleport | Teleport through the wall you are facing | `uxmessentials.tp.vertical` |
| `/top` | teleport | Teleport to the highest block above | `uxmessentials.tp.vertical` |
| `/tp` | teleport | Teleport to a player or coordinates | `uxmessentials.tp.use` |
| `/tpa` | teleport | Request to teleport to a player | `uxmessentials.tpa.use` |
| `/tpaall` | teleport | Request every player to teleport to you | `uxmessentials.tpa.all` |
| `/tpaccept` | teleport | tpyes | `uxmessentials.tpa.use` |
| `/tpahere` | teleport | Ask a player to teleport to you | `uxmessentials.tpahere.use` |
| `/tpalist` | teleport | tprequests | `uxmessentials.tpa.use` |
| `/tpall` | teleport | Pull every player to you | `uxmessentials.tp.all` |
| `/tpauto` | teleport | Auto-accept incoming teleport requests | `uxmessentials.tpa.auto` |
| `/tpblock` | teleport | Block a player's requests | `uxmessentials.tpa.block` |
| `/tpcancel` | teleport | tpacancel | `uxmessentials.tpa.cancel` |
| `/tpdeny` | teleport | tpno | `uxmessentials.tpa.use` |
| `/tphere` | teleport | Pull a player to you | `uxmessentials.tp.use` |
| `/tpo` | teleport | Teleport overriding no-tp flags | `uxmessentials.tp.others` |
| `/tpoff` | teleport | Refuse incoming teleport requests | `uxmessentials.tpa.toggle` |
| `/tpoffline` | teleport | Teleport to a player's logout point | `uxmessentials.tp.offline` |
| `/tpofflinehere` | teleport | Bring a player from their logout point | `uxmessentials.tp.offline` |
| `/tpohere` | teleport | Pull a player overriding flags | `uxmessentials.tp.others` |
| `/tpon` | teleport | Accept incoming teleport requests | `uxmessentials.tpa.toggle` |
| `/tppos` | teleport | Teleport to raw coordinates | `uxmessentials.tp.position` |
| `/tprandomplayer` | teleport | tprp | `uxmessentials.tp.use` |
| `/tptoggle` | teleport | toggletp | `uxmessentials.tpa.toggle` |
| `/tpunblock` | teleport | Unblock a player's requests | `uxmessentials.tpa.block` |
| `/up` | teleport | Teleport up onto a placed block | `uxmessentials.tp.vertical` |
| `/vault` | vaults | Open one of your vaults | `uxmessentials.vault.use` |
| `/vote` | vote | Show the server's vote links | `uxmessentials.vote.use` |
| `/voteparty` | vote | Show progress towards the next vote party | `uxmessentials.voteparty.use` |
| `/warp` | warps | Teleport to a server warp | `uxmessentials.warp.use` |
| `/worlds` | worlds | Manage worlds | `uxmessentials.world.use` |

## Permissions

Listed by module in [permissions-gameplay.md](permissions-gameplay.md) and [permissions-server.md](permissions-server.md).

## Placeholders

Registered when PlaceholderAPI is installed, as `%uxmessentials_<key>%`. A `player` placeholder reads the player it is asked about; a `global` one is the same for everybody. `<n>` and other angle brackets are filled in by you, for example `%uxmessentials_homes_1_world%`.

| Key | Module | Value | Scope |
|---|---|---|---|
| `bossbar_count` | bossbar | How many boss bars are on the player's screen. | session |
| `bossbar_remaining_<bar>` | bossbar | The time left on a timed or announced bar for the player, in their words (5d 8h 52m), or a dash when it has no clock running. | session |
| `bossbar_shown` | bossbar | The ids of the boss bars on the player's screen, top first, or a dash when they see none. | session |
| `bossbar_visible` | bossbar | Whether the player has their boss bars showing (yes/no). | session |
| `commandcontrol_allowed_<command>` | commandcontrol | Whether the player may run that command where they stand (yes/no), answered from the rules the gate uses. | session |
| `communication_broadcasts` | communication | Whether the player receives the rotating announcements (yes/no). | session |
| `communication_chat_enabled` | communication | Whether public chat is open rather than locked by /togglechat (yes/no). | global |
| `communication_prefix` | communication | The player's chat prefix, a catalogue-line prefix read in the player's own language. | session |
| `communication_suffix` | communication | The player's chat suffix, read the same way. | session |
| `prefix` | communication | The chat prefix of the player the line is about, a catalogue-line prefix read in the viewer's language. | relational |
| `suffix` | communication | The chat suffix of the player the line is about, read the same way. | relational |
| `menu_argument_<name>` | custommenus | The value of one named argument the open menu was called with. | session |
| `menu_is_in_menu` | custommenus | Whether the player has a plugin menu open (yes/no). | session |
| `menu_last` | custommenus | The id of the last menu the player opened, which survives the menu closing. | session |
| `menu_opened` | custommenus | The id of the menu the player has open. | session |
| `menu_page` | custommenus | The page the open menu is showing, counting from 1. | session |
| `menu_rows` | custommenus | How many rows the open menu has. | session |
| `balance` | economy | The player's balance in the default currency, as a plain number. | player |
| `balance_formatted` | economy | The player's balance with the currency symbol and grouping applied. | player |
| `baltop_position` | economy | The player's place on the default-currency leaderboard. | player |
| `economy_balance` | economy | The player's balance in the default currency, as a plain number. | player |
| `economy_balance_<currency>` | economy | The player's balance in one named currency, as a plain number. | player |
| `economy_balance_compact` | economy | The player's balance shortened to 1.2k / 3.4M. | player |
| `economy_balance_formatted` | economy | The player's balance with the currency symbol and grouping applied. | player |
| `economy_balance_formatted_<currency>` | economy | The player's balance in one named currency, with its symbol applied. | player |
| `economy_balance_short` | economy | The same shortened balance, under the spelling a config may prefer. | player |
| `economy_baltop_<currency>_<n>_amount` | economy | The balance of the player ranked nth in one named currency, as a plain number. | global |
| `economy_baltop_<currency>_<n>_formatted` | economy | The balance of the player ranked nth in one named currency, with its symbol applied. | global |
| `economy_baltop_<currency>_<n>_name` | economy | The name of the player ranked nth on one named currency's leaderboard. | global |
| `economy_baltop_<currency>_<n>_uuid` | economy | The uuid of the player ranked nth on one named currency's leaderboard. | global |
| `economy_baltop_<n>_amount` | economy | The balance of the player ranked nth, as a plain number. | global |
| `economy_baltop_<n>_formatted` | economy | The balance of the player ranked nth, with the currency symbol applied. | global |
| `economy_baltop_<n>_name` | economy | The name of the player ranked nth on the default-currency leaderboard. | global |
| `economy_baltop_<n>_uuid` | economy | The uuid of the player ranked nth on the default-currency leaderboard. | global |
| `economy_baltop_position` | economy | The player's place on the default-currency leaderboard. | player |
| `economy_currency_name` | economy | The plural name of the default currency. | global |
| `economy_currency_symbol` | economy | The symbol of the default currency. | global |
| `holograms_count` | holograms | How many holograms are placed. | global |
| `homes_<n>` | homes | The name of the player's nth home, counting from 1. | player |
| `homes_<n>_world` | homes | The world the player's nth home sits in. | player |
| `homes_<n>_x` | homes | The block x of the player's nth home. | player |
| `homes_<n>_y` | homes | The block y of the player's nth home. | player |
| `homes_<n>_z` | homes | The block z of the player's nth home. | player |
| `homes_count` | homes | How many homes the player has set. | player |
| `homes_exists_<home>` | homes | Whether the player has a home by that name (yes/no). | player |
| `homes_left` | homes | How many more homes the player may set. | player |
| `homes_limit` | homes | How many homes the player may keep; the infinity marker when the quota is unlimited. | player |
| `homes_list` | homes | The player's home names, comma separated. | player |
| `invrollback_captured` | invrollback | Whether any snapshot has been taken for the player since the last restart (yes/no). | session |
| `invrollback_last_capture` | invrollback | How long ago this server last snapshotted the player's inventory, since the last restart. | session |
| `invrollback_last_cause` | invrollback | What caused that snapshot: death or logout. | session |
| `itemworld_powertool` | itemworld | The commands bound to the item in the player's hand, comma separated. | session |
| `itemworld_powertool_bound` | itemworld | Whether the held item runs anything on click (yes/no). | session |
| `itemworld_powertool_count` | itemworld | How many commands the held item is bound to. | session |
| `itemworld_powertool_enabled` | itemworld | Whether the player currently lets their powertool bindings fire (yes/no). | player |
| `itemworld_unlimited` | itemworld | Whether the player is placing blocks without consuming them (yes/no). | player |
| `kit_available_<kit>` | kits | Whether the player may claim one kit right now (yes/no). | player |
| `kit_claims_left_<kit>` | kits | How many claims of one kit the player has left; the infinity marker when it repeats. | player |
| `kit_cooldown_<kit>` | kits | The wait left before the player may claim one kit again. | player |
| `kit_cooldown_<kit>_formatted` | kits | The same remaining kit wait, under the spelling a config may prefer. | player |
| `kit_cost_<kit>` | kits | What one kit charges to claim, or free when it charges nothing. | global |
| `kit_has_<kit>` | kits | Whether the player holds one kit's permission (yes/no). | player |
| `kits_list` | kits | The ids of the kits the player may claim, comma separated. | player |
| `ignored_by` | messaging | Whether the player the line is about ignores the viewer (yes/no). | relational |
| `ignoring` | messaging | Whether the viewer's ignore list holds the player the line is about (yes/no). | relational |
| `messaging_ignoring_count` | messaging | How many players this player is ignoring. | player |
| `messaging_mail_total` | messaging | How much mail the player holds. | player |
| `messaging_mail_unread` | messaging | How much mail the player has not read. | player |
| `messaging_msgtoggle` | messaging | Whether the player accepts private messages (yes/no). | session |
| `messaging_reply_target` | messaging | Who /r would answer: the last player this one talked to. | session |
| `messaging_socialspy` | messaging | Whether the player is watching other players' messages (yes/no). | session |
| `jailed` | moderation | Whether the player is jailed (yes/no). | player |
| `moderation_ban_issuer` | moderation | Who banned the player. | player |
| `moderation_ban_reason` | moderation | Why the player was banned. | player |
| `moderation_ban_remaining` | moderation | How long is left on the ban, in the compact 1d2h form. | player |
| `moderation_ban_remaining_formatted` | moderation | The same remaining ban, under the spelling a config may prefer. | player |
| `moderation_banned` | moderation | Whether the player is banned (yes/no). | player |
| `moderation_frozen` | moderation | Whether the player is frozen in place by staff (yes/no). | session |
| `moderation_jail_issuer` | moderation | Who jailed the player. | player |
| `moderation_jail_name` | moderation | The named jail the player is held in. | player |
| `moderation_jail_online_only` | moderation | Whether the jail counts down on online time rather than the wall clock (yes/no). | player |
| `moderation_jail_reason` | moderation | Why the player was jailed. | player |
| `moderation_jail_remaining` | moderation | How long is left on the jail, in whole seconds; permanent when only an unjail lifts it. | player |
| `moderation_jail_remaining_formatted` | moderation | The same remaining jail, in the compact 1d2h form. | player |
| `moderation_jailed` | moderation | Whether the player is jailed (yes/no). | player |
| `moderation_mute_issuer` | moderation | Who muted the player. | player |
| `moderation_mute_reason` | moderation | Why the player was muted. | player |
| `moderation_mute_remaining` | moderation | How long is left on the mute, in the compact 1d2h form. | player |
| `moderation_mute_remaining_formatted` | moderation | The same remaining mute, under the spelling a config may prefer. | player |
| `moderation_muted` | moderation | Whether the player is muted (yes/no). | player |
| `moderation_warns` | moderation | How many warnings the player carries. | player |
| `muted` | moderation | Whether the player is muted (yes/no). | player |
| `nametags_format` | nametags | The nametag format the player wears, or a dash when they wear none. | session |
| `nametags_shown` | nametags | Whether the player wears a nametag at all (yes/no). | session |
| `npc_limit` | npc | How many NPCs the player may own, or unlimited. | player |
| `npc_owned` | npc | How many NPCs the player owns. | player |
| `npc_remaining` | npc | How many more NPCs the player may create, or unlimited. | player |
| `npc_total` | npc | How many NPCs the server holds. | global |
| `playerstate_biome` | playerstate | The biome the player is standing in. | session |
| `playerstate_fly` | playerstate | Whether the player may fly (yes/no). | session |
| `playerstate_fly_speed` | playerstate | The player's fly speed. | session |
| `playerstate_flying` | playerstate | Whether the player is flying right now (yes/no). | session |
| `playerstate_food` | playerstate | The player's food level. | session |
| `playerstate_gamemode` | playerstate | The player's game mode. | session |
| `playerstate_god` | playerstate | Whether the player takes no damage (yes/no). | session |
| `playerstate_health` | playerstate | The player's current health. | session |
| `playerstate_level` | playerstate | The player's experience level. | session |
| `playerstate_max_health` | playerstate | The player's maximum health. | session |
| `playerstate_playtime` | playerstate | How long the player has played, in whole hours. | session |
| `playerstate_playtime_formatted` | playerstate | How long the player has played, in the compact 1d2h form. | session |
| `playerstate_speed` | playerstate | The speed that applies to how the player is moving: fly speed while flying, walk speed otherwise. | session |
| `playerstate_walk_speed` | playerstate | The player's walk speed. | session |
| `playerstate_world` | playerstate | The world the player is in. | session |
| `playerstate_x` | playerstate | The player's block x. | session |
| `playerstate_xp` | playerstate | How far the player is through the current experience level, from 0 to 1. | session |
| `playerstate_y` | playerstate | The player's block y. | session |
| `playerstate_z` | playerstate | The player's block z. | session |
| `playerwarp_<warp>_owner` | playerwarps | Who owns one of the player's warps. | player |
| `playerwarp_<warp>_visits` | playerwarps | How many times one of the player's warps has been used. | player |
| `playerwarp_<warp>_world` | playerwarps | The world one of the player's warps sits in. | player |
| `playerwarp_<warp>_x` | playerwarps | The block x of one of the player's warps. | player |
| `playerwarp_<warp>_y` | playerwarps | The block y of one of the player's warps. | player |
| `playerwarp_<warp>_z` | playerwarps | The block z of one of the player's warps. | player |
| `playerwarps_count` | playerwarps | How many player warps the player owns. | player |
| `playerwarps_left` | playerwarps | How many more player warps the player may create. | player |
| `playerwarps_limit` | playerwarps | How many player warps the player may own; the infinity marker when unlimited. | player |
| `playerwarps_list` | playerwarps | The names of the player warps the player owns, comma separated. | player |
| `poses_pose` | poses | The pose the player holds: sit, lay, bellyflop, spin or none. | session |
| `poses_posing` | poses | Whether the player holds a free pose: lay, bellyflop or spin (yes/no). | session |
| `poses_sitting` | poses | Whether the player is sitting (yes/no). | session |
| `poses_toggle` | poses | Whether the player lets others sit on them: allow or refuse. | session |
| `afk` | presence | Whether the player is away (yes/no). | session |
| `afk_duration` | presence | How long the player has been away, in the compact 1h30m form. | session |
| `presence_afk` | presence | Whether the player is away (yes/no). | session |
| `presence_afk_duration` | presence | How long the player has been away, in the compact 1h30m form. | session |
| `presence_afk_reason` | presence | The reason the player gave when they went away. | session |
| `presence_afk_since` | presence | The same away duration, under the spelling a config may prefer. | session |
| `presence_nickname` | presence | The player's nickname, or their name when they have not set one. | session |
| `presence_realname` | presence | The player's account name, whatever nickname they wear. | session |
| `presence_vanished` | presence | Whether the player is vanished (yes/no). | session |
| `vanished` | presence | Whether the player is vanished (yes/no). | session |
| `prestige` | ranks | How many times the player has prestiged. | player |
| `rank` | ranks | The player's current rank. | player |
| `rank_next` | ranks | The rank above the player's, or max when they are at the top of the ladder. | player |
| `rank_next_cost` | ranks | What the next rankup charges, or the dash at the top of the ladder. | player |
| `rank_position` | ranks | Which rung of the ladder the player stands on, counting from one. | player |
| `rank_progress` | ranks | How far up the ladder the player stands, as a whole percentage. | player |
| `rank_total` | ranks | How many rungs the ladder holds. | player |
| `regions_available` | regions | Whether a region provider is reachable at all (yes/no). | global |
| `regions_count` | regions | How many regions cover the player at once. | session |
| `regions_here` | regions | The region the player is standing in, highest priority first when they overlap. | session |
| `regions_here_members` | regions | Who may build in the region the player is standing in, comma separated. | session |
| `regions_here_owners` | regions | Who owns the region the player is standing in, comma separated. | session |
| `regions_here_priority` | regions | That region's priority, which is what decides an overlap. | session |
| `regions_inside` | regions | Whether the player is standing in a protected region (yes/no). | session |
| `regions_world_count` | regions | How many regions are defined in the world the player is in. | session |
| `scoreboard_board` | scoreboard | The board the player's sidebar is drawn from, or a dash when they are shown none. | session |
| `scoreboard_visible` | scoreboard | Whether the player has the sidebar showing (yes/no). | session |
| `security_enforced` | security | Whether the server asks players to verify on join at all (yes/no). | global |
| `security_verifying` | security | Whether the player has an open verification challenge they have not answered (yes/no). | session |
| `servertweaks_brand` | servertweaks | The server brand reported to clients on the F3 screen, or a dash when the tweak is off. | global |
| `skin_chosen` | skin | Whether the player chose a skin of their own (yes/no). | player |
| `skin_model` | skin | The player model the skin was cut for: classic or slim. | player |
| `skin_source` | skin | Where the player's chosen skin came from: by-name, by-url, by-file, bedrock or fallback. | player |
| `skin_value` | skin | What that source names: the account, the link, the file or the Bedrock id. | player |
| `staff_count` | staff | The same connected-staff count, under the spelling a config may prefer. | global |
| `staff_mode` | staff | Whether the player is in staff mode (yes/no). | session |
| `staff_online` | staff | How many staff members are connected. | global |
| `tablist_format` | tablist | The tablist format the player's tab is drawn from, or a dash when they are drawn none. | session |
| `tablist_shown` | tablist | Whether the player's tab is being drawn from a format at all (yes/no). | session |
| `teleport_accepting` | teleport | Whether the player accepts incoming teleport requests (yes/no). | session |
| `teleport_back_available` | teleport | Whether the player has a location to return to with /back (yes/no). | session |
| `teleport_back_world` | teleport | The world the player's /back location sits in. | session |
| `teleport_back_x` | teleport | The block x of the player's /back location. | session |
| `teleport_back_y` | teleport | The block y of the player's /back location. | session |
| `teleport_back_z` | teleport | The block z of the player's /back location. | session |
| `teleport_cooldown_remaining` | teleport | The wait left before the player may teleport again, in the compact 1m30s form. | session |
| `teleport_cooldown_remaining_formatted` | teleport | The same remaining teleport cooldown, under the spelling a config may prefer. | session |
| `teleport_tpa_incoming` | teleport | How many teleport requests are waiting for the player's answer. | session |
| `teleport_tpa_pending` | teleport | How many teleport requests the player has sent and not had answered. | session |
| `teleport_warmup_remaining` | teleport | The stand-still countdown left on the teleport in progress. | session |
| `teleport_warmup_remaining_formatted` | teleport | The same remaining warmup, under the spelling a config may prefer. | session |
| `trading` | trade | Whether the two are the sides of the same live trade (yes/no). | relational |
| `cansee` | vanish | Whether the viewer can see the player the line is about, or vanish hides them (yes/no). | relational |
| `hidden` | vanish | The same read the other way round: whether vanish hides them from the viewer (yes/no). | relational |
| `vaults_count` | vaults | How many vaults the player holds. | player |
| `vaults_left` | vaults | How many more vaults the player may open. | player |
| `vaults_max` | vaults | How many vaults the player may open; the infinity marker when unlimited. | player |
| `vaults_size` | vaults | How many rows each of the player's vaults holds. | player |
| `villagers_following` | villagers | How many villagers are walking after the player right now. | session |
| `villagers_has_follower` | villagers | Whether any villager is following the player (yes/no). | session |
| `voteparty_current` | vote | How many votes the party has collected. | global |
| `voteparty_remaining` | vote | How many votes the party still needs. | global |
| `voteparty_required` | vote | How many votes the party needs to fire. | global |
| `votes_<period>` | vote | The player's vote count for one period: daily, weekly, monthly or alltime. | player |
| `votes_position_<period>` | vote | Where the player sits on one period's vote leaderboard. | player |
| `votes_streak_best` | vote | The player's longest voting streak. | player |
| `votes_streak_current` | vote | How many days in a row the player has voted. | player |
| `votes_top_<period>_<n>_name` | vote | The name of the player ranked nth on one period's vote leaderboard. | global |
| `votes_top_<period>_<n>_votes` | vote | The vote count of the player ranked nth on one period's leaderboard. | global |
| `warp_<warp>_cost` | warps | What one warp charges to use. | global |
| `warp_<warp>_owner` | warps | Who created one warp. | global |
| `warp_<warp>_visits` | warps | How many times one warp has been used. | global |
| `warp_<warp>_world` | warps | The world one warp sits in. | global |
| `warp_<warp>_x` | warps | The block x of one warp. | global |
| `warp_<warp>_y` | warps | The block y of one warp. | global |
| `warp_<warp>_z` | warps | The block z of one warp. | global |
| `warps_count` | warps | How many warps the player may use. | player |
| `warps_list` | warps | The names of the warps the player may use, comma separated. | player |
| `worlds_default` | worlds | The name of the default world. | global |
| `worlds_default_players` | worlds | How many players are in the default world. | global |
| `worlds_loaded_count` | worlds | How many worlds are loaded right now. | global |
| `worlds_managed_count` | worlds | How many worlds the plugin's registry holds. | global |

### Boards

The economy and vote leaderboards also answer the grammar every UXM plugin shares, so a hologram written for another plugin's board works here. `<board>` is `balance` (the default currency, `alltime` only, because a balance is what a player holds now) or `votes` (`alltime`, `monthly`, `weekly`, `daily`, counted in the server's zone with weeks from Monday, as the vote module counts them).

| Key | Value |
|---|---|
| `top_<board>_<period>_<n>_name` | The name at place n, or the word for nobody. |
| `top_<board>_<period>_<n>_value` | The value at place n as the board shows it (a balance with its symbol, a vote count), or the word for none. |
| `top_<board>_<period>_<n>_head` | The player at place n for a `<head:...>` tag: their name, or their uuid when an account cannot have that name; the game's Steve for a place nobody holds. |
| `rank_<board>_<period>` | The reader's own place, or the word for none. |
| `value_<board>_<period>` | The reader's own balance or vote count. |
| `resets_<period>` | The time until a vote period starts again, in the reader's words (`4d 1h`, `1h 30m`, `12m`), or the word for none for `alltime`. |

The words come from the `board.*` lines of the messages files. The older `economy_baltop_*`, `baltop_position`, `votes_top_*` and `votes_position_*` keys keep answering as before.

## Files

On first start the plugin writes `config.conf`, `messages/messages_<tag>.conf` for every shipped language, and for each module a `modules/<module>/config.conf` with its menus under `modules/<module>/gui/`. `config.conf` lists every module and switches each one on or off. `/uxmess reload` reads the files again.
