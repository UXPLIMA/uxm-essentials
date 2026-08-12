package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The keys that describe what the world around a player is doing to them: the survival mechanics they can switch
 * on and off, the item verbs they carry a switch for, the NPCs they own, the region they are standing in, and
 * whether the security module is asking them to prove who they are.
 */
final class WorldMechanicsPlaceholderKeys {

    private static final ModuleId SURVIVAL = ModuleId.of("survival");
    private static final ModuleId ITEMWORLD = ModuleId.of("itemworld");
    private static final ModuleId NPC = ModuleId.of("npc");
    private static final ModuleId REGIONS = ModuleId.of("regions");
    private static final ModuleId SECURITY = ModuleId.of("security");
    private static final ModuleId VILLAGERS = ModuleId.of("villagers");
    private static final ModuleId SERVERTWEAKS = ModuleId.of("servertweaks");
    private static final ModuleId COMMANDCONTROL = ModuleId.of("commandcontrol");
    private static final ModuleId INVROLLBACK = ModuleId.of("invrollback");

    private WorldMechanicsPlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return Stream.of(
                        survival(),
                        itemworld(),
                        npc(),
                        regions(),
                        security(),
                        villagers(),
                        tweaks(),
                        commands(),
                        rollback())
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Each mechanic reads twice: the bare key is the player's own switch, and the {@code _enabled} spelling is
     * whether the server runs the mechanic at all.
     */
    private static List<PlaceholderSpec> survival() {
        return Stream.of(
                        mechanic("treefeller", "tree-feller", "fells a whole tree in one break"),
                        mechanic("veinminer", "veinminer", "follows an ore vein"),
                        mechanic("farmprotect", "farm protection", "stops trampling crops"),
                        mechanic("autopickup", "auto-pickup", "sends drops straight to the inventory"),
                        mechanic("autosmelt", "auto-smelt", "smelts what is mined"),
                        mechanic("autosell", "auto-sell", "sells what is mined"),
                        mechanic("autotool", "auto-tool", "swaps to the right tool"))
                .flatMap(List::stream)
                .toList();
    }

    private static List<PlaceholderSpec> mechanic(String key, String name, String what) {
        return List.of(
                PlaceholderSpec.of(
                        "survival_" + key,
                        "Whether the player has " + name + " switched on (yes/no), the mechanic that " + what + ".",
                        PlaceholderScope.PLAYER,
                        SURVIVAL),
                PlaceholderSpec.of(
                        "survival_" + key + "_enabled",
                        "Whether this server runs " + name + " at all (yes/no), the mechanic that " + what + ".",
                        PlaceholderScope.GLOBAL,
                        SURVIVAL));
    }

    private static List<PlaceholderSpec> itemworld() {
        return List.of(
                PlaceholderSpec.of(
                        "itemworld_powertool",
                        "The commands bound to the item in the player's hand, comma separated.",
                        PlaceholderScope.SESSION,
                        ITEMWORLD),
                PlaceholderSpec.of(
                        "itemworld_powertool_bound",
                        "Whether the held item runs anything on click (yes/no).",
                        PlaceholderScope.SESSION,
                        ITEMWORLD),
                PlaceholderSpec.of(
                        "itemworld_powertool_count",
                        "How many commands the held item is bound to.",
                        PlaceholderScope.SESSION,
                        ITEMWORLD),
                PlaceholderSpec.of(
                        "itemworld_powertool_enabled",
                        "Whether the player currently lets their powertool bindings fire (yes/no).",
                        PlaceholderScope.PLAYER,
                        ITEMWORLD),
                PlaceholderSpec.of(
                        "itemworld_unlimited",
                        "Whether the player is placing blocks without consuming them (yes/no).",
                        PlaceholderScope.PLAYER,
                        ITEMWORLD));
    }

    private static List<PlaceholderSpec> npc() {
        return List.of(
                PlaceholderSpec.of("npc_total", "How many NPCs the server holds.", PlaceholderScope.GLOBAL, NPC),
                PlaceholderSpec.of("npc_owned", "How many NPCs the player owns.", PlaceholderScope.PLAYER, NPC),
                PlaceholderSpec.of(
                        "npc_limit", "How many NPCs the player may own, or unlimited.", PlaceholderScope.PLAYER, NPC),
                PlaceholderSpec.of(
                        "npc_remaining",
                        "How many more NPCs the player may create, or unlimited.",
                        PlaceholderScope.PLAYER,
                        NPC));
    }

    private static List<PlaceholderSpec> regions() {
        return List.of(
                PlaceholderSpec.of(
                        "regions_available",
                        "Whether a region provider is reachable at all (yes/no).",
                        PlaceholderScope.GLOBAL,
                        REGIONS),
                PlaceholderSpec.of(
                        "regions_inside",
                        "Whether the player is standing in a protected region (yes/no).",
                        PlaceholderScope.SESSION,
                        REGIONS),
                PlaceholderSpec.of(
                        "regions_here",
                        "The region the player is standing in, highest priority first when they overlap.",
                        PlaceholderScope.SESSION,
                        REGIONS),
                PlaceholderSpec.of(
                        "regions_here_priority",
                        "That region's priority, which is what decides an overlap.",
                        PlaceholderScope.SESSION,
                        REGIONS),
                PlaceholderSpec.of(
                        "regions_here_owners",
                        "Who owns the region the player is standing in, comma separated.",
                        PlaceholderScope.SESSION,
                        REGIONS),
                PlaceholderSpec.of(
                        "regions_here_members",
                        "Who may build in the region the player is standing in, comma separated.",
                        PlaceholderScope.SESSION,
                        REGIONS),
                PlaceholderSpec.of(
                        "regions_count",
                        "How many regions cover the player at once.",
                        PlaceholderScope.SESSION,
                        REGIONS),
                PlaceholderSpec.of(
                        "regions_world_count",
                        "How many regions are defined in the world the player is in.",
                        PlaceholderScope.SESSION,
                        REGIONS));
    }

    private static List<PlaceholderSpec> security() {
        return List.of(
                PlaceholderSpec.of(
                        "security_verifying",
                        "Whether the player has an open verification challenge they have not answered (yes/no).",
                        PlaceholderScope.SESSION,
                        SECURITY),
                PlaceholderSpec.of(
                        "security_enforced",
                        "Whether the server asks players to verify on join at all (yes/no).",
                        PlaceholderScope.GLOBAL,
                        SECURITY));
    }

    private static List<PlaceholderSpec> villagers() {
        return List.of(
                PlaceholderSpec.of(
                        "villagers_following",
                        "How many villagers are walking after the player right now.",
                        PlaceholderScope.SESSION,
                        VILLAGERS),
                PlaceholderSpec.of(
                        "villagers_has_follower",
                        "Whether any villager is following the player (yes/no).",
                        PlaceholderScope.SESSION,
                        VILLAGERS));
    }

    private static List<PlaceholderSpec> tweaks() {
        return List.of(PlaceholderSpec.of(
                "servertweaks_brand",
                "The server brand reported to clients on the F3 screen, or a dash when the tweak is off.",
                PlaceholderScope.GLOBAL,
                SERVERTWEAKS));
    }

    private static List<PlaceholderSpec> commands() {
        return List.of(PlaceholderSpec.family(
                "commandcontrol_allowed_<command>",
                "Whether the player may run that command where they stand (yes/no), answered from the rules the "
                        + "gate uses.",
                PlaceholderScope.SESSION,
                COMMANDCONTROL));
    }

    /**
     * What this enable has captured, not what the table holds: the snapshots are staff-read history and a HUD
     * refresh must never become a query, so the keys answer from the capture listener's own session record.
     */
    private static List<PlaceholderSpec> rollback() {
        return List.of(
                PlaceholderSpec.of(
                        "invrollback_last_capture",
                        "How long ago this server last snapshotted the player's inventory, since the last restart.",
                        PlaceholderScope.SESSION,
                        INVROLLBACK),
                PlaceholderSpec.of(
                        "invrollback_last_cause",
                        "What caused that snapshot: death or logout.",
                        PlaceholderScope.SESSION,
                        INVROLLBACK),
                PlaceholderSpec.of(
                        "invrollback_captured",
                        "Whether any snapshot has been taken for the player since the last restart (yes/no).",
                        PlaceholderScope.SESSION,
                        INVROLLBACK));
    }
}
