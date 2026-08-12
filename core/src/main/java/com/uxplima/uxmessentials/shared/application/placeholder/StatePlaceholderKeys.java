package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/** What the player's own session looks like right now, plus the display surfaces they are looking at. */
final class StatePlaceholderKeys {

    private static final ModuleId PLAYERSTATE = ModuleId.of("playerstate");
    private static final ModuleId POSES = ModuleId.of("poses");
    private static final ModuleId SCOREBOARD = ModuleId.of("scoreboard");
    private static final ModuleId HOLOGRAMS = ModuleId.of("holograms");
    private static final ModuleId CUSTOMMENUS = ModuleId.of("custommenus");

    private StatePlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return Stream.of(playerstate(), poses(), displays(), menus())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PlaceholderSpec> playerstate() {
        return List.of(
                PlaceholderSpec.of(
                        "playerstate_gamemode", "The player's game mode.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_fly",
                        "Whether the player may fly (yes/no).",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_flying",
                        "Whether the player is flying right now (yes/no).",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_god",
                        "Whether the player takes no damage (yes/no).",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_speed",
                        "The speed that applies to how the player is moving: fly speed while flying, walk speed otherwise.",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_walk_speed", "The player's walk speed.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_fly_speed", "The player's fly speed.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_health", "The player's current health.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_max_health",
                        "The player's maximum health.",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_food", "The player's food level.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_level", "The player's experience level.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_xp",
                        "How far the player is through the current experience level, from 0 to 1.",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_world", "The world the player is in.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of("playerstate_x", "The player's block x.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of("playerstate_y", "The player's block y.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of("playerstate_z", "The player's block z.", PlaceholderScope.SESSION, PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_biome",
                        "The biome the player is standing in.",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_playtime",
                        "How long the player has played, in whole hours.",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE),
                PlaceholderSpec.of(
                        "playerstate_playtime_formatted",
                        "How long the player has played, in the compact 1d2h form.",
                        PlaceholderScope.SESSION,
                        PLAYERSTATE));
    }

    private static List<PlaceholderSpec> poses() {
        return List.of(
                PlaceholderSpec.of(
                        "poses_sitting", "Whether the player is sitting (yes/no).", PlaceholderScope.SESSION, POSES),
                PlaceholderSpec.of(
                        "poses_posing",
                        "Whether the player holds a free pose: lay, bellyflop or spin (yes/no).",
                        PlaceholderScope.SESSION,
                        POSES),
                PlaceholderSpec.of(
                        "poses_pose",
                        "The pose the player holds: sit, lay, bellyflop, spin or none.",
                        PlaceholderScope.SESSION,
                        POSES),
                PlaceholderSpec.of(
                        "poses_toggle",
                        "Whether the player lets others sit on them: allow or refuse.",
                        PlaceholderScope.SESSION,
                        POSES));
    }

    private static List<PlaceholderSpec> displays() {
        return List.of(
                PlaceholderSpec.of(
                        "scoreboard_visible",
                        "Whether the player has the sidebar showing (yes/no).",
                        PlaceholderScope.SESSION,
                        SCOREBOARD),
                PlaceholderSpec.of(
                        "holograms_count", "How many holograms are placed.", PlaceholderScope.GLOBAL, HOLOGRAMS));
    }

    private static List<PlaceholderSpec> menus() {
        return List.of(
                PlaceholderSpec.of(
                        "menu_is_in_menu",
                        "Whether the player has a plugin menu open (yes/no).",
                        PlaceholderScope.SESSION,
                        CUSTOMMENUS),
                PlaceholderSpec.of(
                        "menu_opened",
                        "The id of the menu the player has open.",
                        PlaceholderScope.SESSION,
                        CUSTOMMENUS),
                PlaceholderSpec.of(
                        "menu_last",
                        "The id of the last menu the player opened, which survives the menu closing.",
                        PlaceholderScope.SESSION,
                        CUSTOMMENUS),
                PlaceholderSpec.of(
                        "menu_page",
                        "The page the open menu is showing, counting from 1.",
                        PlaceholderScope.SESSION,
                        CUSTOMMENUS),
                PlaceholderSpec.of(
                        "menu_rows", "How many rows the open menu has.", PlaceholderScope.SESSION, CUSTOMMENUS),
                PlaceholderSpec.family(
                        "menu_argument_<name>",
                        "The value of one named argument the open menu was called with.",
                        PlaceholderScope.SESSION,
                        CUSTOMMENUS));
    }
}
