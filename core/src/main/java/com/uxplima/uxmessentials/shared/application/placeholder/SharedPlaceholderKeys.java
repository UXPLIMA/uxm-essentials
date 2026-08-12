package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;

/** The server-wide keys the kernel answers, which hold a value whatever is enabled. */
final class SharedPlaceholderKeys {

    private SharedPlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return List.of(
                PlaceholderSpec.shared("server_online", "How many players are connected.", PlaceholderScope.GLOBAL),
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
                        "server_world_players_<world>",
                        "How many players are in one named world; the dash when no such world is loaded.",
                        PlaceholderScope.GLOBAL));
    }
}
