package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that own a place or a stash. Data, not logic: one row per node, read by {@link
 * PermissionCatalog} and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class DestinationPermissions {

    private static final ModuleId HOMES = ModuleId.of("homes");
    private static final ModuleId WARPS = ModuleId.of("warps");
    private static final ModuleId PLAYERWARPS = ModuleId.of("playerwarps");
    private static final ModuleId WORLDS = ModuleId.of("worlds");
    private static final ModuleId VAULTS = ModuleId.of("vaults");

    public static final String HOME_ADMIN = "uxmessentials.home.admin";
    public static final String HOME_BYPASS_COST = "uxmessentials.bypass.cost.home";
    public static final String HOME_BYPASS_UNSAFE = "uxmessentials.bypass.safety.home";
    public static final String HOME_ICON = "uxmessentials.home.icon";
    public static final String HOME_INVITE = "uxmessentials.home.invite";
    public static final String HOME_SETSERVER = "uxmessentials.home.setserver";
    public static final String HOME_USE = "uxmessentials.home.use";
    public static final String HOME_VISIT = "uxmessentials.home.visit";
    public static final String MODULE_HOMES = "uxmessentials.module.homes";
    public static final String MODULE_WARPS = "uxmessentials.module.warps";
    public static final String WARP_BYPASS_LOCK = "uxmessentials.bypass.lock.warp";
    public static final String WARP_BYPASS_PASSWORD = "uxmessentials.bypass.password.warp";
    public static final String WARP_BYPASS_SAFETY = "uxmessentials.bypass.safety.warp";
    public static final String WARP_DELETE = "uxmessentials.warp.delete";
    public static final String WARP_EDIT = "uxmessentials.warp.edit";
    public static final String WARP_INFO = "uxmessentials.warp.info";
    public static final String WARP_LIST = "uxmessentials.warp.list";
    public static final String WARP_LOCK = "uxmessentials.warp.lock";
    public static final String WARP_MOVE = "uxmessentials.warp.move";
    public static final String WARP_OTHERS = "uxmessentials.warp.others";
    public static final String WARP_PASSWORD = "uxmessentials.warp.password";
    public static final String WARP_SET = "uxmessentials.warp.set";
    public static final String WARP_SETSERVER = "uxmessentials.warp.setserver";
    public static final String WARP_SIGN_CREATE = "uxmessentials.warp.sign.create";
    public static final String WARP_SIGN_USE = "uxmessentials.warp.sign.use";
    public static final String WARP_USE = "uxmessentials.warp.use";
    public static final String PWARP_BAN = "uxmessentials.pwarp.ban";
    public static final String PWARP_BYPASS_BAN = "uxmessentials.bypass.ban.pwarp";
    public static final String PWARP_BYPASS_PASSWORD = "uxmessentials.bypass.password.pwarp";
    public static final String PWARP_BYPASS_WHITELIST = "uxmessentials.bypass.whitelist.pwarp";
    public static final String PWARP_CATEGORY = "uxmessentials.pwarp.category";
    public static final String PWARP_DELETE = "uxmessentials.pwarp.delete";
    public static final String PWARP_DESCRIPTION = "uxmessentials.pwarp.description";
    public static final String PWARP_DISPLAYNAME = "uxmessentials.pwarp.displayname";
    public static final String PWARP_ICON = "uxmessentials.pwarp.icon";
    public static final String PWARP_MEMBERS = "uxmessentials.pwarp.members";
    public static final String PWARP_MOVE = "uxmessentials.pwarp.move";
    public static final String PWARP_PASSWORD = "uxmessentials.pwarp.password";
    public static final String PWARP_PRICE = "uxmessentials.pwarp.price";
    public static final String PWARP_PUBLIC = "uxmessentials.pwarp.public";
    public static final String PWARP_RENAME = "uxmessentials.pwarp.rename";
    public static final String PWARP_SET = "uxmessentials.pwarp.set";
    public static final String PWARP_SPONSOR = "uxmessentials.pwarp.sponsor";
    public static final String PWARP_TRANSFER = "uxmessentials.pwarp.transfer";
    public static final String PWARP_WHITELIST = "uxmessentials.pwarp.whitelist";
    public static final String PWARP_WITHDRAW = "uxmessentials.pwarp.withdraw";
    public static final String MODULE_PLAYERWARPS = "uxmessentials.module.playerwarps";
    public static final String PWARP_ACCESS = "uxmessentials.pwarp.access";
    public static final String PWARP_ADMIN = "uxmessentials.pwarp.admin";
    public static final String PWARP_BYPASS_COST = "uxmessentials.bypass.cost.pwarp";
    public static final String PWARP_BYPASS_SAFETY = "uxmessentials.bypass.safety.pwarp";
    public static final String PWARP_FAVOURITE = "uxmessentials.pwarp.favourite";
    public static final String PWARP_EDIT = "uxmessentials.pwarp.edit";
    public static final String PWARP_GUI = "uxmessentials.pwarp.gui";
    public static final String PWARP_INFO = "uxmessentials.pwarp.info";
    public static final String PWARP_LIST = "uxmessentials.pwarp.list";
    public static final String PWARP_RATE = "uxmessentials.pwarp.rate";
    public static final String PWARP_USE = "uxmessentials.pwarp.use";
    public static final String MODULE_WORLDS = "uxmessentials.module.worlds";
    public static final String WORLD_ACCESS_BYPASS = "uxmessentials.bypass.access.world";
    public static final String WORLD_BACKUP = "uxmessentials.world.backup";
    public static final String WORLD_BYPASS_SAFETY = "uxmessentials.bypass.safety.world";
    public static final String WORLD_CLAIM = "uxmessentials.world.claim";
    public static final String WORLD_CREATE = "uxmessentials.world.create";
    public static final String WORLD_DELETE = "uxmessentials.world.delete";
    public static final String WORLD_GAMEMODE_BYPASS = "uxmessentials.bypass.gamemode.world";
    public static final String WORLD_GAMERULE = "uxmessentials.world.gamerule";
    public static final String WORLD_GUI = "uxmessentials.world.gui";
    public static final String WORLD_IMPORT = "uxmessentials.world.import";
    public static final String WORLD_INFO = "uxmessentials.world.info";
    public static final String WORLD_LIST = "uxmessentials.world.list";
    public static final String WORLD_LOAD = "uxmessentials.world.load";
    public static final String WORLD_PREGEN = "uxmessentials.world.pregen";
    public static final String WORLD_RESTORE = "uxmessentials.world.restore";
    public static final String WORLD_SET = "uxmessentials.world.set";
    public static final String WORLD_SETSERVER = "uxmessentials.world.setserver";
    public static final String WORLD_SETSPAWN = "uxmessentials.world.setspawn";
    public static final String WORLD_SPAWN = "uxmessentials.world.spawn";
    public static final String WORLD_TP = "uxmessentials.world.tp";
    public static final String WORLD_TP_OTHERS = "uxmessentials.world.tp.others";
    public static final String WORLD_UNLOAD = "uxmessentials.world.unload";
    public static final String WORLD_UNREGISTER = "uxmessentials.world.unregister";
    public static final String WORLD_VOIDRESCUE_EXEMPT = "uxmessentials.bypass.void-rescue";
    public static final String MODULE_VAULTS = "uxmessentials.module.vaults";
    public static final String VAULT_ADMIN_DELETE = "uxmessentials.vault.admin.delete";
    public static final String VAULT_BYPASS_BLACKLIST = "uxmessentials.bypass.blacklist.vault";
    public static final String VAULT_FREE = "uxmessentials.bypass.cost.vault";
    public static final String VAULT_ICON = "uxmessentials.vault.icon";
    public static final String VAULT_OTHERS = "uxmessentials.vault.others";
    public static final String VAULT_RENAME = "uxmessentials.vault.rename";
    public static final String VAULT_USE = "uxmessentials.vault.use";

    /** The head of the home quota; the number held is appended to it. */
    public static final String HOME_LIMIT = "uxmessentials.home.limit";

    /** The head of the player-warp quota; the number held is appended to it. */
    public static final String PWARP_LIMIT = "uxmessentials.pwarp.limit";

    /** The head of the vault-count quota; the number held is appended to it. */
    public static final String VAULT_AMOUNT = "uxmessentials.vault.amount";

    /** The head of the vault-rows quota; the number held is appended to it. */
    public static final String VAULT_SIZE = "uxmessentials.vault.size";

    private static final String WARP_USE_PREFIX = "uxmessentials.warp.use.";
    private static final String WORLD_PREFIX = "uxmessentials.world.";

    /** The node a player needs to use {@code warp} when that warp asks for a permission. */
    public static String warpUse(String warp) {
        return WARP_USE_PREFIX + warp;
    }

    /**
     * The head of the player-warp quota in one world; the number held is appended to it. It caps the owner's warps in
     * {@code world} only, beside the {@link #PWARP_LIMIT} cap on all of them.
     */
    public static String pwarpWorldLimit(String world) {
        return PWARP_LIMIT + "." + world;
    }

    /**
     * The head of the home quota in one world; the number held is appended to it. It caps the owner's homes in
     * {@code world} only, beside the {@link #HOME_LIMIT} cap on all of them.
     */
    public static String homeWorldLimit(String world) {
        return HOME_LIMIT + "." + world;
    }

    /** The node a player needs to enter {@code world} when it is restricted. */
    public static String worldEnter(String world) {
        return WORLD_PREFIX + world + ".enter";
    }

    private DestinationPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(homes(), warps(), playerwarpsOwnership(), playerwarpsVisitors(), worlds(), vaults())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PermissionSpec> homes() {
        return List.of(
                PermissionSpec.of(
                        HOME_ADMIN,
                        "/home admin <player> to list, teleport to, set, delete, clear and inspect another player's homes.",
                        PermissionDefault.OP,
                        HOMES),
                PermissionSpec.of(
                        HOME_BYPASS_COST,
                        "Skip the per-action economy cost for home create, relocate, and teleport actions.",
                        PermissionDefault.OP,
                        HOMES),
                PermissionSpec.of(
                        HOME_BYPASS_UNSAFE,
                        "Skip the unsafe-destination confirm when teleporting to a home via the GUI, and land at an unsafe home on another server.",
                        PermissionDefault.OP,
                        HOMES),
                PermissionSpec.of(
                        HOME_ICON,
                        "Pick a custom GUI icon for one of your homes from the grid.",
                        PermissionDefault.TRUE,
                        HOMES),
                PermissionSpec.of(
                        HOME_INVITE,
                        "/home invite and /home uninvite to grant or revoke another player's access to one of your homes.",
                        PermissionDefault.TRUE,
                        HOMES),
                PermissionSpec.family(
                        HOME_LIMIT + ".<n>",
                        "How many homes you may keep; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        HOMES),
                PermissionSpec.family(
                        homeWorldLimit("<world>") + ".<n>",
                        "How many homes you may keep in one world, counting only your homes there; the largest tier held wins. The limit across every world still applies.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        HOMES),
                PermissionSpec.of(
                        HOME_SETSERVER,
                        "/home setserver <from> <to> to move every home recorded on one server to another without moving any.",
                        PermissionDefault.OP,
                        HOMES),
                PermissionSpec.of(
                        HOME_USE, "/home to open and manage your slot-based home grid.", PermissionDefault.TRUE, HOMES),
                PermissionSpec.of(
                        HOME_VISIT,
                        "/home visit <player> [slot] to teleport to another player's public home or one you were invited to.",
                        PermissionDefault.TRUE,
                        HOMES),
                PermissionSpec.of(
                        MODULE_HOMES,
                        "Hot-reload / inspect the homes module (per-player homes and the slot grid).",
                        PermissionDefault.OP,
                        HOMES));
    }

    private static List<PermissionSpec> warps() {
        return List.of(
                PermissionSpec.of(
                        MODULE_WARPS,
                        "Hot-reload / inspect the warps module (server warps and their access rules).",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_BYPASS_LOCK,
                        "Use a locked warp regardless of its lock state.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_BYPASS_PASSWORD,
                        "Use a password-protected warp without entering its password.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_BYPASS_SAFETY,
                        "Use a warp whose destination fails the safety check.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(WARP_DELETE, "/warp del <name> to remove a warp.", PermissionDefault.OP, WARPS),
                PermissionSpec.of(
                        WARP_EDIT,
                        "/warp editor [name] to open the warp manager, or one warp's editor (icon, category, lock, password, welcome messages, sounds, particles, warmup and cooldown).",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_INFO,
                        "/warp info <name> to show a warp's owner, creation time, cost and required permission.",
                        PermissionDefault.TRUE,
                        WARPS),
                PermissionSpec.of(
                        WARP_LIST, "/warp list to list the warps you may use.", PermissionDefault.TRUE, WARPS),
                PermissionSpec.of(
                        WARP_LOCK,
                        "/warp lock <name> to lock or unlock a warp against use.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_MOVE,
                        "/warp move <name> to move an existing warp to your current location, or to a given one with at.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_OTHERS,
                        "/warp <name> <player> to send another player to a warp.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_PASSWORD,
                        "/warp password <name> [password] to set a warp's access password, or clear it when none is given.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_SET,
                        "/warp create <name> to create a server-wide warp, or move one that exists, at your location or a given one.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_SETSERVER,
                        "/warp setserver <name|all> [server] to record which server a warp lives on without moving it.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_SIGN_CREATE,
                        "Create a [warp] sign that teleports players to a warp on click.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        WARP_SIGN_USE, "Use a [warp] sign to teleport to its warp.", PermissionDefault.TRUE, WARPS),
                PermissionSpec.of(
                        WARP_USE, "/warp <name> to teleport to a server warp.", PermissionDefault.TRUE, WARPS),
                PermissionSpec.family(
                        warpUse("<warp>"),
                        "Use one server warp; asked for every warp, before any permission the warp itself requires.",
                        PermissionDefault.TRUE,
                        PermissionShape.LABEL,
                        WARPS));
    }

    /** What the owner of a player warp may do to it. */
    private static List<PermissionSpec> playerwarpsOwnership() {
        return List.of(
                PermissionSpec.of(
                        PWARP_BAN,
                        "/pwarp ban|unban <name> <player> to bar a player from one of your warps or lift that bar.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_BYPASS_BAN,
                        "Enter a player warp you are banned from (skips the ban check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_BYPASS_PASSWORD,
                        "Enter a password-protected player warp without the password (skips the check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_BYPASS_WHITELIST,
                        "Enter a whitelist-only player warp without being on the whitelist (skips the check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_CATEGORY,
                        "/pwarp category <name> [categoryId] to file a player warp under a browse category.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_DELETE,
                        "/pwarp del <name> to remove one of your player warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_DESCRIPTION,
                        "/pwarp description <name> [text] to set or clear a player warp's description.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_DISPLAYNAME,
                        "/pwarp displayname <name> [text] to set or clear a player warp's display name.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_ICON,
                        "/pwarp icon <name> [icon] to set or clear a player warp's browse icon.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.family(
                        PWARP_LIMIT + ".<n>",
                        "How many player warps you may own across every world; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        PLAYERWARPS),
                PermissionSpec.family(
                        pwarpWorldLimit("<world>") + ".<n>",
                        "How many player warps you may own in one world, counting only your warps there; the largest tier held wins. The limit across every world still applies.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_MEMBERS,
                        "/pwarp members <name> add|remove <player> to grant or revoke a co-owner or manager on one of your warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_MOVE,
                        "/pwarp move <name> to re-anchor one of your player warps at your location.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_PASSWORD,
                        "/pwarp password <name> <password>|clear to set or clear a player warp's password.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_PRICE,
                        "/pwarp price <name> <amount> [currency] to set a player warp's entry price.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_PUBLIC,
                        "/pwarp visibility public|private <name> to toggle a player warp's visibility.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_RENAME,
                        "/pwarp rename <name> <newName> to rename one of your player warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_SET,
                        "/setpwarp <name> to create or move a player-owned warp at your location.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_SPONSOR,
                        "/pwarp sponsor <name> [days] to buy a paid, time-limited pinned browse slot for one of your warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_TRANSFER,
                        "/pwarp transfer <name> <player> to hand ownership of one of your warps to another player.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_WHITELIST,
                        "/pwarp whitelist <name> add|remove <player> to manage a whitelist-access warp's guest list.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_WITHDRAW,
                        "/pwarp withdraw <name> to pay one of your warps' accrued earnings out to its owner.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS));
    }

    /** What a visitor may do, and what an administrator may step over. */
    private static List<PermissionSpec> playerwarpsVisitors() {
        return List.of(
                PermissionSpec.of(
                        MODULE_PLAYERWARPS,
                        "Hot-reload / inspect the playerwarps module (player-owned warps behind /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_ACCESS,
                        "/pwarp access <name> <PUBLIC|PASSWORD|WHITELIST|PRIVATE> to set a player warp's access axis.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_ADMIN,
                        "/pwarp admin restore|purge|setowner|reload to manage any player's warp by its id.",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_BYPASS_COST,
                        "Use a priced player warp without paying its entry cost (skips the charge on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_BYPASS_SAFETY,
                        "Use a player warp whose destination is unsafe (skips the safe-landing check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_FAVOURITE,
                        "/pwarp favourite|unfavourite <name> to star or un-star a player warp.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_EDIT,
                        "/pwarp edit <name> to open one warp's property editor, the click-driven form of the typed "
                                + "verbs. Held by default alongside the use node.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_GUI,
                        "Manage every player's warps in the /pwarp GUI (a player without it edits only their own).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_INFO,
                        "/pwarp info <name> to show a player warp's owner, access, price, visits, and rating.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_LIST,
                        "/pwarps [player] to list your warps or a player's public warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_RATE,
                        "/pwarp rate <name> <1-5> to award a player warp a star rating.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        PWARP_USE,
                        "/pwarp <name> [password] to teleport to a player warp you may enter; a password warp takes its password.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS));
    }

    private static List<PermissionSpec> worlds() {
        return List.of(
                PermissionSpec.of(
                        MODULE_WORLDS,
                        "Hot-reload / inspect the worlds module (world creation, properties and access).",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.family(
                        worldEnter("<world>"),
                        "Enter one world that is configured as restricted.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_ACCESS_BYPASS,
                        "Enter a restricted world without holding that world's own entry node.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_BACKUP,
                        "/worlds backup <name>: snapshot a world's folder.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_BYPASS_SAFETY,
                        "Land on the spawn of a world on another server even when the safe-landing check refuses it.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_CLAIM,
                        "/worlds claim <name>: take a world no server has claimed onto this server.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_CREATE,
                        "/worlds create <name>: generate and register a new world.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_DELETE,
                        "/worlds delete <name>: unregister a world and delete its folder from disk.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_GAMEMODE_BYPASS,
                        "Keep your own game mode in a world that forces one on entry.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_GAMERULE,
                        "/worlds gamerule <name> <rule> <value>: change one gamerule on a world.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_GUI, "/worlds gui: open the world management GUI.", PermissionDefault.OP, WORLDS),
                PermissionSpec.of(
                        WORLD_IMPORT,
                        "/worlds import <folder>: adopt an existing world folder into the registry.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_INFO,
                        "/worlds info <name>: read one world's generator, properties, gamerules and spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_LIST,
                        "/worlds list: list every registered world with its load state and player count.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_LOAD,
                        "/worlds load <name>: load a registered world that is currently unloaded.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_PREGEN,
                        "/worlds pregen <name> <radius>: pre-generate a world's chunks in the background.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_RESTORE,
                        "/worlds restore <name> <backup>: restore a world from one of its snapshots.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_SET,
                        "/worlds set <name> <property> <value>: change a world property such as difficulty or PvP.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_SETSERVER,
                        "/worlds setserver <from> <to>: move every world recorded on one server to another, settings"
                                + " included, without touching a world's files.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_SETSPAWN,
                        "/worlds setspawn [name]: set a world's spawn point to where you are standing.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_SPAWN,
                        "/worlds spawn to teleport to a world's spawn (subject to per-world access rules).",
                        PermissionDefault.TRUE,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_TP,
                        "/worlds tp <name>: teleport yourself to a world's spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_TP_OTHERS,
                        "/worlds tp <name> <player>: teleport somebody else to a world's spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_UNLOAD,
                        "/worlds unload <name>: unload a loaded world, moving anybody inside to spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_UNREGISTER,
                        "/worlds unregister <name>: drop a world from the registry, leaving its folder on disk.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        WORLD_VOIDRESCUE_EXEMPT,
                        "Keep falling in a world that catches players out of the void.",
                        PermissionDefault.FALSE,
                        WORLDS));
    }

    private static List<PermissionSpec> vaults() {
        return List.of(
                PermissionSpec.of(
                        MODULE_VAULTS,
                        "Hot-reload / inspect the vaults module (DB-persisted player vault storage).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.of(
                        VAULT_ADMIN_DELETE,
                        "/vault delete <player> <n>: delete another player's vault (audit-logged, no refund).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.family(
                        VAULT_AMOUNT + ".<n>",
                        "How many vaults you may open; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        VAULTS),
                PermissionSpec.of(
                        VAULT_BYPASS_BLACKLIST,
                        "Store any item in a vault, ignoring the blacklist-materials list (items are not returned).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.of(
                        VAULT_FREE,
                        "Bypass every vault economy fee (create/open); no refund is paid on delete.",
                        PermissionDefault.FALSE,
                        VAULTS),
                PermissionSpec.of(
                        VAULT_ICON,
                        "/vault icon <n> [material]: set or clear the icon of your own vault (held item if omitted).",
                        PermissionDefault.TRUE,
                        VAULTS),
                PermissionSpec.of(
                        VAULT_OTHERS,
                        "/vault <player> [n]: open and audit another player's vault (audit-logged).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.of(
                        VAULT_RENAME,
                        "/vault rename <n> [name]: set or clear the display name of your own vault.",
                        PermissionDefault.TRUE,
                        VAULTS),
                PermissionSpec.family(
                        VAULT_SIZE + ".<rows>",
                        "How many rows each of your vaults holds; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        VAULTS),
                PermissionSpec.of(
                        VAULT_USE,
                        "/vault to open your default vault (or list them), /vault <n> to open the Nth, and /vault delete <n> to delete your own.",
                        PermissionDefault.TRUE,
                        VAULTS));
    }
}
