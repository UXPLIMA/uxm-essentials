# Placeholders: player and server facts

<!-- Generated from the code catalogues by ./gradlew :core:generateReference. Edit the catalogue, not this page. -->

Registered when PlaceholderAPI is installed, as `%uxm_essentials_<key>%`. A `player` key reads the player it is asked about, offline too; a `session` key reads live state and answers the dash while the player is away; a `global` one is the same for everybody; a `relational` one is typed `%rel_uxm_essentials_<key>%` and answers about the player a line is drawn for, as the viewer sees them. `<n>` and other angle brackets are filled in by you, for example `%uxm_essentials_homes_1_world%`. The example shows the shape of an answer. The keys each module answers are in [placeholders.md](placeholders.md). The leaderboard keys are in [reference.md](reference.md#boards). The earlier `%uxmessentials_<key>%` form still answers in this release and logs a warning the first time it is read; [upgrading.md](upgrading.md) says what to change. `/uxmess placeholders export` writes the same catalogue from the running build to a file in the plugin folder.

| Key | Module | Value | Example | Scope |
|---|---|---|---|---|
| `cooldown_<label>` | shared | How long the player still waits on one cooldown label, in whole seconds; 0 when it is open. | `"90"` | player |
| `cooldown_<label>_formatted` | shared | The same wait in the compact 1h2m3s form; 0s when it is open. | `"1m30s"` | player |
| `cooldown_active_<label>` | shared | Whether a cooldown is running on that label at all (yes/no). | `"yes"` | player |
| `distance` | shared | How far apart the two players stand, in blocks; the dash across worlds or when either is offline. | `"48"` | relational |
| `format_compact_<n>` | shared | The number shortened to k, M, B or T, so 1234567 reads 1.23M. | `"1.23M"` | global |
| `format_number_<n>` | shared | The number with its thousands grouped, so 1234567 reads 1,234,567. | `"1,234,567"` | global |
| `format_time_<n>` | shared | A count of seconds spelled in the compact 1h2m3s form. | `"1h2m3s"` | global |
| `hand_amount` | shared | How many are in the main-hand stack. | `"1"` | session |
| `hand_damage` | shared | How much durability the main-hand item has spent. | `"120"` | session |
| `hand_durability` | shared | How much durability the main-hand item has left. | `"1441"` | session |
| `hand_durability_max` | shared | The main-hand item's durability ceiling. | `"1561"` | session |
| `hand_enchants` | shared | The enchantments on the main-hand item, each as name and level, comma separated. | `"sharpness 5, unbreaking 3"` | session |
| `hand_enchants_count` | shared | How many enchantments the main-hand item carries. | `"2"` | session |
| `hand_lore` | shared | The lore of the main-hand item, joined into one line. | `"Forged in the nether"` | session |
| `hand_model` | shared | The custom model data on the main-hand item. | `"1001"` | session |
| `hand_name` | shared | The display name of the item in the main hand, or its material when it carries none. | `"Excalibur"` | session |
| `hand_type` | shared | The material of the item in the main hand. | `"diamond_sword"` | session |
| `itemcount_<material>` | shared | How many of one material the player carries, counting every stack in their inventory. | `"64"` | session |
| `module_<id>` | shared | Whether that feature module is switched on (yes/no); the id is the folder name under modules/, as homes or economy. Reading it lets a scoreboard hide the lines a disabled module would answer with a dash. | `"yes"` | global |
| `offhand_amount` | shared | How many are in the off-hand stack. | `"1"` | session |
| `offhand_damage` | shared | How much durability the off-hand item has spent. | `"12"` | session |
| `offhand_durability` | shared | How much durability the off-hand item has left. | `"324"` | session |
| `offhand_durability_max` | shared | The off-hand item's durability ceiling. | `"336"` | session |
| `offhand_enchants` | shared | The enchantments on the off-hand item, each as name and level, comma separated. | `"unbreaking 3"` | session |
| `offhand_enchants_count` | shared | How many enchantments the off-hand item carries. | `"1"` | session |
| `offhand_lore` | shared | The lore of the off-hand item, joined into one line. | `"Guards the gate"` | session |
| `offhand_model` | shared | The custom model data on the off-hand item. | `"0"` | session |
| `offhand_name` | shared | The display name of the item in the off hand, or its material when it carries none. | `"shield"` | session |
| `offhand_type` | shared | The material of the item in the off hand. | `"shield"` | session |
| `p_<name>_<key>` | shared | Any other key on this page, answered about the named player instead of the one reading it; the name is resolved the same way a command resolves it, so it works offline and on a cracked server. | `"1250.5"` | player |
| `player_absorption` | shared | The absorption hearts on top of the player's health. | `"4.0"` | session |
| `player_air` | shared | The air the player has left underwater, in ticks. | `"300"` | session |
| `player_air_max` | shared | The air a full breath holds, in ticks. | `"300"` | session |
| `player_armor` | shared | The armour points the player's equipment is worth. | `"15"` | session |
| `player_banned` | shared | Whether the server's own ban list holds the account (yes/no); the moderation keys read the plugin's. | `"no"` | player |
| `player_bed` | shared | The player's respawn point, as world x y z. | `"world 120 64 -32"` | session |
| `player_biome` | shared | The biome the player stands in. | `"plains"` | session |
| `player_block_below` | shared | The block the player stands on. | `"grass_block"` | session |
| `player_burning` | shared | Whether the player is on fire (yes/no). | `"no"` | session |
| `player_can_fly` | shared | Whether the player is allowed to fly (yes/no). | `"no"` | session |
| `player_compass` | shared | Where the player's compass points, as world x y z. | `"world 0 64 0"` | session |
| `player_deaths` | shared | How many times the account has died. | `"9"` | player |
| `player_direction` | shared | The compass direction the player faces, as north or south_west. | `"west"` | session |
| `player_display_name` | shared | The name other players see, as plain text. | `"Steve"` | session |
| `player_exp_percent` | shared | How far through the current experience level the player is, as a whole percentage. | `"45"` | session |
| `player_exp_progress` | shared | How far through the current experience level the player is, from 0 to 1. | `"0.45"` | session |
| `player_exp_to_next` | shared | How many experience points remain before the next level. | `"62"` | session |
| `player_exp_total` | shared | The experience points the player holds in total. | `"1395"` | session |
| `player_first_join` | shared | When the account first joined, as a date and time. | `"2024-03-01 18:42"` | player |
| `player_first_join_date` | shared | The same first-join stamp, under the spelling a config may prefer. | `"2024-03-01 18:42"` | player |
| `player_fly_speed` | shared | The player's flight speed, from 0 to 1. | `"0.1"` | session |
| `player_flying` | shared | Whether the player is flying right now (yes/no). | `"no"` | session |
| `player_food` | shared | The player's hunger bar, from 0 to 20. | `"17"` | session |
| `player_gamemode` | shared | The game mode the player is in. | `"survival"` | session |
| `player_has_bed` | shared | Whether the player has a respawn point set (yes/no). | `"yes"` | session |
| `player_health` | shared | The player's health, in half-hearts. | `"18.0"` | session |
| `player_health_max` | shared | The health ceiling the player's health is measured against. | `"20.0"` | session |
| `player_health_percent` | shared | The player's health as a percentage of their maximum. | `"90"` | session |
| `player_health_rounded` | shared | The player's health rounded to a whole number. | `"18"` | session |
| `player_ip` | shared | The address the player is connected from, without the port. | `"203.0.113.7"` | session |
| `player_kills` | shared | How many players the account has killed. | `"4"` | player |
| `player_language` | shared | The language the player reads in, as tr or pt_br: the forced one, else their /lang choice, else their client's. | `"tr"` | session |
| `player_last_seen` | shared | When the account was last connected; empty while it is connected now. | `"2024-06-12 21:05"` | player |
| `player_last_seen_date` | shared | The same last-seen stamp, under the spelling a config may prefer. | `"2024-06-12 21:05"` | player |
| `player_level` | shared | The player's experience level. | `"30"` | session |
| `player_light` | shared | The light level where the player stands. | `"15"` | session |
| `player_locale` | shared | The client's language, as en_us. | `"en_us"` | session |
| `player_location` | shared | Where the player stands, as world x y z. | `"world 128 64 -240"` | session |
| `player_mob_kills` | shared | How many mobs the account has killed. | `"512"` | player |
| `player_name` | shared | The player's account name. | `"Steve"` | player |
| `player_op` | shared | Whether the server treats the player as an operator (yes/no). | `"no"` | session |
| `player_ping` | shared | The player's round-trip time, in milliseconds. | `"42"` | session |
| `player_pitch` | shared | How far up or down the player looks, in degrees. | `"-12.5"` | session |
| `player_playtime` | shared | How long the account has played, in whole hours. | `"135"` | player |
| `player_playtime_days` | shared | How long the account has played, in whole days. | `"5"` | player |
| `player_playtime_formatted` | shared | How long the account has played, in the compact 1d2h form. | `"5d15h"` | player |
| `player_playtime_hours` | shared | How long the account has played, in whole hours. | `"135"` | player |
| `player_playtime_minutes` | shared | How long the account has played, in whole minutes. | `"8122"` | player |
| `player_playtime_seconds` | shared | How long the account has played, in whole seconds. | `"487320"` | player |
| `player_saturation` | shared | The hidden saturation behind the hunger bar. | `"2.4"` | session |
| `player_sneaking` | shared | Whether the player is crouching (yes/no). | `"no"` | session |
| `player_sprinting` | shared | Whether the player is running (yes/no). | `"yes"` | session |
| `player_uuid` | shared | The player's account id. | `"069a79f4-44e9-4726-a5be-fca90e38aaf5"` | player |
| `player_walk_speed` | shared | The player's walking speed, from 0 to 1. | `"0.2"` | session |
| `player_world` | shared | The world the player stands in. | `"world"` | session |
| `player_world_environment` | shared | The environment of the player's world: normal, nether or the_end. | `"normal"` | session |
| `player_world_time` | shared | The time of day in the player's world, in ticks. | `"6000"` | session |
| `player_world_time_formatted` | shared | The time of day in the player's world as a 24-hour clock, where tick 0 is 06:00. | `"12:00"` | session |
| `player_world_weather` | shared | The sky in the player's world: clear, rain or thunder. | `"clear"` | session |
| `player_x` | shared | The player's x coordinate, as a whole block. | `"128"` | session |
| `player_x_exact` | shared | The player's x coordinate, to two decimals. | `"128.53"` | session |
| `player_y` | shared | The player's y coordinate, as a whole block. | `"64"` | session |
| `player_y_exact` | shared | The player's y coordinate, to two decimals. | `"64.00"` | session |
| `player_yaw` | shared | The direction the player faces, in degrees. | `"90.0"` | session |
| `player_z` | shared | The player's z coordinate, as a whole block. | `"-240"` | session |
| `player_z_exact` | shared | The player's z coordinate, to two decimals. | `"-239.70"` | session |
| `progressbar_<now>_<total>` | shared | A twenty-character bar filled to now out of total; append a third segment to set the width. | `"██████████░░░░░░░░░░"` | global |
| `same_world` | shared | Whether the two players stand in the same world (yes/no). | `"yes"` | relational |
| `server_date` | shared | The calendar day on the machine the server runs on, as yyyy-mm-dd. | `"2024-06-12"` | global |
| `server_max_players` | shared | The server's player slot count. | `"100"` | global |
| `server_motd` | shared | The message of the day, as plain text. | `"Welcome to the server"` | global |
| `server_name` | shared | The server's own name. | `"lobby"` | global |
| `server_online` | shared | How many players are connected. | `"24"` | global |
| `server_ram_free` | shared | Heap still free, in whole megabytes. | `"5120"` | global |
| `server_ram_max` | shared | Heap ceiling, in whole megabytes. | `"8192"` | global |
| `server_ram_used` | shared | Heap in use, in whole megabytes. | `"3072"` | global |
| `server_time` | shared | The wall-clock time on the machine the server runs on, as a 24-hour clock. | `"21:05"` | global |
| `server_tps` | shared | Ticks per second over the last minute. | `"19.98"` | global |
| `server_tps_15m` | shared | Ticks per second over the last fifteen minutes. | `"19.97"` | global |
| `server_tps_5m` | shared | Ticks per second over the last five minutes. | `"19.95"` | global |
| `server_tps_colored` | shared | Ticks per second over the last minute, wrapped in a green, yellow or red MiniMessage colour. | `"<green>19.98</green>"` | global |
| `server_uptime` | shared | How long the server has been up, in whole minutes. | `"1440"` | global |
| `server_uptime_formatted` | shared | How long the server has been up, in the compact 1h30m form. | `"1d"` | global |
| `server_version` | shared | The Minecraft version the server runs. | `"26.2"` | global |
| `server_world_chunks_<world>` | shared | How many chunks are loaded in the named world. | `"961"` | global |
| `server_world_entities_<world>` | shared | How many entities are in the named world. | `"1834"` | global |
| `server_world_players_<world>` | shared | How many players are in one named world; the dash when no such world is loaded. | `"6"` | global |
| `server_world_time_<world>` | shared | The time of day in one named world, in ticks; the dash when no such world is loaded. | `"18000"` | global |
| `server_world_time_formatted_<world>` | shared | The same time as a 24-hour clock, where tick 0 is 06:00. | `"00:00"` | global |
| `server_world_weather_<world>` | shared | The sky over one named world: clear, rain or thunder. | `"rain"` | global |
| `server_worlds` | shared | How many worlds are loaded. | `"3"` | global |
| `stat_<statistic>` | shared | Any vanilla statistic by name, as stat_jump or stat_damage_dealt; a statistic that counts per block, item or entity takes it on the end, as stat_mine_block_diamond_ore or stat_kill_entity_zombie. | `"3120"` | player |
