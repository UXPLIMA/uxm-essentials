# Placeholders

<!-- Generated from the code catalogues by ./gradlew :core:generateReference. Edit the catalogue, not this page. -->

Registered when PlaceholderAPI is installed, as `%uxm_essentials_<key>%`. A `player` key reads the player it is asked about, offline too; a `session` key reads live state and answers the dash while the player is away; a `global` one is the same for everybody; a `relational` one is typed `%rel_uxm_essentials_<key>%` and answers about the player a line is drawn for, as the viewer sees them. `<n>` and other angle brackets are filled in by you, for example `%uxm_essentials_homes_1_world%`. The example shows the shape of an answer. The facts every server has, about a player, the item in their hand or the server itself, are in [placeholders-facts.md](placeholders-facts.md). The leaderboard keys are in [reference.md](reference.md#boards). The earlier `%uxmessentials_<key>%` form still answers in this release and logs a warning the first time it is read; [upgrading.md](upgrading.md) says what to change. `/uxmess placeholders export` writes the same catalogue from the running build to a file in the plugin folder.

| Key | Module | Value | Example | Scope |
|---|---|---|---|---|
| `bossbar_count` | bossbar | How many boss bars are on the player's screen. | `"2"` | session |
| `bossbar_remaining_<bar>` | bossbar | The time left on a timed or announced bar for the player, in their words (5d 8h 52m), or a dash when it has no clock running. | `"5d 8h 52m"` | session |
| `bossbar_shown` | bossbar | The ids of the boss bars on the player's screen, top first, or a dash when they see none. | `"event, restart"` | session |
| `bossbar_visible` | bossbar | Whether the player has their boss bars showing (yes/no). | `"yes"` | session |
| `commandcontrol_allowed_<command>` | commandcontrol | Whether the player may run that command where they stand (yes/no), answered from the rules the gate uses. | `"yes"` | session |
| `communication_broadcasts` | communication | Whether the player receives the rotating announcements (yes/no). | `"yes"` | session |
| `communication_chat_enabled` | communication | Whether public chat is open rather than locked by /togglechat (yes/no). | `"yes"` | global |
| `communication_prefix` | communication | The player's chat prefix, a catalogue-line prefix read in the player's own language. | `"[VIP] "` | session |
| `communication_suffix` | communication | The player's chat suffix, read the same way. | `" *"` | session |
| `prefix` | communication | The chat prefix of the player the line is about, a catalogue-line prefix read in the viewer's language. | `"[VIP] "` | relational |
| `suffix` | communication | The chat suffix of the player the line is about, read the same way. | `" *"` | relational |
| `menu_argument_<name>` | custommenus | The value of one named argument the open menu was called with. | `"diamond"` | session |
| `menu_is_in_menu` | custommenus | Whether the player has a plugin menu open (yes/no). | `"yes"` | session |
| `menu_last` | custommenus | The id of the last menu the player opened, which survives the menu closing. | `"shop"` | session |
| `menu_opened` | custommenus | The id of the menu the player has open. | `"shop"` | session |
| `menu_page` | custommenus | The page the open menu is showing, counting from 1. | `"2"` | session |
| `menu_rows` | custommenus | How many rows the open menu has. | `"6"` | session |
| `balance` | economy | The player's balance in the default currency, as a plain number. | `"1250.5"` | player |
| `balance_formatted` | economy | The player's balance with the currency symbol and grouping applied. | `"$1,250.50"` | player |
| `baltop_position` | economy | The player's place on the default-currency leaderboard. | `"7"` | player |
| `economy_balance` | economy | The player's balance in the default currency, as a plain number. | `"1250.5"` | player |
| `economy_balance_<currency>` | economy | The player's balance in one named currency, as a plain number. | `"40"` | player |
| `economy_balance_compact` | economy | The player's balance shortened to 1.2k / 3.4M. | `"1.25k"` | player |
| `economy_balance_formatted` | economy | The player's balance with the currency symbol and grouping applied. | `"$1,250.50"` | player |
| `economy_balance_formatted_<currency>` | economy | The player's balance in one named currency, with its symbol applied. | `"40 gems"` | player |
| `economy_balance_short` | economy | The same shortened balance, under the spelling a config may prefer. | `"1.25k"` | player |
| `economy_baltop_<currency>_<n>_amount` | economy | The balance of the player ranked nth in one named currency, as a plain number. | `"320"` | global |
| `economy_baltop_<currency>_<n>_formatted` | economy | The balance of the player ranked nth in one named currency, with its symbol applied. | `"320 gems"` | global |
| `economy_baltop_<currency>_<n>_name` | economy | The name of the player ranked nth on one named currency's leaderboard. | `"Alex"` | global |
| `economy_baltop_<currency>_<n>_uuid` | economy | The uuid of the player ranked nth on one named currency's leaderboard. | `"853c80ef-3c37-49fd-aa49-938b674adae6"` | global |
| `economy_baltop_<n>_amount` | economy | The balance of the player ranked nth, as a plain number. | `"98000"` | global |
| `economy_baltop_<n>_formatted` | economy | The balance of the player ranked nth, with the currency symbol applied. | `"$98,000.00"` | global |
| `economy_baltop_<n>_name` | economy | The name of the player ranked nth on the default-currency leaderboard. | `"Alex"` | global |
| `economy_baltop_<n>_uuid` | economy | The uuid of the player ranked nth on the default-currency leaderboard. | `"853c80ef-3c37-49fd-aa49-938b674adae6"` | global |
| `economy_baltop_position` | economy | The player's place on the default-currency leaderboard. | `"7"` | player |
| `economy_currency_name` | economy | The plural name of the default currency. | `"dollars"` | global |
| `economy_currency_symbol` | economy | The symbol of the default currency. | `"$"` | global |
| `regions_allowed_<action>` | guard | Whether the player may do a guard action where they stand, such as regions_allowed_break. | `"no"` | session |
| `regions_available` | guard | Whether the guard module is on (yes/no). Every regions_ placeholder is also readable as guard_. | `"yes"` | global |
| `regions_count` | guard | How many regions cover the player at once. | `"2"` | session |
| `regions_here` | guard | The region the player is standing in, highest priority first when they overlap. | `"spawn"` | session |
| `regions_here_members` | guard | Who may build in the region the player is standing in, comma separated. | `"Steve, Notch"` | session |
| `regions_here_owners` | guard | Who owns the region the player is standing in, comma separated. | `"Alex"` | session |
| `regions_here_priority` | guard | That region's priority, which is what decides an overlap. | `"10"` | session |
| `regions_here_role` | guard | The player's role in the region they stand in (owner, manager, member, guest), or empty. | `"member"` | session |
| `regions_inside` | guard | Whether the player is standing in a protected region (yes/no). | `"yes"` | session |
| `regions_world_count` | guard | How many regions are defined in the world the player is in. | `"17"` | session |
| `holograms_count` | holograms | How many holograms are placed. | `"12"` | global |
| `homes_<n>` | homes | The name of the player's nth home, counting from 1. | `"base"` | player |
| `homes_<n>_world` | homes | The world the player's nth home sits in. | `"world"` | player |
| `homes_<n>_x` | homes | The block x of the player's nth home. | `"128"` | player |
| `homes_<n>_y` | homes | The block y of the player's nth home. | `"64"` | player |
| `homes_<n>_z` | homes | The block z of the player's nth home. | `"-240"` | player |
| `homes_count` | homes | How many homes the player has set. | `"2"` | player |
| `homes_exists_<home>` | homes | Whether the player has a home by that name (yes/no). | `"yes"` | player |
| `homes_left` | homes | How many more homes the player may set. | `"3"` | player |
| `homes_limit` | homes | How many homes the player may keep; the infinity marker when the quota is unlimited. | `"5"` | player |
| `homes_list` | homes | The player's home names, comma separated. | `"base, farm"` | player |
| `invrollback_captured` | invrollback | Whether any snapshot has been taken for the player since the last restart (yes/no). | `"yes"` | session |
| `invrollback_last_capture` | invrollback | How long ago this server last snapshotted the player's inventory, since the last restart. | `"4m"` | session |
| `invrollback_last_cause` | invrollback | What caused that snapshot: death or logout. | `"death"` | session |
| `itemworld_powertool` | itemworld | The commands bound to the item in the player's hand, comma separated. | `"spawn, heal"` | session |
| `itemworld_powertool_bound` | itemworld | Whether the held item runs anything on click (yes/no). | `"yes"` | session |
| `itemworld_powertool_count` | itemworld | How many commands the held item is bound to. | `"2"` | session |
| `itemworld_powertool_enabled` | itemworld | Whether the player currently lets their powertool bindings fire (yes/no). | `"yes"` | player |
| `itemworld_unlimited` | itemworld | Whether the player is placing blocks without consuming them (yes/no). | `"no"` | player |
| `kit_available_<kit>` | kits | Whether the player may claim one kit right now (yes/no). | `"yes"` | player |
| `kit_claims_left_<kit>` | kits | How many claims of one kit the player has left; the infinity marker when it repeats. | `"2"` | player |
| `kit_cooldown_<kit>` | kits | The wait left before the player may claim one kit again. | `"23h59m"` | player |
| `kit_cooldown_<kit>_formatted` | kits | The same remaining kit wait, under the spelling a config may prefer. | `"23h59m"` | player |
| `kit_cost_<kit>` | kits | What one kit charges to claim, or free when it charges nothing. | `"$250.00"` | global |
| `kit_has_<kit>` | kits | Whether the player holds one kit's permission (yes/no). | `"yes"` | player |
| `kits_list` | kits | The ids of the kits the player may claim, comma separated. | `"starter, daily, vip"` | player |
| `ignored_by` | messaging | Whether the player the line is about ignores the viewer (yes/no). | `"no"` | relational |
| `ignoring` | messaging | Whether the viewer's ignore list holds the player the line is about (yes/no). | `"no"` | relational |
| `messaging_ignoring_count` | messaging | How many players this player is ignoring. | `"1"` | player |
| `messaging_mail_total` | messaging | How much mail the player holds. | `"5"` | player |
| `messaging_mail_unread` | messaging | How much mail the player has not read. | `"2"` | player |
| `messaging_msgtoggle` | messaging | Whether the player accepts private messages (yes/no). | `"yes"` | session |
| `messaging_reply_target` | messaging | Who /r would answer: the last player this one talked to. | `"Alex"` | session |
| `messaging_socialspy` | messaging | Whether the player is watching other players' messages (yes/no). | `"no"` | session |
| `jailed` | moderation | Whether the player is jailed (yes/no). | `"no"` | player |
| `moderation_ban_issuer` | moderation | Who banned the player. | `"Alex"` | player |
| `moderation_ban_reason` | moderation | Why the player was banned. | `"Cheating"` | player |
| `moderation_ban_remaining` | moderation | How long is left on the ban, in the compact 1d2h form. | `"6d23h"` | player |
| `moderation_ban_remaining_formatted` | moderation | The same remaining ban, under the spelling a config may prefer. | `"6d23h"` | player |
| `moderation_banned` | moderation | Whether the player is banned (yes/no). | `"no"` | player |
| `moderation_frozen` | moderation | Whether the player is frozen in place by staff (yes/no). | `"no"` | session |
| `moderation_jail_issuer` | moderation | Who jailed the player. | `"Alex"` | player |
| `moderation_jail_name` | moderation | The named jail the player is held in. | `"cells"` | player |
| `moderation_jail_online_only` | moderation | Whether the jail counts down on online time rather than the wall clock (yes/no). | `"yes"` | player |
| `moderation_jail_reason` | moderation | Why the player was jailed. | `"Griefing"` | player |
| `moderation_jail_remaining` | moderation | How long is left on the jail, in whole seconds; permanent when only an unjail lifts it. | `"1800"` | player |
| `moderation_jail_remaining_formatted` | moderation | The same remaining jail, in the compact 1d2h form. | `"30m"` | player |
| `moderation_jailed` | moderation | Whether the player is jailed (yes/no). | `"no"` | player |
| `moderation_mute_issuer` | moderation | Who muted the player. | `"Alex"` | player |
| `moderation_mute_reason` | moderation | Why the player was muted. | `"Spam"` | player |
| `moderation_mute_remaining` | moderation | How long is left on the mute, in the compact 1d2h form. | `"1h30m"` | player |
| `moderation_mute_remaining_formatted` | moderation | The same remaining mute, under the spelling a config may prefer. | `"1h30m"` | player |
| `moderation_muted` | moderation | Whether the player is muted (yes/no, or a dash while it is not known). | `"yes"` | player |
| `moderation_warns` | moderation | How many warnings the player carries. | `"1"` | player |
| `muted` | moderation | Whether the player is muted (yes/no, or a dash while it is not known). | `"no"` | player |
| `nametags_format` | nametags | The nametag format the player wears, or a dash when they wear none. | `"staff"` | session |
| `nametags_shown` | nametags | Whether the player wears a nametag at all (yes/no). | `"yes"` | session |
| `npc_limit` | npc | How many NPCs the player may own, or unlimited. | `"3"` | player |
| `npc_owned` | npc | How many NPCs the player owns. | `"1"` | player |
| `npc_remaining` | npc | How many more NPCs the player may create, or unlimited. | `"2"` | player |
| `npc_total` | npc | How many NPCs the server holds. | `"8"` | global |
| `playerstate_biome` | playerstate | The biome the player is standing in. | `"plains"` | session |
| `playerstate_fly` | playerstate | Whether the player may fly (yes/no). | `"yes"` | session |
| `playerstate_fly_speed` | playerstate | The player's fly speed. | `"0.1"` | session |
| `playerstate_flying` | playerstate | Whether the player is flying right now (yes/no). | `"no"` | session |
| `playerstate_food` | playerstate | The player's food level. | `"17"` | session |
| `playerstate_gamemode` | playerstate | The player's game mode. | `"creative"` | session |
| `playerstate_god` | playerstate | Whether the player takes no damage (yes/no). | `"no"` | session |
| `playerstate_health` | playerstate | The player's current health. | `"18.0"` | session |
| `playerstate_level` | playerstate | The player's experience level. | `"30"` | session |
| `playerstate_max_health` | playerstate | The player's maximum health. | `"20.0"` | session |
| `playerstate_playtime` | playerstate | How long the player has played, in whole hours. | `"135"` | session |
| `playerstate_playtime_formatted` | playerstate | How long the player has played, in the compact 1d2h form. | `"5d15h"` | session |
| `playerstate_speed` | playerstate | The speed that applies to how the player is moving: fly speed while flying, walk speed otherwise. | `"0.2"` | session |
| `playerstate_walk_speed` | playerstate | The player's walk speed. | `"0.2"` | session |
| `playerstate_world` | playerstate | The world the player is in. | `"world"` | session |
| `playerstate_x` | playerstate | The player's block x. | `"128"` | session |
| `playerstate_xp` | playerstate | How far the player is through the current experience level, from 0 to 1. | `"0.45"` | session |
| `playerstate_y` | playerstate | The player's block y. | `"64"` | session |
| `playerstate_z` | playerstate | The player's block z. | `"-240"` | session |
| `playerwarp_<warp>_owner` | playerwarps | Who owns one of the player's warps. | `"Steve"` | player |
| `playerwarp_<warp>_visits` | playerwarps | How many times one of the player's warps has been used. | `"27"` | player |
| `playerwarp_<warp>_world` | playerwarps | The world one of the player's warps sits in. | `"world"` | player |
| `playerwarp_<warp>_x` | playerwarps | The block x of one of the player's warps. | `"512"` | player |
| `playerwarp_<warp>_y` | playerwarps | The block y of one of the player's warps. | `"70"` | player |
| `playerwarp_<warp>_z` | playerwarps | The block z of one of the player's warps. | `"-96"` | player |
| `playerwarps_count` | playerwarps | How many player warps the player owns. | `"1"` | player |
| `playerwarps_left` | playerwarps | How many more player warps the player may create. | `"2"` | player |
| `playerwarps_limit` | playerwarps | How many player warps the player may own; the infinity marker when unlimited. | `"3"` | player |
| `playerwarps_list` | playerwarps | The names of the player warps the player owns, comma separated. | `"myshop"` | player |
| `poses_pose` | poses | The pose the player holds: sit, lay, bellyflop, spin or none. | `"sit"` | session |
| `poses_posing` | poses | Whether the player holds a free pose: lay, bellyflop or spin (yes/no). | `"no"` | session |
| `poses_sitting` | poses | Whether the player is sitting (yes/no). | `"yes"` | session |
| `poses_toggle` | poses | Whether the player lets others sit on them: allow or refuse. | `"allow"` | session |
| `afk` | presence | Whether the player is away (yes/no). | `"yes"` | session |
| `afk_duration` | presence | How long the player has been away, in the compact 1h30m form. | `"12m"` | session |
| `presence_afk` | presence | Whether the player is away (yes/no). | `"yes"` | session |
| `presence_afk_duration` | presence | How long the player has been away, in the compact 1h30m form. | `"12m"` | session |
| `presence_afk_reason` | presence | The reason the player gave when they went away. | `"dinner"` | session |
| `presence_afk_since` | presence | The same away duration, under the spelling a config may prefer. | `"12m"` | session |
| `presence_nickname` | presence | The player's nickname, or their name when they have not set one. | `"Stevie"` | session |
| `presence_realname` | presence | The player's account name, whatever nickname they wear. | `"Steve"` | session |
| `presence_vanished` | presence | Whether the player is vanished (yes/no). | `"no"` | session |
| `vanished` | presence | Whether the player is vanished (yes/no). | `"no"` | session |
| `prestige` | ranks | How many times the player has prestiged. | `"1"` | player |
| `rank` | ranks | The player's current rank. | `"knight"` | player |
| `rank_next` | ranks | The rank above the player's, or max when they are at the top of the ladder. | `"lord"` | player |
| `rank_next_cost` | ranks | What the next rankup charges, or the dash at the top of the ladder. | `"$5,000.00"` | player |
| `rank_position` | ranks | Which rung of the ladder the player stands on, counting from one. | `"3"` | player |
| `rank_progress` | ranks | How far up the ladder the player stands, as a whole percentage. | `"37"` | player |
| `rank_total` | ranks | How many rungs the ladder holds. | `"8"` | player |
| `scoreboard_board` | scoreboard | The board the player's sidebar is drawn from, or a dash when they are shown none. | `"default"` | session |
| `scoreboard_visible` | scoreboard | Whether the player has the sidebar showing (yes/no). | `"yes"` | session |
| `security_enforced` | security | Whether the server asks players to verify on join at all (yes/no). | `"yes"` | global |
| `security_verifying` | security | Whether the player has an open verification challenge they have not answered (yes/no). | `"no"` | session |
| `servertweaks_brand` | servertweaks | The server brand reported to clients on the F3 screen, or a dash when the tweak is off. | `"uxmEssentials"` | global |
| `skin_chosen` | skin | Whether the player chose a skin of their own (yes/no). | `"yes"` | player |
| `skin_model` | skin | The player model the skin was cut for: classic or slim. | `"classic"` | player |
| `skin_source` | skin | Where the player's chosen skin came from: by-name, by-url, by-file, bedrock or fallback. | `"by-name"` | player |
| `skin_value` | skin | What that source names: the account, the link, the file or the Bedrock id. | `"Notch"` | player |
| `staff_count` | staff | The same connected-staff count, under the spelling a config may prefer. | `"3"` | global |
| `staff_mode` | staff | Whether the player is in staff mode (yes/no). | `"no"` | session |
| `staff_online` | staff | How many staff members are connected. | `"3"` | global |
| `survival_autopickup` | survival | Whether the player has auto-pickup switched on (yes/no), the mechanic that sends drops straight to the inventory. | `"yes"` | player |
| `survival_autopickup_enabled` | survival | Whether this server runs auto-pickup at all (yes/no), the mechanic that sends drops straight to the inventory. | `"yes"` | global |
| `survival_autosell` | survival | Whether the player has auto-sell switched on (yes/no), the mechanic that sells what is mined. | `"yes"` | player |
| `survival_autosell_enabled` | survival | Whether this server runs auto-sell at all (yes/no), the mechanic that sells what is mined. | `"yes"` | global |
| `survival_autosmelt` | survival | Whether the player has auto-smelt switched on (yes/no), the mechanic that smelts what is mined. | `"yes"` | player |
| `survival_autosmelt_enabled` | survival | Whether this server runs auto-smelt at all (yes/no), the mechanic that smelts what is mined. | `"yes"` | global |
| `survival_autotool` | survival | Whether the player has auto-tool switched on (yes/no), the mechanic that swaps to the right tool. | `"yes"` | player |
| `survival_autotool_enabled` | survival | Whether this server runs auto-tool at all (yes/no), the mechanic that swaps to the right tool. | `"yes"` | global |
| `survival_farmprotect` | survival | Whether the player has farm protection switched on (yes/no), the mechanic that stops trampling crops. | `"yes"` | player |
| `survival_farmprotect_enabled` | survival | Whether this server runs farm protection at all (yes/no), the mechanic that stops trampling crops. | `"yes"` | global |
| `survival_treefeller` | survival | Whether the player has tree-feller switched on (yes/no), the mechanic that fells a whole tree in one break. | `"yes"` | player |
| `survival_treefeller_enabled` | survival | Whether this server runs tree-feller at all (yes/no), the mechanic that fells a whole tree in one break. | `"yes"` | global |
| `survival_veinminer` | survival | Whether the player has veinminer switched on (yes/no), the mechanic that follows an ore vein. | `"yes"` | player |
| `survival_veinminer_enabled` | survival | Whether this server runs veinminer at all (yes/no), the mechanic that follows an ore vein. | `"yes"` | global |
| `tablist_format` | tablist | The tablist format the player's tab is drawn from, or a dash when they are drawn none. | `"default"` | session |
| `tablist_shown` | tablist | Whether the player's tab is being drawn from a format at all (yes/no). | `"yes"` | session |
| `teleport_accepting` | teleport | Whether the player accepts incoming teleport requests (yes/no). | `"yes"` | session |
| `teleport_back_available` | teleport | Whether the player has a location to return to with /back (yes/no). | `"yes"` | session |
| `teleport_back_world` | teleport | The world the player's /back location sits in. | `"world_nether"` | session |
| `teleport_back_x` | teleport | The block x of the player's /back location. | `"-40"` | session |
| `teleport_back_y` | teleport | The block y of the player's /back location. | `"68"` | session |
| `teleport_back_z` | teleport | The block z of the player's /back location. | `"155"` | session |
| `teleport_cooldown_remaining` | teleport | The wait left before the player may teleport again, in the compact 1m30s form. | `"1m30s"` | session |
| `teleport_cooldown_remaining_formatted` | teleport | The same remaining teleport cooldown, under the spelling a config may prefer. | `"1m30s"` | session |
| `teleport_tpa_incoming` | teleport | How many teleport requests are waiting for the player's answer. | `"1"` | session |
| `teleport_tpa_pending` | teleport | How many teleport requests the player has sent and not had answered. | `"0"` | session |
| `teleport_warmup_remaining` | teleport | The stand-still countdown left on the teleport in progress. | `"3s"` | session |
| `teleport_warmup_remaining_formatted` | teleport | The same remaining warmup, under the spelling a config may prefer. | `"3s"` | session |
| `trading` | trade | Whether the two are the sides of the same live trade (yes/no). | `"no"` | relational |
| `cansee` | vanish | Whether the viewer can see the player the line is about, or vanish hides them (yes/no). | `"yes"` | relational |
| `hidden` | vanish | The same read the other way round: whether vanish hides them from the viewer (yes/no). | `"no"` | relational |
| `vaults_count` | vaults | How many vaults the player holds. | `"2"` | player |
| `vaults_left` | vaults | How many more vaults the player may open. | `"3"` | player |
| `vaults_max` | vaults | How many vaults the player may open; the infinity marker when unlimited. | `"5"` | player |
| `vaults_size` | vaults | How many rows each of the player's vaults holds. | `"6"` | player |
| `villagers_following` | villagers | How many villagers are walking after the player right now. | `"2"` | session |
| `villagers_has_follower` | villagers | Whether any villager is following the player (yes/no). | `"yes"` | session |
| `voteparty_current` | vote | How many votes the party has collected. | `"37"` | global |
| `voteparty_remaining` | vote | How many votes the party still needs. | `"13"` | global |
| `voteparty_required` | vote | How many votes the party needs to fire. | `"50"` | global |
| `votes_<period>` | vote | The player's vote count for one period: daily, weekly, monthly or alltime. | `"14"` | player |
| `votes_position_<period>` | vote | Where the player sits on one period's vote leaderboard. | `"3"` | player |
| `votes_streak_best` | vote | The player's longest voting streak. | `"12"` | player |
| `votes_streak_current` | vote | How many days in a row the player has voted. | `"5"` | player |
| `votes_top_<period>_<n>_name` | vote | The name of the player ranked nth on one period's vote leaderboard. | `"Steve"` | global |
| `votes_top_<period>_<n>_votes` | vote | The vote count of the player ranked nth on one period's leaderboard. | `"42"` | global |
| `warp_<warp>_cost` | warps | What one warp charges to use. | `"$10.00"` | global |
| `warp_<warp>_owner` | warps | Who created one warp. | `"Alex"` | global |
| `warp_<warp>_visits` | warps | How many times one warp has been used. | `"318"` | global |
| `warp_<warp>_world` | warps | The world one warp sits in. | `"world"` | global |
| `warp_<warp>_x` | warps | The block x of one warp. | `"0"` | global |
| `warp_<warp>_y` | warps | The block y of one warp. | `"72"` | global |
| `warp_<warp>_z` | warps | The block z of one warp. | `"0"` | global |
| `warps_count` | warps | How many warps the player may use. | `"4"` | player |
| `warps_list` | warps | The names of the warps the player may use, comma separated. | `"spawn, shop, arena, nether"` | player |
| `worlds_default` | worlds | The name of the default world. | `"world"` | global |
| `worlds_default_players` | worlds | How many players are in the default world. | `"12"` | global |
| `worlds_loaded_count` | worlds | How many worlds are loaded right now. | `"3"` | global |
| `worlds_managed_count` | worlds | How many worlds the plugin's registry holds. | `"5"` | global |
