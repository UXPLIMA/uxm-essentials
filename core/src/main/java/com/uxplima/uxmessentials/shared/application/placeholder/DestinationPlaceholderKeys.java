package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/** Everything about where a player can go: their homes, the server warps, player warps, worlds and teleports. */
final class DestinationPlaceholderKeys {

    private static final ModuleId HOMES = ModuleId.of("homes");
    private static final ModuleId WARPS = ModuleId.of("warps");
    private static final ModuleId PLAYERWARPS = ModuleId.of("playerwarps");
    private static final ModuleId WORLDS = ModuleId.of("worlds");
    private static final ModuleId TELEPORT = ModuleId.of("teleport");

    private DestinationPlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return Stream.of(homes(), warps(), playerwarps(), worlds(), teleport())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PlaceholderSpec> homes() {
        return List.of(
                PlaceholderSpec.of(
                        "homes_count", "How many homes the player has set.", "2", PlaceholderScope.PLAYER, HOMES),
                PlaceholderSpec.of(
                        "homes_limit",
                        "How many homes the player may keep; the infinity marker when the quota is unlimited.",
                        "5",
                        PlaceholderScope.PLAYER,
                        HOMES),
                PlaceholderSpec.of(
                        "homes_left", "How many more homes the player may set.", "3", PlaceholderScope.PLAYER, HOMES),
                PlaceholderSpec.of(
                        "homes_list",
                        "The player's home names, comma separated.",
                        "base, farm",
                        PlaceholderScope.PLAYER,
                        HOMES),
                PlaceholderSpec.family(
                        "homes_exists_<home>",
                        "Whether the player has a home by that name (yes/no).",
                        "yes",
                        PlaceholderScope.PLAYER,
                        HOMES),
                PlaceholderSpec.family(
                        "homes_<n>",
                        "The name of the player's nth home, counting from 1.",
                        "base",
                        PlaceholderScope.PLAYER,
                        HOMES),
                PlaceholderSpec.family(
                        "homes_<n>_world",
                        "The world the player's nth home sits in.",
                        "world",
                        PlaceholderScope.PLAYER,
                        HOMES),
                PlaceholderSpec.family(
                        "homes_<n>_x", "The block x of the player's nth home.", "128", PlaceholderScope.PLAYER, HOMES),
                PlaceholderSpec.family(
                        "homes_<n>_y", "The block y of the player's nth home.", "64", PlaceholderScope.PLAYER, HOMES),
                PlaceholderSpec.family(
                        "homes_<n>_z",
                        "The block z of the player's nth home.",
                        "-240",
                        PlaceholderScope.PLAYER,
                        HOMES));
    }

    private static List<PlaceholderSpec> warps() {
        return List.of(
                PlaceholderSpec.of(
                        "warps_count", "How many warps the player may use.", "4", PlaceholderScope.PLAYER, WARPS),
                PlaceholderSpec.of(
                        "warps_list",
                        "The names of the warps the player may use, comma separated.",
                        "spawn, shop, arena, nether",
                        PlaceholderScope.PLAYER,
                        WARPS),
                PlaceholderSpec.family(
                        "warp_<warp>_world", "The world one warp sits in.", "world", PlaceholderScope.GLOBAL, WARPS),
                PlaceholderSpec.family(
                        "warp_<warp>_x", "The block x of one warp.", "0", PlaceholderScope.GLOBAL, WARPS),
                PlaceholderSpec.family(
                        "warp_<warp>_y", "The block y of one warp.", "72", PlaceholderScope.GLOBAL, WARPS),
                PlaceholderSpec.family(
                        "warp_<warp>_z", "The block z of one warp.", "0", PlaceholderScope.GLOBAL, WARPS),
                PlaceholderSpec.family(
                        "warp_<warp>_visits",
                        "How many times one warp has been used.",
                        "318",
                        PlaceholderScope.GLOBAL,
                        WARPS),
                PlaceholderSpec.family(
                        "warp_<warp>_owner", "Who created one warp.", "Alex", PlaceholderScope.GLOBAL, WARPS),
                PlaceholderSpec.family(
                        "warp_<warp>_cost", "What one warp charges to use.", "$10.00", PlaceholderScope.GLOBAL, WARPS));
    }

    private static List<PlaceholderSpec> playerwarps() {
        return List.of(
                PlaceholderSpec.of(
                        "playerwarps_count",
                        "How many player warps the player owns.",
                        "1",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.of(
                        "playerwarps_limit",
                        "How many player warps the player may own; the infinity marker when unlimited.",
                        "3",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.of(
                        "playerwarps_left",
                        "How many more player warps the player may create.",
                        "2",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.of(
                        "playerwarps_list",
                        "The names of the player warps the player owns, comma separated.",
                        "myshop",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.family(
                        "playerwarp_<warp>_owner",
                        "Who owns one of the player's warps.",
                        "Steve",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.family(
                        "playerwarp_<warp>_world",
                        "The world one of the player's warps sits in.",
                        "world",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.family(
                        "playerwarp_<warp>_x",
                        "The block x of one of the player's warps.",
                        "512",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.family(
                        "playerwarp_<warp>_y",
                        "The block y of one of the player's warps.",
                        "70",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.family(
                        "playerwarp_<warp>_z",
                        "The block z of one of the player's warps.",
                        "-96",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS),
                PlaceholderSpec.family(
                        "playerwarp_<warp>_visits",
                        "How many times one of the player's warps has been used.",
                        "27",
                        PlaceholderScope.PLAYER,
                        PLAYERWARPS));
    }

    private static List<PlaceholderSpec> worlds() {
        return List.of(
                PlaceholderSpec.of(
                        "worlds_managed_count",
                        "How many worlds the plugin's registry holds.",
                        "5",
                        PlaceholderScope.GLOBAL,
                        WORLDS),
                PlaceholderSpec.of(
                        "worlds_loaded_count",
                        "How many worlds are loaded right now.",
                        "3",
                        PlaceholderScope.GLOBAL,
                        WORLDS),
                PlaceholderSpec.of(
                        "worlds_default", "The name of the default world.", "world", PlaceholderScope.GLOBAL, WORLDS),
                PlaceholderSpec.of(
                        "worlds_default_players",
                        "How many players are in the default world.",
                        "12",
                        PlaceholderScope.GLOBAL,
                        WORLDS));
    }

    private static List<PlaceholderSpec> teleport() {
        return List.of(
                PlaceholderSpec.of(
                        "teleport_cooldown_remaining",
                        "The wait left before the player may teleport again, in the compact 1m30s form.",
                        "1m30s",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_cooldown_remaining_formatted",
                        "The same remaining teleport cooldown, under the spelling a config may prefer.",
                        "1m30s",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_warmup_remaining",
                        "The stand-still countdown left on the teleport in progress.",
                        "3s",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_warmup_remaining_formatted",
                        "The same remaining warmup, under the spelling a config may prefer.",
                        "3s",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_back_available",
                        "Whether the player has a location to return to with /back (yes/no).",
                        "yes",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_back_world",
                        "The world the player's /back location sits in.",
                        "world_nether",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_back_x",
                        "The block x of the player's /back location.",
                        "-40",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_back_y",
                        "The block y of the player's /back location.",
                        "68",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_back_z",
                        "The block z of the player's /back location.",
                        "155",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_tpa_incoming",
                        "How many teleport requests are waiting for the player's answer.",
                        "1",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_tpa_pending",
                        "How many teleport requests the player has sent and not had answered.",
                        "0",
                        PlaceholderScope.SESSION,
                        TELEPORT),
                PlaceholderSpec.of(
                        "teleport_accepting",
                        "Whether the player accepts incoming teleport requests (yes/no).",
                        "yes",
                        PlaceholderScope.SESSION,
                        TELEPORT));
    }
}
