package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that decorate a world or a screen. Data, not logic: one row per node, read by
 * {@link PermissionCatalog} and through it by the server registration, the reference page and the in-game listing.
 */
final class ContentPermissions {

    private static final ModuleId NPC = ModuleId.of("npc");
    private static final ModuleId HOLOGRAMS = ModuleId.of("holograms");
    private static final ModuleId CUSTOMMENUS = ModuleId.of("custommenus");
    private static final ModuleId REGIONS = ModuleId.of("regions");
    private static final ModuleId VILLAGERS = ModuleId.of("villagers");
    private static final ModuleId SURVIVAL = ModuleId.of("survival");
    private static final ModuleId KITS = ModuleId.of("kits");
    private static final ModuleId SCOREBOARD = ModuleId.of("scoreboard");
    private static final ModuleId TABLIST = ModuleId.of("tablist");
    private static final ModuleId NAMETAGS = ModuleId.of("nametags");
    private static final ModuleId SERVERTWEAKS = ModuleId.of("servertweaks");

    private ContentPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(
                        npc(),
                        holograms(),
                        custommenus(),
                        regions(),
                        villagers(),
                        survival(),
                        kits(),
                        scoreboard(),
                        tablist(),
                        nametags(),
                        servertweaks())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PermissionSpec> npc() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.npc",
                        "Hot-reload / inspect the npc module (server-wide fake-player NPCs behind /npc).",
                        PermissionDefault.OP,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.admin",
                        "/npc to create, delete, list, move, re-skin, and bind the click command of fake-player NPCs.",
                        PermissionDefault.OP,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.create",
                        "/npc create and /npc copy. Held by default alongside the admin node; negate it to leave an operator editing only the NPCs that already exist.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.delete",
                        "/npc delete. Held by default alongside the admin node; the capability most worth negating for build staff.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.move",
                        "/npc movehere, moveto, teleport, center and fix: change where an NPC stands.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.appearance",
                        "/npc skin, skinslim, type, equip, glow, pose, scale and displayname: change how an NPC looks.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.action",
                        "/npc command and /npc action: change what an NPC runs when clicked.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.view",
                        "/npc list, info, nearby and help: read-only inspection.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.edit",
                        "Every remaining /npc setting (data, state, cooldown, mirror, collidable, showintab, view and turn distance).",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        "uxmessentials.npc.gui",
                        "/npc (no args) opens the NPC management GUI.",
                        PermissionDefault.OP,
                        NPC),
                PermissionSpec.family(
                        "uxmessentials.npc.limit.<n>",
                        "How many NPCs you may own; the largest tier held wins.",
                        PermissionDefault.OP,
                        PermissionShape.QUOTA,
                        NPC));
    }

    private static List<PermissionSpec> holograms() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.hologram.use",
                        "/hologram to create, edit, move, list and delete native-Display holograms.",
                        PermissionDefault.OP,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.create",
                        "/hologram create and copy. Held by default alongside the base node; negate it to leave an operator editing only the holograms that already exist.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.delete",
                        "/hologram delete. Held by default alongside the base node; the capability most worth negating for build staff.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.move",
                        "/hologram movehere, moveto, center, teleport and rotate: change where a hologram sits.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.appearance",
                        "/hologram billboard, background, glow, opacity, shadow, linewidth, viewrange, alignment, seethrough, growup, item, block, head and entity: change how a hologram looks.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.visibility",
                        "/hologram visibility, visibilitydistance, show, hide, blacklist and unblacklist: change who sees a hologram.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.action",
                        "/hologram action and clickcommand: change what a hologram runs when clicked.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.view",
                        "/hologram list, info and nearby: read-only inspection.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.hologram.edit",
                        "The line and page content of a hologram (addline, setline, insertline, removeline, page, leaderboard, linknpc, unlinknpc).",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.holograms.gui",
                        "/hologram (no args) opens the holograms management GUI.",
                        PermissionDefault.OP,
                        HOLOGRAMS),
                PermissionSpec.of(
                        "uxmessentials.module.holograms",
                        "Hot-reload / inspect the holograms module (native-Display holograms behind /hologram).",
                        PermissionDefault.OP,
                        HOLOGRAMS));
    }

    private static List<PermissionSpec> custommenus() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.menu.admin",
                        "/menu reload to re-read the menus/ folder from disk.",
                        PermissionDefault.OP,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        "uxmessentials.menu.editor",
                        "/menu editor (and the /uxmess gui hub entry) to create, duplicate, rename and delete custom menus in-game.",
                        PermissionDefault.OP,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        "uxmessentials.menu.open.others",
                        "/menu open <menu> <player>: open a custom menu for somebody else.",
                        PermissionDefault.OP,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        "uxmessentials.menu.use",
                        "/menu open <name> to open an operator custom menu, and /menu list to see the loaded menus.",
                        PermissionDefault.TRUE,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        "uxmessentials.module.custommenus",
                        "Hot-reload / inspect the custommenus module (operator custom menus behind /menu).",
                        PermissionDefault.OP,
                        CUSTOMMENUS));
    }

    private static List<PermissionSpec> regions() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.regions",
                        "Hot-reload / inspect the regions module (WorldGuard region management).",
                        PermissionDefault.OP,
                        REGIONS),
                PermissionSpec.of(
                        "uxmessentials.regions.admin",
                        "/regions priority <id> <value>: set a WorldGuard region priority.",
                        PermissionDefault.OP,
                        REGIONS),
                PermissionSpec.of(
                        "uxmessentials.regions.create",
                        "/regions create <id> (and /regions pos1|pos2): define a cuboid WorldGuard region.",
                        PermissionDefault.OP,
                        REGIONS),
                PermissionSpec.of(
                        "uxmessentials.regions.flags",
                        "/regions flags <id>: open the per-region flag editor GUI.",
                        PermissionDefault.OP,
                        REGIONS),
                PermissionSpec.of(
                        "uxmessentials.regions.list",
                        "/regions [world]: open the WorldGuard region-list GUI for a world.",
                        PermissionDefault.OP,
                        REGIONS),
                PermissionSpec.of(
                        "uxmessentials.regions.members",
                        "/regions members <id> and /regions addmember|addowner <id> <player>: manage a region roster.",
                        PermissionDefault.OP,
                        REGIONS));
    }

    private static List<PermissionSpec> villagers() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.villagers",
                        "Hot-reload / inspect the villagers module (villager trade management).",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        "uxmessentials.villagers.bucket",
                        "Sneak-right-click a villager to pick it up into a captured-villager item, and place it back later.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        "uxmessentials.villagers.follow",
                        "/villager follow: toggle whether the villager you are looking at pathfinds after you.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        "uxmessentials.villagers.leash",
                        "Right-click a villager with a lead to leash it, when leashing is enabled.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        "uxmessentials.villagers.manager",
                        "/villager manager: open and edit the trades of the villager you are looking at.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        "uxmessentials.villagers.protect",
                        "/villager protect: toggle whether the villager you are looking at is protected from death and despawn.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        "uxmessentials.villagers.trade",
                        "Open a villager's trade window directly on right-click, when click-to-trade is enabled.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        "uxmessentials.villagers.use",
                        "/villager: the root command's base node; its subcommands each gate further on their own node.",
                        PermissionDefault.OP,
                        VILLAGERS));
    }

    private static List<PermissionSpec> survival() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.survival",
                        "Hot-reload / inspect the survival module (opt-in gameplay mechanics).",
                        PermissionDefault.OP,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autopickup",
                        "Auto-pickup acts for you: mined drops go straight to your inventory.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autopickup.toggle",
                        "/autopickup: switch your personal auto-pickup on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autosell",
                        "Auto-sell acts for you: priced drops are sold for coin as you mine.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autosell.toggle",
                        "/autosell: switch your personal auto-sell on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autosmelt",
                        "Auto-smelt acts for you: ores are smelted as you mine them.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autosmelt.toggle",
                        "/autosmelt: switch your personal auto-smelt on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autotool",
                        "Auto-tool acts for you: the best tool swaps to hand as you mine.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.autotool.toggle",
                        "/autotool: switch your personal auto-tool on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.farmassist",
                        "Right-click a mature crop to harvest and replant it, spending one seed.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.farmprotect",
                        "Farmland protection acts for you: you will not trample your crops.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.farmprotect.toggle",
                        "/farmprotect: switch your personal farmland protection on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.gui",
                        "/survival: open your personal survival mechanics settings panel.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.treefeller",
                        "Tree-feller acts for you: break one log to fell the whole trunk.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.treefeller.toggle",
                        "/treefeller: switch your personal tree-feller on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.veinminer",
                        "Veinminer acts for you: break one block to mine the connected vein.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        "uxmessentials.survival.veinminer.toggle",
                        "/veinminer: switch your personal veinminer on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL));
    }

    private static List<PermissionSpec> kits() {
        return List.of(
                PermissionSpec.family(
                        "uxmessentials.kit.<kit>",
                        "Claim one kit.",
                        PermissionDefault.TRUE,
                        PermissionShape.LABEL,
                        KITS),
                PermissionSpec.family(
                        "uxmessentials.kit.cooldown.<kit>.<seconds>",
                        "The wait between claims of one kit, in seconds; the shortest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.kit.cooldown.bypass",
                        "Skip kit cooldowns and re-claim one-time kits.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.kit.edit",
                        "/kit create, /kit del, /kit editor to define, remove and edit kit contents.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.kit.others",
                        "/kit <name> <player> to give a kit to another player.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.kit.preview",
                        "/kit show <name> to preview a kit's contents without claiming it.",
                        PermissionDefault.TRUE,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.kit.reset",
                        "/kit reset <player> [kit] to clear a player's claim/cooldown stamps.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.kit.use",
                        "/kit <name> to claim a kit and /kit list to list the kits you may claim.",
                        PermissionDefault.TRUE,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.module.kits",
                        "Hot-reload / inspect the kits module (kit definitions, cooldowns and claims).",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        "uxmessentials.oversizedstacks",
                        "Receive kit items in stacks larger than the material normally allows.",
                        PermissionDefault.OP,
                        KITS));
    }

    private static List<PermissionSpec> scoreboard() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.scoreboard",
                        "Hot-reload / inspect the scoreboard module (per-player sidebar and tablist on uxmlib-hud).",
                        PermissionDefault.OP,
                        SCOREBOARD),
                PermissionSpec.of(
                        "uxmessentials.scoreboard.gui",
                        "/scoreboard gui (and the scoreboard entry on the /uxmess gui hub) to open the per-player scoreboard settings panel: the show/hide toggle.",
                        PermissionDefault.TRUE,
                        SCOREBOARD),
                PermissionSpec.of(
                        "uxmessentials.scoreboard.use",
                        "/scoreboard (alias /sb) to toggle whether you see the scoreboard display.",
                        PermissionDefault.TRUE,
                        SCOREBOARD));
    }

    private static List<PermissionSpec> tablist() {
        return List.of(PermissionSpec.of(
                "uxmessentials.module.tablist",
                "Hot-reload / inspect the tablist module (the player list header, footer and rows).",
                PermissionDefault.OP,
                TABLIST));
    }

    private static List<PermissionSpec> nametags() {
        return List.of(PermissionSpec.of(
                "uxmessentials.module.nametags",
                "Hot-reload / inspect the nametags module (the name shown above a player).",
                PermissionDefault.OP,
                NAMETAGS));
    }

    private static List<PermissionSpec> servertweaks() {
        return List.of(PermissionSpec.of(
                "uxmessentials.module.servertweaks",
                "Hot-reload / inspect the servertweaks module (the small server-behaviour switches).",
                PermissionDefault.OP,
                SERVERTWEAKS));
    }
}
