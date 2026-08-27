package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that own a place or a stash. Data, not logic: one row per node, read by {@link
 * PermissionCatalog} and through it by the server registration, the reference page and the in-game listing.
 */
final class DestinationPermissions {

    private static final ModuleId HOMES = ModuleId.of("homes");
    private static final ModuleId WARPS = ModuleId.of("warps");
    private static final ModuleId PLAYERWARPS = ModuleId.of("playerwarps");
    private static final ModuleId WORLDS = ModuleId.of("worlds");
    private static final ModuleId VAULTS = ModuleId.of("vaults");

    private DestinationPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(homes(), warps(), playerwarpsOwnership(), playerwarpsVisitors(), worlds(), vaults())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PermissionSpec> homes() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.home.admin",
                        "/homeadmin to manage another player's homes.",
                        PermissionDefault.OP,
                        HOMES),
                PermissionSpec.of(
                        "uxmessentials.home.bypass.cost",
                        "Skip the per-action economy cost for home create, relocate, and teleport actions.",
                        PermissionDefault.OP,
                        HOMES),
                PermissionSpec.of(
                        "uxmessentials.home.bypass.unsafe",
                        "Skip the unsafe-destination confirm when teleporting to a home via the GUI.",
                        PermissionDefault.OP,
                        HOMES),
                PermissionSpec.of(
                        "uxmessentials.home.icon",
                        "Pick a custom GUI icon for one of your homes from the grid.",
                        PermissionDefault.TRUE,
                        HOMES),
                PermissionSpec.of(
                        "uxmessentials.home.invite",
                        "/invite and /uninvite to grant or revoke another player's access to one of your homes.",
                        PermissionDefault.TRUE,
                        HOMES),
                PermissionSpec.family(
                        "uxmessentials.home.limit.<n>",
                        "How many homes you may keep; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        HOMES),
                PermissionSpec.of(
                        "uxmessentials.home.use",
                        "/home to open and manage your slot-based home grid.",
                        PermissionDefault.TRUE,
                        HOMES),
                PermissionSpec.of(
                        "uxmessentials.home.visit",
                        "/visit to teleport to another player's public home or one you were invited to.",
                        PermissionDefault.TRUE,
                        HOMES),
                PermissionSpec.of(
                        "uxmessentials.module.homes",
                        "Hot-reload / inspect the homes module (per-player homes and the slot grid).",
                        PermissionDefault.OP,
                        HOMES));
    }

    private static List<PermissionSpec> warps() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.warps",
                        "Hot-reload / inspect the warps module (server warps and their access rules).",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.bypass.lock",
                        "Use a locked warp regardless of its lock state.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.bypass.password",
                        "Use a password-protected warp without entering its password.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.bypass.safety",
                        "Use a warp whose destination fails the safety check.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.delete", "/delwarp <name> to remove a warp.", PermissionDefault.OP, WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.edit",
                        "/warp editor <name> to open the warp editor (cost, gates, effects, welcome message, icon).",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.info",
                        "/warpinfo <name> to show a warp's owner, creation time and cost.",
                        PermissionDefault.TRUE,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.list",
                        "/warps to list the warps you may use.",
                        PermissionDefault.TRUE,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.lock",
                        "/warp lock <name> to lock or unlock a warp against use.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.move",
                        "/movewarp <name> to move an existing warp to your current location.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.others",
                        "/warp <name> <player> to send another player to a warp.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.password",
                        "/warp password <name> to set or clear a warp's access password.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.set",
                        "/setwarp <name> to create or move a server-wide warp.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.sign.create",
                        "Create a [warp] sign that teleports players to a warp on click.",
                        PermissionDefault.OP,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.sign.use",
                        "Use a [warp] sign to teleport to its warp.",
                        PermissionDefault.TRUE,
                        WARPS),
                PermissionSpec.of(
                        "uxmessentials.warp.use",
                        "/warp <name> to teleport to a server warp.",
                        PermissionDefault.TRUE,
                        WARPS),
                PermissionSpec.family(
                        "uxmessentials.warp.use.<warp>",
                        "Use one warp, when that warp is configured to require a permission.",
                        PermissionDefault.TRUE,
                        PermissionShape.LABEL,
                        WARPS));
    }

    /** What the owner of a player warp may do to it. */
    private static List<PermissionSpec> playerwarpsOwnership() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.pwarp.ban",
                        "/pwarp ban|unban <name> <player> to bar a player from one of your warps or lift that bar.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.bypass.ban",
                        "Enter a player warp you are banned from (skips the ban check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.bypass.password",
                        "Enter a password-protected player warp without the password (skips the check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.bypass.whitelist",
                        "Enter a whitelist-only player warp without being on the whitelist (skips the check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.category",
                        "/pwarp category <name> [categoryId] to file a player warp under a browse category.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.delete",
                        "/pwarp del <name> to remove one of your player warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.description",
                        "/pwarp description <name> [text] to set or clear a player warp's description.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.displayname",
                        "/pwarp displayname <name> [text] to set or clear a player warp's display name.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.icon",
                        "/pwarp icon <name> [icon] to set or clear a player warp's browse icon.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.family(
                        "uxmessentials.pwarp.limit.<n>",
                        "How many player warps you may own; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.members",
                        "/pwarp members <name> add|remove <player> to grant or revoke a co-owner or manager on one of your warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.move",
                        "/pwarp move <name> to re-anchor one of your player warps at your location.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.password",
                        "/pwarp password <name> <password>|clear to set or clear a player warp's password.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.price",
                        "/pwarp price <name> <amount> [currency] to set a player warp's entry price.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.public",
                        "/pwarp visibility public|private <name> to toggle a player warp's visibility.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.rename",
                        "/pwarp rename <name> <newName> to rename one of your player warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.set",
                        "/setpwarp <name> to create or move a player-owned warp at your location.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.sponsor",
                        "/pwarp sponsor <name> [days] to buy a paid, time-limited pinned browse slot for one of your warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.transfer",
                        "/pwarp transfer <name> <player> to hand ownership of one of your warps to another player.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.whitelist",
                        "/pwarp whitelist <name> add|remove <player> to manage a whitelist-access warp's guest list.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.withdraw",
                        "/pwarp withdraw <name> to pay one of your warps' accrued earnings out to its owner.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS));
    }

    /** What a visitor may do, and what an administrator may step over. */
    private static List<PermissionSpec> playerwarpsVisitors() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.playerwarps",
                        "Hot-reload / inspect the playerwarps module (player-owned warps behind /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.access",
                        "/pwarp access <name> <PUBLIC|PASSWORD|WHITELIST|PRIVATE> to set a player warp's access axis.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.admin",
                        "/pwarp admin restore|purge|setowner|reload to manage any player's warp by its id.",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.bypass.cost",
                        "Use a priced player warp without paying its entry cost (skips the charge on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.bypass.safety",
                        "Use a player warp whose destination is unsafe (skips the safe-landing check on /pwarp).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.favourite",
                        "/pwarp favourite|unfavourite <name> to star or un-star a player warp.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.edit",
                        "/pwarp edit <name> to open one warp's property editor, the click-driven form of the typed "
                                + "verbs. Held by default alongside the use node.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.gui",
                        "Manage every player's warps in the /pwarp GUI (a player without it edits only their own).",
                        PermissionDefault.OP,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.info",
                        "/pwarp info <name> to show a player warp's owner, access, price, visits, and rating.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.list",
                        "/pwarps [player] to list your warps or a player's public warps.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.rate",
                        "/pwarp rate <name> <1-5> to award a player warp a star rating.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS),
                PermissionSpec.of(
                        "uxmessentials.pwarp.use",
                        "/pwarp <name> [owner] to teleport to your own or a player's public warp.",
                        PermissionDefault.TRUE,
                        PLAYERWARPS));
    }

    private static List<PermissionSpec> worlds() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.worlds",
                        "Hot-reload / inspect the worlds module (world creation, properties and access).",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.family(
                        "uxmessentials.world.<world>",
                        "Enter one world that is configured as restricted.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.access.bypass",
                        "Enter a restricted world without holding that world's own entry node.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.backup",
                        "/world backup <name>: snapshot a world's folder.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.create",
                        "/world create <name>: generate and register a new world.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.delete",
                        "/world delete <name>: unregister a world and delete its folder from disk.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.gamemode.bypass",
                        "Keep your own game mode in a world that forces one on entry.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.gamerule",
                        "/world gamerule <name> <rule> <value>: change one gamerule on a world.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.gui",
                        "/world: open the world management GUI.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.import",
                        "/world import <folder>: adopt an existing world folder into the registry.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.info",
                        "/world info <name>: read one world's generator, properties, gamerules and spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.list",
                        "/world list: list every registered world with its load state and player count.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.load",
                        "/world load <name>: load a registered world that is currently unloaded.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.pregen",
                        "/world pregen <name> <radius>: pre-generate a world's chunks in the background.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.restore",
                        "/world restore <name> <backup>: restore a world from one of its snapshots.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.set",
                        "/world set <name> <property> <value>: change a world property such as difficulty or PvP.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.setspawn",
                        "/world setspawn [name]: set a world's spawn point to where you are standing.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.spawn",
                        "/worlds spawn to teleport to a world's spawn (subject to per-world access rules).",
                        PermissionDefault.TRUE,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.tp",
                        "/world tp <name>: teleport yourself to a world's spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.tp.others",
                        "/world tp <name> <player>: teleport somebody else to a world's spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.unload",
                        "/world unload <name>: unload a loaded world, moving anybody inside to spawn.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.unregister",
                        "/world unregister <name>: drop a world from the registry, leaving its folder on disk.",
                        PermissionDefault.OP,
                        WORLDS),
                PermissionSpec.of(
                        "uxmessentials.world.voidrescue.exempt",
                        "Keep falling in a world that catches players out of the void.",
                        PermissionDefault.FALSE,
                        WORLDS));
    }

    private static List<PermissionSpec> vaults() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.vaults",
                        "Hot-reload / inspect the vaults module (DB-persisted player vault storage).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.of(
                        "uxmessentials.vault.admin.delete",
                        "/vault delete <player> <n>: delete another player's vault (audit-logged, no refund).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.family(
                        "uxmessentials.vault.amount.<n>",
                        "How many vaults you may open; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        VAULTS),
                PermissionSpec.of(
                        "uxmessentials.vault.bypass-blacklist",
                        "Store any item in a vault, ignoring the blacklist-materials list (items are not returned).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.of(
                        "uxmessentials.vault.free",
                        "Bypass every vault economy fee (create/open); no refund is paid on delete.",
                        PermissionDefault.FALSE,
                        VAULTS),
                PermissionSpec.of(
                        "uxmessentials.vault.icon",
                        "/vault icon <n> [material]: set or clear the icon of your own vault (held item if omitted).",
                        PermissionDefault.TRUE,
                        VAULTS),
                PermissionSpec.of(
                        "uxmessentials.vault.others",
                        "/vault <player> [n]: open and audit another player's vault (audit-logged).",
                        PermissionDefault.OP,
                        VAULTS),
                PermissionSpec.of(
                        "uxmessentials.vault.rename",
                        "/vault rename <n> [name]: set or clear the display name of your own vault.",
                        PermissionDefault.TRUE,
                        VAULTS),
                PermissionSpec.family(
                        "uxmessentials.vault.size.<rows>",
                        "How many rows each of your vaults holds; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        VAULTS),
                PermissionSpec.of(
                        "uxmessentials.vault.use",
                        "/vault to open your default vault (or list them), /vault <n> to open the Nth, and /vault delete <n> to delete your own.",
                        PermissionDefault.TRUE,
                        VAULTS));
    }
}
