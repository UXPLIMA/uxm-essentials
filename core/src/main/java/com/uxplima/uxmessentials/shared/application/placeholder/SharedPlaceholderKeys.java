package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;
import java.util.stream.Stream;

/** The server-wide keys the kernel answers, which hold a value whatever is enabled. */
final class SharedPlaceholderKeys {

    private SharedPlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return Stream.of(
                        modules(),
                        addressing(),
                        between(),
                        account(),
                        identity(),
                        session(),
                        vitals(),
                        place(),
                        statistics(),
                        held(),
                        waits(),
                        formatting(),
                        server())
                .flatMap(List::stream)
                .toList();
    }

    /** Which feature modules are switched on, so a template can hide a line rather than print a dash. */
    private static List<PlaceholderSpec> modules() {
        return List.of(PlaceholderSpec.sharedFamily(
                "module_<id>",
                "Whether that feature module is switched on (yes/no); the id is the one in modules.conf, as "
                        + "homes or economy. Reading it lets a scoreboard hide the lines a disabled module would "
                        + "answer with a dash.",
                PlaceholderScope.GLOBAL));
    }

    /** Reading a key about somebody other than the player the placeholder is being rendered for. */
    private static List<PlaceholderSpec> addressing() {
        return List.of(PlaceholderSpec.sharedFamily(
                "p_<name>_<key>",
                "Any other key on this page, answered about the named player instead of the one reading it; "
                        + "the name is resolved the same way a command resolves it, so it works offline and "
                        + "on a cracked server.",
                PlaceholderScope.PLAYER));
    }

    /** What holds between two players wherever they stand, which needs no feature module. */
    private static List<PlaceholderSpec> between() {
        return List.of(
                PlaceholderSpec.shared(
                        "same_world",
                        "Whether the two players stand in the same world (yes/no).",
                        PlaceholderScope.RELATIONAL),
                PlaceholderSpec.shared(
                        "distance",
                        "How far apart the two players stand, in blocks; the dash across worlds or when either is offline.",
                        PlaceholderScope.RELATIONAL));
    }

    /** What the server remembers about an account, which answers whether or not it is connected. */
    private static List<PlaceholderSpec> account() {
        return List.of(
                PlaceholderSpec.shared(
                        "player_first_join",
                        "When the account first joined, as a date and time.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_first_join_date",
                        "The same first-join stamp, under the spelling a config may prefer.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_last_seen",
                        "When the account was last connected; empty while it is connected now.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_last_seen_date",
                        "The same last-seen stamp, under the spelling a config may prefer.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_playtime", "How long the account has played, in whole hours.", PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_playtime_formatted",
                        "How long the account has played, in the compact 1d2h form.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_playtime_days",
                        "How long the account has played, in whole days.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_playtime_hours",
                        "How long the account has played, in whole hours.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_playtime_minutes",
                        "How long the account has played, in whole minutes.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_playtime_seconds",
                        "How long the account has played, in whole seconds.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_banned",
                        "Whether the server's own ban list holds the account (yes/no); the moderation keys read the plugin's.",
                        PlaceholderScope.PLAYER));
    }

    /** What the server holds about a connected player, which reads the dash once they disconnect. */
    private static List<PlaceholderSpec> session() {
        return List.of(
                PlaceholderSpec.shared(
                        "player_ping", "The player's round-trip time, in milliseconds.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_op",
                        "Whether the server treats the player as an operator (yes/no).",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_sneaking", "Whether the player is crouching (yes/no).", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_sprinting", "Whether the player is running (yes/no).", PlaceholderScope.SESSION),
                PlaceholderSpec.shared("player_world", "The world the player stands in.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_world_time",
                        "The time of day in the player's world, in ticks.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_world_time_formatted",
                        "The time of day in the player's world as a 24-hour clock, where tick 0 is 06:00.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_world_weather",
                        "The sky in the player's world: clear, rain or thunder.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared("player_level", "The player's experience level.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_exp_total",
                        "The experience points the player holds in total.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_exp_to_next",
                        "How many experience points remain before the next level.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_exp_progress",
                        "How far through the current experience level the player is, from 0 to 1.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_exp_percent",
                        "How far through the current experience level the player is, as a whole percentage.",
                        PlaceholderScope.SESSION));
    }

    /** Who the player is to the server, and how they are configured to move. */
    private static List<PlaceholderSpec> identity() {
        return List.of(
                PlaceholderSpec.shared("player_name", "The player's account name.", PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_display_name", "The name other players see, as plain text.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared("player_uuid", "The player's account id.", PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_ip",
                        "The address the player is connected from, without the port.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared("player_locale", "The client's language, as en_us.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared("player_gamemode", "The game mode the player is in.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_flying", "Whether the player is flying right now (yes/no).", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_can_fly", "Whether the player is allowed to fly (yes/no).", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_fly_speed", "The player's flight speed, from 0 to 1.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_walk_speed", "The player's walking speed, from 0 to 1.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_bed", "The player's respawn point, as world x y z.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_has_bed",
                        "Whether the player has a respawn point set (yes/no).",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_compass",
                        "Where the player's compass points, as world x y z.",
                        PlaceholderScope.SESSION));
    }

    /** How the player's body is doing right now. */
    private static List<PlaceholderSpec> vitals() {
        return List.of(
                PlaceholderSpec.shared(
                        "player_health", "The player's health, in half-hearts.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_health_rounded",
                        "The player's health rounded to a whole number.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_health_max",
                        "The health ceiling the player's health is measured against.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_health_percent",
                        "The player's health as a percentage of their maximum.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_food", "The player's hunger bar, from 0 to 20.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_saturation", "The hidden saturation behind the hunger bar.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_air", "The air the player has left underwater, in ticks.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_air_max", "The air a full breath holds, in ticks.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_armor", "The armour points the player's equipment is worth.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_absorption",
                        "The absorption hearts on top of the player's health.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_burning", "Whether the player is on fire (yes/no).", PlaceholderScope.SESSION));
    }

    /** Where the player stands, in more detail than the world name. */
    private static List<PlaceholderSpec> place() {
        return List.of(
                PlaceholderSpec.shared(
                        "player_x", "The player's x coordinate, as a whole block.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_y", "The player's y coordinate, as a whole block.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_z", "The player's z coordinate, as a whole block.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_x_exact", "The player's x coordinate, to two decimals.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_y_exact", "The player's y coordinate, to two decimals.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_z_exact", "The player's z coordinate, to two decimals.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_yaw", "The direction the player faces, in degrees.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_pitch", "How far up or down the player looks, in degrees.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_direction",
                        "The compass direction the player faces, as north or south_west.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared("player_biome", "The biome the player stands in.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_block_below", "The block the player stands on.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_light", "The light level where the player stands.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_world_environment",
                        "The environment of the player's world: normal, nether or the_end.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "player_location", "Where the player stands, as world x y z.", PlaceholderScope.SESSION));
    }

    /** The vanilla statistics, as one open family plus the three counters a scoreboard asks for by name. */
    private static List<PlaceholderSpec> statistics() {
        return List.of(
                PlaceholderSpec.sharedFamily(
                        "stat_<statistic>",
                        "Any vanilla statistic by name, as stat_jump or stat_damage_dealt; a statistic that "
                                + "counts per block, item or entity takes it on the end, as "
                                + "stat_mine_block_diamond_ore or stat_kill_entity_zombie.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_deaths", "How many times the account has died.", PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_kills", "How many players the account has killed.", PlaceholderScope.PLAYER),
                PlaceholderSpec.shared(
                        "player_mob_kills", "How many mobs the account has killed.", PlaceholderScope.PLAYER));
    }

    /** The item in each hand, for a HUD that mirrors what the player is holding. */
    private static List<PlaceholderSpec> held() {
        return List.of(
                PlaceholderSpec.shared(
                        "hand_type", "The material of the item in the main hand.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_name",
                        "The display name of the item in the main hand, or its material when it carries none.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared("hand_amount", "How many are in the main-hand stack.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_damage", "How much durability the main-hand item has spent.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_durability",
                        "How much durability the main-hand item has left.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_durability_max", "The main-hand item's durability ceiling.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_enchants",
                        "The enchantments on the main-hand item, each as name and level, comma separated.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_enchants_count",
                        "How many enchantments the main-hand item carries.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_lore", "The lore of the main-hand item, joined into one line.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "hand_model", "The custom model data on the main-hand item.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_type", "The material of the item in the off hand.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_name",
                        "The display name of the item in the off hand, or its material when it carries none.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_amount", "How many are in the off-hand stack.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_damage", "How much durability the off-hand item has spent.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_durability",
                        "How much durability the off-hand item has left.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_durability_max", "The off-hand item's durability ceiling.", PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_enchants",
                        "The enchantments on the off-hand item, each as name and level, comma separated.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_enchants_count",
                        "How many enchantments the off-hand item carries.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_lore",
                        "The lore of the off-hand item, joined into one line.",
                        PlaceholderScope.SESSION),
                PlaceholderSpec.shared(
                        "offhand_model", "The custom model data on the off-hand item.", PlaceholderScope.SESSION),
                PlaceholderSpec.sharedFamily(
                        "itemcount_<material>",
                        "How many of one material the player carries, counting every stack in their inventory.",
                        PlaceholderScope.SESSION));
    }

    /** The generic cooldown gate, keyed by whatever label an operator chose for it. */
    private static List<PlaceholderSpec> waits() {
        return List.of(
                PlaceholderSpec.sharedFamily(
                        "cooldown_<label>",
                        "How long the player still waits on one cooldown label, in whole seconds; 0 when it is open.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.sharedFamily(
                        "cooldown_<label>_formatted",
                        "The same wait in the compact 1h2m3s form; 0s when it is open.",
                        PlaceholderScope.PLAYER),
                PlaceholderSpec.sharedFamily(
                        "cooldown_active_<label>",
                        "Whether a cooldown is running on that label at all (yes/no).",
                        PlaceholderScope.PLAYER));
    }

    /** Helpers that carry their own input in the key and render it, rather than reading anything. */
    private static List<PlaceholderSpec> formatting() {
        return List.of(
                PlaceholderSpec.sharedFamily(
                        "format_number_<n>",
                        "The number with its thousands grouped, so 1234567 reads 1,234,567.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "format_compact_<n>",
                        "The number shortened to k, M, B or T, so 1234567 reads 1.23M.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "format_time_<n>",
                        "A count of seconds spelled in the compact 1h2m3s form.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "progressbar_<now>_<total>",
                        "A twenty-character bar filled to now out of total; append a third segment to set the width.",
                        PlaceholderScope.GLOBAL));
    }

    /** The server-wide metrics, which need no player at all. */
    private static List<PlaceholderSpec> server() {
        return List.of(
                PlaceholderSpec.shared("server_online", "How many players are connected.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared("server_name", "The server's own name.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_motd", "The message of the day, as plain text.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared("server_worlds", "How many worlds are loaded.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_time",
                        "The wall-clock time on the machine the server runs on, as a 24-hour clock.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_date",
                        "The calendar day on the machine the server runs on, as yyyy-mm-dd.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "server_world_entities_<world>",
                        "How many entities are in the named world.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "server_world_chunks_<world>",
                        "How many chunks are loaded in the named world.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_max_players", "The server's player slot count.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_version", "The Minecraft version the server runs.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_uptime", "How long the server has been up, in whole minutes.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_uptime_formatted",
                        "How long the server has been up, in the compact 1h30m form.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared("server_tps", "Ticks per second over the last minute.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_tps_5m", "Ticks per second over the last five minutes.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_tps_15m", "Ticks per second over the last fifteen minutes.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_tps_colored",
                        "Ticks per second over the last minute, wrapped in a green, yellow or red MiniMessage colour.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared("server_ram_used", "Heap in use, in whole megabytes.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared("server_ram_max", "Heap ceiling, in whole megabytes.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.shared(
                        "server_ram_free", "Heap still free, in whole megabytes.", PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "server_world_time_<world>",
                        "The time of day in one named world, in ticks; the dash when no such world is loaded.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "server_world_time_formatted_<world>",
                        "The same time as a 24-hour clock, where tick 0 is 06:00.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "server_world_weather_<world>",
                        "The sky over one named world: clear, rain or thunder.",
                        PlaceholderScope.GLOBAL),
                PlaceholderSpec.sharedFamily(
                        "server_world_players_<world>",
                        "How many players are in one named world; the dash when no such world is loaded.",
                        PlaceholderScope.GLOBAL));
    }
}
