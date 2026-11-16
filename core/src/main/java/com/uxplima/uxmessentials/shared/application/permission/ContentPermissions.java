package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that decorate a world or a screen. Data, not logic: one row per node, read by
 * {@link PermissionCatalog} and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class ContentPermissions {

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

    public static final String MODULE_NPC = "uxmessentials.module.npc";
    public static final String NPC_ADMIN = "uxmessentials.npc.admin";
    public static final String NPC_CREATE = "uxmessentials.npc.create";
    public static final String NPC_DELETE = "uxmessentials.npc.delete";
    public static final String NPC_MOVE = "uxmessentials.npc.move";
    public static final String NPC_APPEARANCE = "uxmessentials.npc.appearance";
    public static final String NPC_ACTION = "uxmessentials.npc.action";
    public static final String NPC_VIEW = "uxmessentials.npc.view";
    public static final String NPC_SCOPE = "uxmessentials.npc.scope";
    public static final String NPC_EDIT = "uxmessentials.npc.edit";
    public static final String NPC_GUI = "uxmessentials.npc.gui";
    public static final String HOLOGRAM_USE = "uxmessentials.hologram.use";
    public static final String HOLOGRAM_CREATE = "uxmessentials.hologram.create";
    public static final String HOLOGRAM_DELETE = "uxmessentials.hologram.delete";
    public static final String HOLOGRAM_MOVE = "uxmessentials.hologram.move";
    public static final String HOLOGRAM_APPEARANCE = "uxmessentials.hologram.appearance";
    public static final String HOLOGRAM_VISIBILITY = "uxmessentials.hologram.visibility";
    public static final String HOLOGRAM_ACTION = "uxmessentials.hologram.action";
    public static final String HOLOGRAM_SCOPE = "uxmessentials.hologram.scope";
    public static final String HOLOGRAM_VIEW = "uxmessentials.hologram.view";
    public static final String HOLOGRAM_EDIT = "uxmessentials.hologram.edit";
    public static final String HOLOGRAMS_GUI = "uxmessentials.holograms.gui";
    public static final String MODULE_HOLOGRAMS = "uxmessentials.module.holograms";
    public static final String MENU_ADMIN = "uxmessentials.menu.admin";
    public static final String MENU_EDITOR = "uxmessentials.menu.editor";
    public static final String MENU_OPEN_OTHERS = "uxmessentials.menu.open.others";
    public static final String MENU_USE = "uxmessentials.menu.use";
    public static final String MODULE_CUSTOMMENUS = "uxmessentials.module.custommenus";
    public static final String MODULE_CUSTOMCOMMANDS = "uxmessentials.module.customcommands";
    public static final String CUSTOMCOMMAND_ADMIN = "uxmessentials.customcommand.admin";
    public static final String CUSTOMCOMMAND_RUN_OTHERS = "uxmessentials.customcommand.run.others";
    public static final String CUSTOMCOMMAND_COOLDOWN_BYPASS = "uxmessentials.bypass.cooldown.customcommand";
    public static final String CUSTOMCOMMAND_COST_BYPASS = "uxmessentials.bypass.cost.customcommand";
    public static final String MODULE_GUARD = "uxmessentials.module.guard";
    public static final String GUARD_USE = "uxmessentials.guard.use";
    public static final String GUARD_HERE = "uxmessentials.guard.here";
    public static final String GUARD_MEMBER = "uxmessentials.guard.member";
    public static final String GUARD_WHY = "uxmessentials.guard.why";
    public static final String GUARD_DEFINE = "uxmessentials.guard.define";
    public static final String GUARD_REMOVE = "uxmessentials.guard.remove";
    public static final String GUARD_INFO = "uxmessentials.guard.info";
    public static final String GUARD_LIST = "uxmessentials.guard.list";
    public static final String GUARD_RULE = "uxmessentials.guard.rule";
    public static final String GUARD_ROLE = "uxmessentials.guard.role";
    public static final String GUARD_SET = "uxmessentials.guard.set";
    public static final String GUARD_PRIORITY = "uxmessentials.guard.priority";
    public static final String GUARD_PARENT = "uxmessentials.guard.parent";
    public static final String GUARD_FAMILY = "uxmessentials.guard.family";
    public static final String GUARD_TAG = "uxmessentials.guard.tag";
    public static final String GUARD_TEMPLATE = "uxmessentials.guard.template";
    public static final String GUARD_SCOPE = "uxmessentials.guard.scope";
    public static final String GUARD_LOCK = "uxmessentials.guard.lock";
    public static final String GUARD_BYPASS = "uxmessentials.bypass.guard";
    public static final String GUARD_BYPASS_GAMEMODE = "uxmessentials.bypass.gamemode.guard";
    public static final String GUARD_SHOW = "uxmessentials.guard.show";
    public static final String GUARD_NEAR = "uxmessentials.guard.near";
    public static final String GUARD_TEST = "uxmessentials.guard.test";
    public static final String GUARD_INSPECT = "uxmessentials.guard.inspect";
    public static final String GUARD_HISTORY = "uxmessentials.guard.history";
    public static final String GUARD_UNDO = "uxmessentials.guard.undo";
    public static final String GUARD_LOG = "uxmessentials.guard.log";
    public static final String GUARD_STATUS = "uxmessentials.guard.status";
    public static final String GUARD_IMPORT = "uxmessentials.guard.import";
    public static final String GUARD_BACKUPS = "uxmessentials.guard.backups";
    public static final String GUARD_RELOAD = "uxmessentials.guard.reload";
    public static final String GUARD_MENU = "uxmessentials.guard.menu";
    public static final String GUARD_NOTIFY = "uxmessentials.guard.notify";
    public static final String MODULE_VILLAGERS = "uxmessentials.module.villagers";
    public static final String VILLAGERS_BUCKET = "uxmessentials.villagers.bucket";
    public static final String VILLAGERS_FOLLOW = "uxmessentials.villagers.follow";
    public static final String VILLAGERS_LEASH = "uxmessentials.villagers.leash";
    public static final String VILLAGERS_MANAGER = "uxmessentials.villagers.manager";
    public static final String VILLAGERS_PROTECT = "uxmessentials.villagers.protect";
    public static final String VILLAGERS_TRADE = "uxmessentials.villagers.trade";
    public static final String VILLAGERS_USE = "uxmessentials.villagers.use";
    public static final String MODULE_SURVIVAL = "uxmessentials.module.survival";
    public static final String SURVIVAL_AUTOPICKUP = "uxmessentials.survival.autopickup";
    public static final String SURVIVAL_AUTOPICKUP_TOGGLE = "uxmessentials.survival.autopickup.toggle";
    public static final String SURVIVAL_AUTOSELL = "uxmessentials.survival.autosell";
    public static final String SURVIVAL_AUTOSELL_TOGGLE = "uxmessentials.survival.autosell.toggle";
    public static final String SURVIVAL_AUTOSMELT = "uxmessentials.survival.autosmelt";
    public static final String SURVIVAL_AUTOSMELT_TOGGLE = "uxmessentials.survival.autosmelt.toggle";
    public static final String SURVIVAL_AUTOTOOL = "uxmessentials.survival.autotool";
    public static final String SURVIVAL_AUTOTOOL_TOGGLE = "uxmessentials.survival.autotool.toggle";
    public static final String SURVIVAL_FARMASSIST = "uxmessentials.survival.farmassist";
    public static final String SURVIVAL_FARMPROTECT = "uxmessentials.survival.farmprotect";
    public static final String SURVIVAL_FARMPROTECT_TOGGLE = "uxmessentials.survival.farmprotect.toggle";
    public static final String SURVIVAL_GUI = "uxmessentials.survival.gui";
    public static final String SURVIVAL_TREEFELLER = "uxmessentials.survival.treefeller";
    public static final String SURVIVAL_TREEFELLER_TOGGLE = "uxmessentials.survival.treefeller.toggle";
    public static final String SURVIVAL_VEINMINER = "uxmessentials.survival.veinminer";
    public static final String SURVIVAL_VEINMINER_TOGGLE = "uxmessentials.survival.veinminer.toggle";
    public static final String KIT_COOLDOWN_BYPASS = "uxmessentials.bypass.cooldown.kit";
    public static final String KIT_EDIT = "uxmessentials.kit.edit";
    public static final String KIT_OTHERS = "uxmessentials.kit.others";
    public static final String KIT_PREVIEW = "uxmessentials.kit.preview";
    public static final String KIT_RESET = "uxmessentials.kit.reset";
    public static final String KIT_USE = "uxmessentials.kit.use";
    public static final String MODULE_KITS = "uxmessentials.module.kits";
    public static final String OVERSIZEDSTACKS = "uxmessentials.oversizedstacks";
    public static final String MODULE_SCOREBOARD = "uxmessentials.module.scoreboard";
    public static final String SCOREBOARD_GUI = "uxmessentials.scoreboard.gui";
    public static final String SCOREBOARD_USE = "uxmessentials.scoreboard.use";
    public static final String MODULE_BOSSBAR = "uxmessentials.module.bossbar";
    public static final String BOSSBAR_USE = "uxmessentials.bossbar.use";
    public static final String BOSSBAR_OTHERS = "uxmessentials.bossbar.others";
    public static final String BOSSBAR_ANNOUNCE = "uxmessentials.bossbar.announce";
    public static final String MODULE_TABLIST = "uxmessentials.module.tablist";
    public static final String MODULE_NAMETAGS = "uxmessentials.module.nametags";
    public static final String MODULE_SERVERTWEAKS = "uxmessentials.module.servertweaks";
    public static final String MODULE_SKIN = "uxmessentials.module.skin";
    public static final String SKIN_USE = "uxmessentials.skin.use";
    public static final String SKIN_URL = "uxmessentials.skin.url";
    public static final String SKIN_FILE = "uxmessentials.skin.file";
    public static final String SKIN_UPDATE = "uxmessentials.skin.update";
    public static final String SKIN_OTHER = "uxmessentials.skin.other";
    public static final String SKIN_DROP = "uxmessentials.skin.drop";
    public static final String SKIN_INFO = "uxmessentials.skin.info";
    public static final String SKIN_PURGE = "uxmessentials.skin.purge";

    /** The head of the NPC quota; the number held is appended to it. */
    public static final String NPC_LIMIT = "uxmessentials.npc.limit";

    private static final String KIT_PREFIX = "uxmessentials.kit.";
    private static final String KIT_COOLDOWN_PREFIX = "uxmessentials.kit.cooldown.";
    private static final String SKIN_NAME_PREFIX = "uxmessentials.skin.name.";
    private static final String CUSTOMCOMMAND_PREFIX = "uxmessentials.customcommand.";

    /** The node that lets a player claim {@code kit} when its file asks for a permission. */
    public static String kit(String kit) {
        return KIT_PREFIX + kit;
    }

    /** The node that hands a player the wait a kit sets for its permission-cooldown {@code group}. */
    public static String kitCooldown(String group) {
        return KIT_COOLDOWN_PREFIX + group;
    }

    /** The node a player needs to wear {@code skin} once the operator restricted it. */
    public static String skinName(String skin) {
        return SKIN_NAME_PREFIX + skin;
    }

    /**
     * The node the command wizard offers for a new operator-defined command. It is only a suggestion the operator
     * writes into that command's file, so the catalogue has no row for it.
     */
    public static String customCommand(String id) {
        return CUSTOMCOMMAND_PREFIX + id;
    }

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
                        MODULE_NPC,
                        "Hot-reload / inspect the npc module (server-wide fake-player NPCs behind /npc).",
                        PermissionDefault.OP,
                        NPC),
                PermissionSpec.of(
                        NPC_ADMIN,
                        "/npc to create, delete, list, move, re-skin, and bind the click command of fake-player NPCs.",
                        PermissionDefault.OP,
                        NPC),
                PermissionSpec.of(
                        NPC_CREATE,
                        "/npc create and /npc copy. Held by default alongside the admin node; negate it to leave an operator editing only the NPCs that already exist.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        NPC_DELETE,
                        "/npc delete. Held by default alongside the admin node; the capability most worth negating for build staff.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        NPC_MOVE,
                        "/npc movehere, moveto, teleport, center and fix: change where an NPC stands.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        NPC_APPEARANCE,
                        "/npc skin, skinslim, type, equip, glow, pose, scale and displayname: change how an NPC looks.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        NPC_ACTION,
                        "/npc command and /npc action: change what an NPC runs when clicked.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        NPC_VIEW,
                        "/npc list, info, nearby and help: read-only inspection.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        NPC_SCOPE,
                        "/npc scope: choose which servers of the network show an NPC.",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(
                        NPC_EDIT,
                        "Every remaining /npc setting (data, state, cooldown, mirror, collidable, showintab, view and turn distance).",
                        PermissionDefault.TRUE,
                        NPC),
                PermissionSpec.of(NPC_GUI, "/npc (no args) opens the NPC management GUI.", PermissionDefault.OP, NPC),
                PermissionSpec.family(
                        NPC_LIMIT + ".<n>",
                        "How many NPCs you may own; the largest tier held wins.",
                        PermissionDefault.OP,
                        PermissionShape.QUOTA,
                        NPC));
    }

    private static List<PermissionSpec> holograms() {
        return List.of(
                PermissionSpec.of(
                        HOLOGRAM_USE,
                        "/hologram to create, edit, move, list and delete native-Display holograms.",
                        PermissionDefault.OP,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_CREATE,
                        "/hologram create and copy. Held by default alongside the base node; negate it to leave an operator editing only the holograms that already exist.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_DELETE,
                        "/hologram delete. Held by default alongside the base node; the capability most worth negating for build staff.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_MOVE,
                        "/hologram movehere, moveto, center, teleport and rotate: change where a hologram sits.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_APPEARANCE,
                        "/hologram billboard, background, glow, opacity, shadow, linewidth, viewrange, alignment, seethrough, growup, item, block, head and entity: change how a hologram looks.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_VISIBILITY,
                        "/hologram visibility, visibilitydistance, show, hide, blacklist and unblacklist: change who sees a hologram.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_ACTION,
                        "/hologram action and clickcommand: change what a hologram runs when clicked.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_SCOPE,
                        "/hologram scope: choose which servers of the network show a hologram.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_VIEW,
                        "/hologram list, info and nearby: read-only inspection.",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAM_EDIT,
                        "The line and page content of a hologram (addline, setline, insertline, removeline, page, leaderboard, linknpc, unlinknpc).",
                        PermissionDefault.TRUE,
                        HOLOGRAMS),
                PermissionSpec.of(
                        HOLOGRAMS_GUI,
                        "/hologram (no args) opens the holograms management GUI.",
                        PermissionDefault.OP,
                        HOLOGRAMS),
                PermissionSpec.of(
                        MODULE_HOLOGRAMS,
                        "Hot-reload / inspect the holograms module (native-Display holograms behind /hologram).",
                        PermissionDefault.OP,
                        HOLOGRAMS));
    }

    private static List<PermissionSpec> custommenus() {
        return List.of(
                PermissionSpec.of(
                        MENU_ADMIN,
                        "/menu reload to re-read the menus/ folder from disk.",
                        PermissionDefault.OP,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        MENU_EDITOR,
                        "/menu editor (and the /uxmess gui hub entry) to create, duplicate, rename and delete custom menus in-game.",
                        PermissionDefault.OP,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        MENU_OPEN_OTHERS,
                        "/menu open <menu> <player>: open a custom menu for somebody else.",
                        PermissionDefault.OP,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        MENU_USE,
                        "/menu open <name> to open an operator custom menu, and /menu list to see the loaded menus.",
                        PermissionDefault.TRUE,
                        CUSTOMMENUS),
                PermissionSpec.of(
                        MODULE_CUSTOMMENUS,
                        "Hot-reload / inspect the custommenus module (operator custom menus behind /menu).",
                        PermissionDefault.OP,
                        CUSTOMMENUS));
    }

    private static List<PermissionSpec> customcommands() {
        return List.of(
                PermissionSpec.of(
                        MODULE_CUSTOMCOMMANDS,
                        "Hot-reload / inspect the customcommands module (operator-defined commands behind"
                                + " /customcmd).",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        CUSTOMCOMMAND_ADMIN,
                        "/customcmd to list, inspect, reload, test and run operator-defined commands.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        CUSTOMCOMMAND_RUN_OTHERS,
                        "/customcmd run <id> <player>: run an operator-defined command for somebody else.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.family(
                        SharedPermissions.featureWarmup("customcommand") + ".<seconds>",
                        "The stand-still countdown before an operator-defined command runs, in seconds; the shortest tier held wins and 0 removes it.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        CUSTOMCOMMAND_COOLDOWN_BYPASS,
                        "Run an operator-defined command without waiting out its cooldown.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS),
                PermissionSpec.of(
                        CUSTOMCOMMAND_COST_BYPASS,
                        "Run an operator-defined command that costs money without paying for it.",
                        PermissionDefault.OP,
                        CUSTOMCOMMANDS));
    }

    private static List<PermissionSpec> guard() {
        return List.of(
                PermissionSpec.of(
                        MODULE_GUARD,
                        "Hot-reload / inspect the guard module (region protection).",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_USE,
                        "/guard and /guard help, actions: the base of every guard command.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        GUARD_HERE,
                        "/guard here: the regions you stand in and your role there.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        GUARD_MEMBER,
                        "/guard member <region> <player> <member|guest|clear>: owners and managers edit their own region's roster.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        GUARD_WHY,
                        "/guard why <action>: explain the verdict for an action where you stand.",
                        PermissionDefault.TRUE,
                        GUARD),
                PermissionSpec.of(
                        GUARD_DEFINE,
                        "/guard define, redefine, wand, pos1, pos2, point, clear, expand: draw and define regions.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_REMOVE,
                        "/guard remove and restore: delete a region or bring a removed one back.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_INFO,
                        "/guard info <region>: a region's shape, rules, roles and behaviours.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_LIST, "/guard list [world]: list the regions of a world.", PermissionDefault.OP, GUARD),
                PermissionSpec.of(
                        GUARD_RULE,
                        "/guard rule <region> <action> <target> <allow|deny|clear>: edit a region's rules.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_ROLE,
                        "/guard role <region> <player> <role|clear>: give any role on any region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_SET,
                        "/guard set <region> <behaviour> <value>: set a region behaviour.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_PRIORITY,
                        "/guard priority <region> <value>: set a region's priority.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_PARENT,
                        "/guard parent <region> <parent|clear>: inherit rules from another region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_FAMILY,
                        "/guard family <region> <name|clear>: group regions so their borders open to each other.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_TAG,
                        "/guard tag <region> add|remove|bulk: tag regions and edit them by tag.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_TEMPLATE,
                        "/guard template <region> <template|clear>: apply a rule template.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_SCOPE,
                        "/guard scope <region> <server|set:name>: choose which servers a region applies on.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_LOCK,
                        "/guard lock and unlock; edit and WorldEdit inside a locked region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_BYPASS, "/guard bypass: toggle ignoring every guard rule.", PermissionDefault.OP, GUARD),
                PermissionSpec.of(
                        GUARD_BYPASS_GAMEMODE,
                        "Keep your own game mode in a region whose gamemode behaviour would change it.",
                        PermissionDefault.FALSE,
                        GUARD),
                PermissionSpec.of(
                        GUARD_SHOW,
                        "/guard show and hide: draw region borders for yourself.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_NEAR, "/guard near [radius]: list regions around you.", PermissionDefault.OP, GUARD),
                PermissionSpec.of(
                        GUARD_TEST,
                        "/guard test <player> <action> [region]: check a verdict for another player.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_INSPECT,
                        "/guard inspect: click a block to read what a player with no role may do there, and see why each of your own refusals happened.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_HISTORY,
                        "/guard history <region>: the change history of a region.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_UNDO,
                        "/guard undo <history id>: undo one change from a region's history.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_LOG, "/guard log [player]: recent denied actions.", PermissionDefault.OP, GUARD),
                PermissionSpec.of(
                        GUARD_STATUS, "/guard status: engine counters and memory use.", PermissionDefault.OP, GUARD),
                PermissionSpec.of(
                        GUARD_IMPORT,
                        "/guard import worldguard: import WorldGuard regions, with dry-run and undo.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_BACKUPS,
                        "/guard backups [restore <file>]: list the region backups, or put a backup's regions back.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_RELOAD,
                        "/guard reload: reload the guard config and recompile every world.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_MENU,
                        "See the region list in /guard menu; without it a region owner or manager gets the roles window of the region they stand in.",
                        PermissionDefault.OP,
                        GUARD),
                PermissionSpec.of(
                        GUARD_NOTIFY,
                        "Receive staff alerts when players repeatedly hit guard denials.",
                        PermissionDefault.OP,
                        GUARD));
    }

    private static List<PermissionSpec> villagers() {
        return List.of(
                PermissionSpec.of(
                        MODULE_VILLAGERS,
                        "Hot-reload / inspect the villagers module (villager trade management).",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        VILLAGERS_BUCKET,
                        "Sneak-right-click a villager to pick it up into a captured-villager item, and place it back later.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        VILLAGERS_FOLLOW,
                        "/villager follow: toggle whether the villager you are looking at pathfinds after you.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        VILLAGERS_LEASH,
                        "Right-click a villager with a lead to leash it, when leashing is enabled.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        VILLAGERS_MANAGER,
                        "/villager manager (and the villagers entry on the /uxmess gui hub): open and edit the trades of the villager you are looking at.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        VILLAGERS_PROTECT,
                        "/villager protect: toggle whether the villager you are looking at is protected from death and despawn.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        VILLAGERS_TRADE,
                        "Open a villager's trade window directly on right-click, when click-to-trade is enabled.",
                        PermissionDefault.OP,
                        VILLAGERS),
                PermissionSpec.of(
                        VILLAGERS_USE,
                        "/villager: the root command's base node; its subcommands each gate further on their own node.",
                        PermissionDefault.OP,
                        VILLAGERS));
    }

    private static List<PermissionSpec> survival() {
        return List.of(
                PermissionSpec.of(
                        MODULE_SURVIVAL,
                        "Hot-reload / inspect the survival module (opt-in gameplay mechanics).",
                        PermissionDefault.OP,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOPICKUP,
                        "Auto-pickup acts for you: mined drops go straight to your inventory.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOPICKUP_TOGGLE,
                        "/autopickup: switch your personal auto-pickup on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOSELL,
                        "Auto-sell acts for you: priced drops are sold for coin as you mine.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOSELL_TOGGLE,
                        "/autosell: switch your personal auto-sell on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOSMELT,
                        "Auto-smelt acts for you: ores are smelted as you mine them.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOSMELT_TOGGLE,
                        "/autosmelt: switch your personal auto-smelt on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOTOOL,
                        "Auto-tool acts for you: the best tool swaps to hand as you mine.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_AUTOTOOL_TOGGLE,
                        "/autotool: switch your personal auto-tool on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_FARMASSIST,
                        "Right-click a mature crop to harvest and replant it, spending one seed.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_FARMPROTECT,
                        "Farmland protection acts for you: you will not trample your crops.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_FARMPROTECT_TOGGLE,
                        "/farmprotect: switch your personal farmland protection on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_GUI,
                        "/survival: open your personal survival mechanics settings panel.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_TREEFELLER,
                        "Tree-feller acts for you: break one log to fell the whole trunk.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_TREEFELLER_TOGGLE,
                        "/treefeller: switch your personal tree-feller on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_VEINMINER,
                        "Veinminer acts for you: break one block to mine the connected vein.",
                        PermissionDefault.TRUE,
                        SURVIVAL),
                PermissionSpec.of(
                        SURVIVAL_VEINMINER_TOGGLE,
                        "/veinminer: switch your personal veinminer on or off.",
                        PermissionDefault.TRUE,
                        SURVIVAL));
    }

    private static List<PermissionSpec> kits() {
        return List.of(
                PermissionSpec.family(
                        kit("<kit>"),
                        "Claim a kit whose file sets permission = true.",
                        // The node is only asked for a kit its file reserves, so a default that every player holds
                        // would leave the reservation granting nothing.
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        KITS),
                PermissionSpec.family(
                        kitCooldown("<group>"),
                        "Use the wait a kit's permission-cooldowns block sets for <group>; the shortest wait held wins. A group named by a number is skipped with a warning, since that name is a kit cooldown tier.",
                        PermissionDefault.FALSE,
                        PermissionShape.LABEL,
                        KITS),
                PermissionSpec.family(
                        SharedPermissions.featureCooldown("kit") + ".<seconds>",
                        "A cap on the wait before a kit can be claimed again, in seconds; the shortest tier held wins and 0 removes it. It lowers each kit's own wait but never lengthens it.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER,
                        KITS),
                PermissionSpec.of(
                        KIT_COOLDOWN_BYPASS,
                        "Skip kit cooldowns and re-claim one-time kits.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        KIT_EDIT,
                        "/kit create, /kit del, /kit editor to define, remove and edit kit contents.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        KIT_OTHERS,
                        "/kit <name> <player> to give a kit to another player.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        KIT_PREVIEW,
                        "/kit show <name> to preview a kit's contents without claiming it.",
                        PermissionDefault.TRUE,
                        KITS),
                PermissionSpec.of(
                        KIT_RESET,
                        "/kit reset <player> [kit] to clear a player's claim/cooldown stamps.",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        KIT_USE,
                        "/kit <name> to claim a kit and /kit list to list the kits you may claim.",
                        PermissionDefault.TRUE,
                        KITS),
                PermissionSpec.of(
                        MODULE_KITS,
                        "Hot-reload / inspect the kits module (kit definitions, cooldowns and claims).",
                        PermissionDefault.OP,
                        KITS),
                PermissionSpec.of(
                        OVERSIZEDSTACKS,
                        "Receive kit items in stacks larger than the material normally allows.",
                        PermissionDefault.OP,
                        KITS));
    }

    private static List<PermissionSpec> scoreboard() {
        return List.of(
                PermissionSpec.of(
                        MODULE_SCOREBOARD,
                        "Hot-reload / inspect the scoreboard module (per-player sidebar and tablist on uxmlib-hud).",
                        PermissionDefault.OP,
                        SCOREBOARD),
                PermissionSpec.of(
                        SCOREBOARD_GUI,
                        "/scoreboard gui (and the scoreboard entry on the /uxmess gui hub) to open the per-player scoreboard settings panel: the show/hide toggle.",
                        PermissionDefault.TRUE,
                        SCOREBOARD),
                PermissionSpec.of(
                        SCOREBOARD_USE,
                        "/scoreboard (alias /sb) to toggle whether you see the scoreboard display.",
                        PermissionDefault.TRUE,
                        SCOREBOARD));
    }

    private static List<PermissionSpec> bossbar() {
        return List.of(
                PermissionSpec.of(
                        MODULE_BOSSBAR,
                        "Hot-reload / inspect the bossbar module (bars that stay up, timed bars and announced bars).",
                        PermissionDefault.OP,
                        BOSSBAR),
                PermissionSpec.of(
                        BOSSBAR_USE,
                        "/bossbars, /bossbars toggle|on|off to hide or show your own boss bars.",
                        PermissionDefault.TRUE,
                        BOSSBAR),
                PermissionSpec.of(
                        BOSSBAR_OTHERS,
                        "/bossbars toggle|on|off <player> to hide or show another player's boss bars.",
                        PermissionDefault.OP,
                        BOSSBAR),
                PermissionSpec.of(
                        BOSSBAR_ANNOUNCE,
                        "/bossbars announce <bar> <time> for everybody and /bossbars send <player> <bar> <time> for one player.",
                        PermissionDefault.OP,
                        BOSSBAR));
    }

    private static List<PermissionSpec> tablist() {
        return List.of(PermissionSpec.of(
                MODULE_TABLIST,
                "Hot-reload / inspect the tablist module (the player list header, footer and rows).",
                PermissionDefault.OP,
                TABLIST));
    }

    private static List<PermissionSpec> nametags() {
        return List.of(PermissionSpec.of(
                MODULE_NAMETAGS,
                "Hot-reload / inspect the nametags module (the name shown above a player).",
                PermissionDefault.OP,
                NAMETAGS));
    }

    private static List<PermissionSpec> servertweaks() {
        return List.of(PermissionSpec.of(
                MODULE_SERVERTWEAKS,
                "Hot-reload / inspect the servertweaks module (the small server-behaviour switches).",
                PermissionDefault.OP,
                SERVERTWEAKS));
    }

    private static List<PermissionSpec> skin() {
        return List.of(
                PermissionSpec.of(
                        MODULE_SKIN,
                        "Hot-reload / inspect the skin module (the skin a player wears).",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        SKIN_USE,
                        "/skin <name> and /skin clear: wear another account's skin, or go back to your own.",
                        PermissionDefault.TRUE,
                        SKIN),
                PermissionSpec.of(
                        SKIN_URL,
                        "/skin url <link>: wear the skin drawn in an image published on the web.",
                        PermissionDefault.TRUE,
                        SKIN),
                PermissionSpec.of(
                        SKIN_FILE,
                        "/skin file <name>: wear one of the skins the operator dropped in the server's skin folder.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        SKIN_UPDATE,
                        "/skin update: re-fetch your skin, for an account whose skin changed at the source.",
                        PermissionDefault.TRUE,
                        SKIN),
                PermissionSpec.of(
                        SKIN_OTHER,
                        "/skin set <name> <player> and /skin clear <player>: dress or undress somebody else.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        SKIN_DROP,
                        "/skin drop <player>: delete a stored skin, so their next join derives one afresh.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        SKIN_INFO,
                        "/skin info <player>: which skin somebody wears, from where, and when it was set.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.of(
                        SKIN_PURGE,
                        "/skin purge <name>: forget a cached texture so the next lookup goes back to the source.",
                        PermissionDefault.OP,
                        SKIN),
                PermissionSpec.family(
                        skinName("<skin>"),
                        "Wear one skin listed under limits.restricted-skins; a skin nobody restricted is open to everyone.",
                        // Only a restricted skin asks for its node, so a default every player holds would undo the
                        // restriction.
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        SKIN),
                PermissionSpec.family(
                        SharedPermissions.featureCooldown("skin") + ".<seconds>",
                        "The wait between skin changes, in seconds; a held tier sets the wait, the shortest held wins and 0 removes it; with none held the configured wait applies.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER,
                        SKIN));
    }
}
