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
    private static final ModuleId CUSTOMCOMMANDS = ModuleId.of("customcommands");
    private static final ModuleId GUARD = ModuleId.of("guard");
    private static final ModuleId VILLAGERS = ModuleId.of("villagers");
    private static final ModuleId SURVIVAL = ModuleId.of("survival");
    private static final ModuleId KITS = ModuleId.of("kits");
    private static final ModuleId SCOREBOARD = ModuleId.of("scoreboard");
    private static final ModuleId TABLIST = ModuleId.of("tablist");
    private static final ModuleId NAMETAGS = ModuleId.of("nametags");
    private static final ModuleId SERVERTWEAKS = ModuleId.of("servertweaks");
    private static final ModuleId SKIN = ModuleId.of("skin");
    private static final ModuleId BOSSBAR = ModuleId.of("bossbar");

    private ContentPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(
                        npc(),
                        holograms(),
                        custommenus(),
                        customcommands(),
                        guard(),
                        villagers(),
                        survival(),
                        kits(),
                        scoreboard(),
                        tablist(),
                        nametags(),
                        servertweaks(),
                        skin(),
                        bossbar())
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

    private static List<PermissionSpec> customcommands() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.customcommands",
                        "Hot-reload / inspect the customcommands module (operator-defined commands behind"
                                + " /customcmd).",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        "uxmessentials.customcommand.admin",
                        "/customcmd to list, inspect, reload, test and run operator-defined commands.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        "uxmessentials.customcommand.run.others",
                        "/customcmd run <id> <player>: run an operator-defined command for somebody else.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        "uxmessentials.customcommand.cooldown.bypass",
                        "Run an operator-defined command without waiting out its cooldown.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        "uxmessentials.customcommand.cost.bypass",
                        "Run an operator-defined command that costs money without paying for it.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS));
    }

    private static List<PermissionSpec> guard() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.guard",
                        "Hot-reload / inspect the guard module (region protection).",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.use",
                        "/guard and /guard help, actions: the base of every guard command.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.here",
                        "/guard here: the regions you stand in and your role there.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.member",
                        "/guard member <region> <player> <member|guest|clear>: owners and managers edit their own region's roster.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.why",
                        "/guard why <action>: explain the verdict for an action where you stand.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.define",
                        "/guard define, redefine, wand, pos1, pos2, point, clear, expand: draw and define regions.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.remove",
                        "/guard remove and restore: delete a region or bring a removed one back.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.info",
                        "/guard info <region>: a region's shape, rules, roles and behaviours.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.list",
                        "/guard list [world]: list the regions of a world.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.rule",
                        "/guard rule <region> <action> <target> <allow|deny|clear>: edit a region's rules.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.role",
                        "/guard role <region> <player> <role|clear>: give any role on any region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.set",
                        "/guard set <region> <behaviour> <value>: set a region behaviour.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.priority",
                        "/guard priority <region> <value>: set a region's priority.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.parent",
                        "/guard parent <region> <parent|clear>: inherit rules from another region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.family",
                        "/guard family <region> <name|clear>: group regions so their borders open to each other.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.tag",
                        "/guard tag <region> add|remove|bulk: tag regions and edit them by tag.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.template",
                        "/guard template <region> <template|clear>: apply a rule template.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.scope",
                        "/guard scope <region> <server|set:name>: choose which servers a region applies on.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.lock",
                        "/guard lock and unlock; edit and WorldEdit inside a locked region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.bypass",
                        "/guard bypass: toggle ignoring every guard rule.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.bypass.gamemode",
                        "Keep your own game mode in a region whose gamemode behaviour would change it.",
                        PermissionDefault.FALSE,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.show",
                        "/guard show and hide: draw region borders for yourself.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.near",
                        "/guard near [radius]: list regions around you.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.test",
                        "/guard test <player> <action> [region]: check a verdict for another player.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.inspect",
                        "/guard inspect: click a block to read what a player with no role may do there, and see why each of your own refusals happened.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.history",
                        "/guard history <region>: the change history of a region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.undo",
                        "/guard undo <history id>: undo one change from a region's history.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.log",
                        "/guard log [player]: recent denied actions.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.status",
                        "/guard status: engine counters and memory use.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.import",
                        "/guard import worldguard: import WorldGuard regions, with dry-run and undo.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.reload",
                        "/guard reload: reload the guard config and recompile every world.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.menu",
                        "See the region list in /guard menu; without it a region owner or manager gets the roles window of the region they stand in.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        "uxmessentials.guard.notify",
                        "Receive staff alerts when players repeatedly hit guard denials.",
                        PermissionDefault.OP,
                        GUARD));
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
                        "Claim a kit whose file sets permission = true.",
                        PermissionDefault.TRUE,
                        PermissionShape.LABEL,
                        KITS),
                PermissionSpec.family(
                        "uxmessentials.kit.cooldown.<group>",
                        "Use the wait a kit's permission-cooldowns block sets for <group>; the shortest wait held wins.",
                        PermissionDefault.FALSE,
                        PermissionShape.LABEL,
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

    private static List<PermissionSpec> bossbar() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.bossbar",
                        "Hot-reload / inspect the bossbar module (bars that stay up, timed bars and announced bars).",
                        PermissionDefault.OP,
                        BOSSBAR),
                PermissionSpec.of(
                        "uxmessentials.bossbar.use",
                        "/bossbars, /bossbars toggle|on|off to hide or show your own boss bars.",
                        PermissionDefault.TRUE,
                        BOSSBAR),
                PermissionSpec.of(
                        "uxmessentials.bossbar.others",
                        "/bossbars toggle|on|off <player> to hide or show another player's boss bars.",
                        PermissionDefault.OP,
                        BOSSBAR),
                PermissionSpec.of(
                        "uxmessentials.bossbar.announce",
                        "/bossbars announce <bar> <time> for everybody and /bossbars send <player> <bar> <time> for one player.",
                        PermissionDefault.OP,
                        BOSSBAR));
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

    private static List<PermissionSpec> skin() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.skin",
                        "Hot-reload / inspect the skin module (the skin a player wears).",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.use",
                        "/skin <name> and /skin clear: wear another account's skin, or go back to your own.",
                        PermissionDefault.TRUE,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.url",
                        "/skin url <link>: wear the skin drawn in an image published on the web.",
                        PermissionDefault.TRUE,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.file",
                        "/skin file <name>: wear one of the skins the operator dropped in the server's skin folder.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.update",
                        "/skin update: re-fetch your skin, for an account whose skin changed at the source.",
                        PermissionDefault.TRUE,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.other",
                        "/skin set <name> <player> and /skin clear <player>: dress or undress somebody else.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.drop",
                        "/skin drop <player>: delete a stored skin, so their next join derives one afresh.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.info",
                        "/skin info <player>: which skin somebody wears, from where, and when it was set.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        "uxmessentials.skin.purge",
                        "/skin purge <name>: forget a cached texture so the next lookup goes back to the source.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.family(
                        "uxmessentials.skin.name.<skin>",
                        "Wear one skin listed under limits.restricted-skins; a skin nobody restricted is open to everyone.",
                        PermissionDefault.TRUE,
                        PermissionShape.LABEL,
                        SKIN));
    }
}
