package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that police a server. Data, not logic: one row per node, read by {@link
 * PermissionCatalog} and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class EnforcementPermissions {

    private static final ModuleId MODERATION = ModuleId.of("moderation");
    private static final ModuleId SECURITY = ModuleId.of("security");
    private static final ModuleId STAFF = ModuleId.of("staff");
    private static final ModuleId INVROLLBACK = ModuleId.of("invrollback");
    private static final ModuleId VANISH = ModuleId.of("vanish");
    private static final ModuleId COMMANDCONTROL = ModuleId.of("commandcontrol");

    public static final String MODERATION_BAN = "uxmessentials.moderation.ban";
    public static final String MODERATION_BANIP = "uxmessentials.moderation.banip";
    public static final String MODERATION_BANLIST = "uxmessentials.moderation.banlist";
    public static final String MODERATION_FREEZE = "uxmessentials.moderation.freeze";
    public static final String MODERATION_JAIL = "uxmessentials.moderation.jail";
    public static final String MODERATION_KICK = "uxmessentials.moderation.kick";
    public static final String MODERATION_LOCKDOWN = "uxmessentials.moderation.lockdown";
    public static final String MODERATION_LOCKDOWN_BYPASS = "uxmessentials.bypass.lockdown";
    public static final String MODERATION_MUTE = "uxmessentials.moderation.mute";
    public static final String MODERATION_MUTELIST = "uxmessentials.moderation.mutelist";
    public static final String MODERATION_SANCTION = "uxmessentials.moderation.sanction";
    public static final String MODERATION_SUDO = "uxmessentials.moderation.sudo";
    public static final String MODERATION_TEMPBAN = "uxmessentials.moderation.tempban";
    public static final String MODERATION_TEMPLATES = "uxmessentials.moderation.templates";
    public static final String MODERATION_TOGGLEJAIL = "uxmessentials.moderation.togglejail";
    public static final String MODERATION_UNJAIL = "uxmessentials.moderation.unjail";
    public static final String MODERATION_UNMUTE = "uxmessentials.moderation.unmute";
    public static final String MODERATION_WARN = "uxmessentials.moderation.warn";
    public static final String MODERATION_BROADCAST_RECEIVE = "uxmessentials.moderation.broadcast.receive";
    public static final String MODERATION_CHECK = "uxmessentials.moderation.check";
    public static final String MODERATION_COMMANDSPY = "uxmessentials.moderation.commandspy";
    public static final String MODERATION_EXEMPT = "uxmessentials.bypass.punishment";
    public static final String MODERATION_GUI = "uxmessentials.moderation.gui";
    public static final String MODERATION_HISTORY = "uxmessentials.moderation.history";
    public static final String MODERATION_SEEN = "uxmessentials.moderation.seen";
    public static final String MODERATION_STAFFHISTORY = "uxmessentials.moderation.staffhistory";
    public static final String MODERATION_STAFFROLLBACK = "uxmessentials.moderation.staffrollback";
    public static final String MODERATION_STATS = "uxmessentials.moderation.stats";
    public static final String MODULE_MODERATION = "uxmessentials.module.moderation";
    public static final String MODULE_SECURITY = "uxmessentials.module.security";
    public static final String SECURITY_2FA = "uxmessentials.security.2fa";
    public static final String SECURITY_ADMIN = "uxmessentials.security.admin";
    public static final String SECURITY_ALTS = "uxmessentials.security.alts";
    public static final String SECURITY_ALTS_NOTIFY = "uxmessentials.security.alts.notify";
    public static final String SECURITY_BYPASS = "uxmessentials.bypass.security";
    public static final String SECURITY_CLIENTINFO = "uxmessentials.security.clientinfo";
    public static final String SECURITY_FORCE = "uxmessentials.security.force";
    public static final String SECURITY_PIN = "uxmessentials.security.pin";
    public static final String SECURITY_PIN_REQUIRED = "uxmessentials.security.pin.required";
    public static final String SECURITY_RESET = "uxmessentials.security.reset";
    public static final String MODULE_STAFF = "uxmessentials.module.staff";
    public static final String STAFF_CHAT = "uxmessentials.staff.chat";
    public static final String STAFF_LIST = "uxmessentials.staff.list";
    public static final String STAFF_MODE = "uxmessentials.staff.mode";
    public static final String INVROLLBACK_EXPORT = "uxmessentials.invrollback.export";
    public static final String INVROLLBACK_RESTORE = "uxmessentials.invrollback.restore";
    public static final String INVROLLBACK_TELEPORT = "uxmessentials.invrollback.teleport";
    public static final String MODULE_INVROLLBACK = "uxmessentials.module.invrollback";
    public static final String MODULE_VANISH = "uxmessentials.module.vanish";
    public static final String VANISH_LIST = "uxmessentials.vanish.list";
    public static final String VANISH_OTHERS = "uxmessentials.vanish.others";
    public static final String VANISH_PERSIST = "uxmessentials.vanish.persist";
    public static final String VANISH_SEE = "uxmessentials.vanish.see";
    public static final String VANISH_SILENT = "uxmessentials.vanish.silent";
    public static final String VANISH_USE = "uxmessentials.vanish.use";
    public static final String COMMANDCONTROL_BYPASS = "uxmessentials.bypass.commandcontrol";
    public static final String COMMANDCONTROL_CHANNELHIDE_BYPASS = "uxmessentials.bypass.channel-hide";
    public static final String COMMANDCONTROL_SPAM_BYPASS = "uxmessentials.bypass.command-spam";
    public static final String COMMANDCONTROL_VIEWPLUGINS = "uxmessentials.commandcontrol.viewplugins";
    public static final String COMMANDCONTROL_VIEWPROXYCOMMANDS = "uxmessentials.commandcontrol.viewproxycommands";
    public static final String MODULE_COMMANDCONTROL = "uxmessentials.module.commandcontrol";

    /** The head of the numbered see levels; the level is appended to it with no separator. */
    public static final String VANISH_SEE_LEVEL = "uxmessentials.vanish.see.level";

    /** The head of the numbered vanish levels; the level is appended to it with no separator. */
    public static final String VANISH_USE_LEVEL = "uxmessentials.vanish.use.level";

    private static final String MODERATION_PREFIX = "uxmessentials.moderation.";

    /** The head of the duration cap for one {@code sanction}; the seconds held are appended to it. */
    public static String maxDuration(String sanction) {
        return MODERATION_PREFIX + sanction + ".maxduration";
    }

    private EnforcementPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(
                        moderationSanctions(),
                        moderationReview(),
                        security(),
                        staff(),
                        invrollback(),
                        vanish(),
                        commandcontrol())
                .flatMap(List::stream)
                .toList();
    }

    /** Handing out and lifting punishments. */
    private static List<PermissionSpec> moderationSanctions() {
        return List.of(
                PermissionSpec.family(
                        maxDuration("<sanction>") + ".<seconds>",
                        "The longest ban or mute you may hand out, in seconds: <sanction> is ban (which also caps /tempban) or mute. The largest tier held wins, -1 or no node means no limit, and a jail has no limit.",
                        PermissionDefault.OP,
                        PermissionShape.TIER,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_BAN,
                        "/ban <player> [reason] and /unban <player>: permanent UUID ban and its lift; /banhistory <player> reviews a player's full ban/unban history.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_BANIP,
                        "/banip <player|ip> [reason] / /unbanip <ip>: IP ban with stored-IP alt detection.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_BANLIST,
                        "/banlist to review the players currently banned.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_FREEZE,
                        "/freeze <player> / /unfreeze <player>: pin a player in place pending review.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_JAIL,
                        "/jail <player> <jail> [duration] [reason]; /jails lists configured jails; /jailedplayers lists who is jailed; /setjail <name> defines a jail at your location; /jail del <name> removes a defined jail. On a network the target may be on any server, and a jail defined on another server sends them there.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_KICK,
                        "/kick <player> [reason]; /kickall [reason] to clear non-exempt players.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_LOCKDOWN,
                        "/lockdown [on|off]: refuse every login except holders of the lockdown bypass; the flag survives restart.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_LOCKDOWN_BYPASS,
                        "Join the server while it is locked down (/lockdown). Held by staff who must stay reachable during a lockdown.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_MUTE,
                        "/mute <player> [duration] [reason] (/tempmute is the explicit duration alias); /mutehistory <player> reviews a player's full mute/unmute history.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_MUTELIST,
                        "/mutelist to review the players currently muted.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_SANCTION,
                        "/sanction <player>: aggregated read-only punishment summary: current mute, jail, ban state and active warning count.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_SUDO,
                        "/sudo <player> <command>: run a command as another player.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_TEMPBAN, "/tempban <player> <duration> [reason].", PermissionDefault.OP, MODERATION),
                PermissionSpec.of(
                        MODERATION_TEMPLATES,
                        "/punish <player> <template>: apply a configured punishment template (a preset reason + optional duration) as a ban or tempban.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_TOGGLEJAIL,
                        "/togglejail <player> [jail] [reason]: release the target if jailed, otherwise jail them in the named jail (or the default-jail setting).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(MODERATION_UNJAIL, "/unjail <player>.", PermissionDefault.OP, MODERATION),
                PermissionSpec.of(MODERATION_UNMUTE, "/unmute <player>.", PermissionDefault.OP, MODERATION),
                PermissionSpec.of(
                        MODERATION_WARN,
                        "/warn <player> [reason], /tempwarn <player> <duration> [reason], /warns <player> and /unwarn <player>: issue (standing or timed), review and clear warning history.",
                        PermissionDefault.OP,
                        MODERATION));
    }

    /** Reading the record, and the exemptions from it. */
    private static List<PermissionSpec> moderationReview() {
        return List.of(
                PermissionSpec.of(
                        MODERATION_BROADCAST_RECEIVE,
                        "Receive the staff sanction broadcast: the one-line announcement a non-silent /ban /mute /kick /warn emits. The -s flag (or broadcast.silent-by-default) suppresses it. Holders also read the line when a jailed player could not be taken to a jail on another server, which -s does not suppress. Duration tiers ride numbered nodes: uxmessentials.moderation.ban.maxduration.<seconds> and uxmessentials.moderation.mute.maxduration.<seconds> cap how long a ban/mute that holder may issue (highest held wins; no node = unlimited).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_CHECK,
                        "/checkban <player> and /checkmute <player>: report whether a player is currently banned or muted.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_COMMANDSPY,
                        "/commandspy (/cspy) to watch the commands other players run.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_EXEMPT,
                        "Cannot be banned, tempbanned, IP-banned, muted, warned, kicked, jailed or frozen by anyone, whatever their rank; also left online by /kickall and by an IP ban on their address.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_GUI,
                        "/mod opens the moderation management GUI (active punishments + per-player history).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_HISTORY,
                        "/history <player>: review a player's full disciplinary record (ban/mute/warn/kick) newest-first.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_SEEN,
                        "/seen, /seenip and /alts <player>: last-seen / last-IP lookup, surfaces alts.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_STAFFHISTORY,
                        "/staffhistory <staff>: review the sanctions a staff member has issued, newest-first.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_STAFFROLLBACK,
                        "/staffrollback <staff> [limit]: revoke a staff member's still-active sanctions (un-ban/un-mute/clear-warns the targets they sanctioned).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODERATION_STATS,
                        "/modstats [staff] [days]: staff punishment analytics: a most-active-staff leaderboard or a single staff member's breakdown, optionally over the last N days.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        MODULE_MODERATION,
                        "Hot-reload / inspect the moderation module (bans, mutes, jails, warnings and history).",
                        PermissionDefault.OP,
                        MODERATION));
    }

    private static List<PermissionSpec> security() {
        return List.of(
                PermissionSpec.of(
                        MODULE_SECURITY,
                        "Hot-reload / inspect the security module (2FA, op-protection, IP/alt guard).",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_2FA,
                        "/2fa: set up, confirm or disable an authenticator second factor on your own account.",
                        PermissionDefault.TRUE,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_ADMIN,
                        "/security: inspect and manage another player's second factors.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_ALTS,
                        "/ipalts: list the accounts that share an IP with a player.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_ALTS_NOTIFY,
                        "Receive the staff notice when a join shares an IP with other accounts or reports a flagged client.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_BYPASS,
                        "Exempt from the two-factor join verification and op-command re-auth checks.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_CLIENTINFO,
                        "/clientinfo: show the client brand a player reported.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_FORCE,
                        "/security force <player>: force a player to re-verify their second factor on their next action or join.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_PIN,
                        "/pin: set, change or remove a numeric PIN second factor on your own account.",
                        PermissionDefault.TRUE,
                        SECURITY),
                PermissionSpec.of(
                        SECURITY_PIN_REQUIRED, "Must set a PIN before playing.", PermissionDefault.FALSE, SECURITY),
                PermissionSpec.of(
                        SECURITY_RESET,
                        "/security reset <player> [totp|pin|all]: clear a factor a player can no longer prove.",
                        PermissionDefault.OP,
                        SECURITY));
    }

    private static List<PermissionSpec> staff() {
        return List.of(
                PermissionSpec.of(
                        MODULE_STAFF,
                        "Hot-reload / inspect the staff module (staff mode, its loadout and staff chat).",
                        PermissionDefault.OP,
                        STAFF),
                PermissionSpec.of(
                        STAFF_CHAT,
                        "/staffchat (alias /sc) to send and receive lines on the staff-only chat channel.",
                        PermissionDefault.OP,
                        STAFF),
                PermissionSpec.of(
                        STAFF_LIST,
                        "/stafflist to open the online-staff GUI (vanish-aware) and click a head to teleport to that staff member.",
                        PermissionDefault.OP,
                        STAFF),
                PermissionSpec.of(
                        STAFF_MODE,
                        "/staffmode [player] to flip into staff mode: your real loadout is saved and swapped for the gadget hotbar (and you vanish); leaving restores it. The EXAMINE gadget opens a player's inventory.",
                        PermissionDefault.OP,
                        STAFF));
    }

    private static List<PermissionSpec> invrollback() {
        return List.of(
                PermissionSpec.of(
                        INVROLLBACK_EXPORT,
                        "/invrestore export <player> <index> packs a snapshot into shulker boxes and gives them to you.",
                        PermissionDefault.OP,
                        INVROLLBACK),
                PermissionSpec.of(
                        INVROLLBACK_RESTORE,
                        "/invrestore <player> (and the invrollback entry on the /uxmess gui hub) opens the inventory-snapshot restore GUI for a player.",
                        PermissionDefault.OP,
                        INVROLLBACK),
                PermissionSpec.of(
                        INVROLLBACK_TELEPORT,
                        "/invrestore tp <player> <index> teleports you to where the snapshot was captured.",
                        PermissionDefault.OP,
                        INVROLLBACK),
                PermissionSpec.of(
                        MODULE_INVROLLBACK,
                        "Hot-reload / inspect the invrollback module (inventory snapshots and restore).",
                        PermissionDefault.OP,
                        INVROLLBACK));
    }

    private static List<PermissionSpec> vanish() {
        return List.of(
                PermissionSpec.of(
                        MODULE_VANISH, "Hot-reload / inspect the vanish module.", PermissionDefault.OP, VANISH),
                PermissionSpec.of(
                        VANISH_LIST,
                        "/vanish list to see the hidden players you are permitted to see (scoped to your see level).",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        VANISH_OTHERS,
                        "/vanish <player> to toggle another player's vanish.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        VANISH_PERSIST,
                        "Remain vanished across a relog instead of reappearing on join.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        VANISH_SEE,
                        "See other vanished players (staff-among-staff visibility) and target them with /tp.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.family(
                        VANISH_SEE_LEVEL + "<n>",
                        "How deeply you see: a viewer sees a vanished player when their see level reaches that "
                                + "player's use level; the largest level held wins and plain .see is level 1.",
                        PermissionDefault.OP,
                        PermissionShape.QUOTA,
                        VANISH),
                PermissionSpec.family(
                        VANISH_USE_LEVEL + "<n>",
                        "How deeply you vanish: only a viewer whose see level reaches this level finds you; the "
                                + "largest level held wins and plain .use is level 1.",
                        PermissionDefault.OP,
                        PermissionShape.QUOTA,
                        VANISH),
                PermissionSpec.of(
                        VANISH_SILENT,
                        "/vanish -s to vanish or reappear without the fake join/quit broadcast.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        VANISH_USE,
                        "/vanish to become invisible to other players; suppresses fake join/quit.",
                        PermissionDefault.OP,
                        VANISH));
    }

    private static List<PermissionSpec> commandcontrol() {
        return List.of(
                PermissionSpec.of(
                        COMMANDCONTROL_BYPASS,
                        "Exempt from the command whitelist/blacklist gate and the tab-completion / plugin-hide scrub.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        COMMANDCONTROL_CHANNELHIDE_BYPASS,
                        "Exempt from the plugin-channel hider - the full channel-registration list is sent to this player.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        COMMANDCONTROL_SPAM_BYPASS,
                        "Exempt from the command-spam rate limiter - commands are never counted and no spam action fires.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        COMMANDCONTROL_VIEWPLUGINS,
                        "See the plugin-listing / help commands (/plugins, /pl, /help, ...) hidden by the plugin-hide feature.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        COMMANDCONTROL_VIEWPROXYCOMMANDS,
                        "Checked on the Velocity proxy: see and run the proxy commands the proxy plugin-hide removes and blocks.",
                        PermissionDefault.FALSE,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        MODULE_COMMANDCONTROL,
                        "Hot-reload / inspect the commandcontrol module (command whitelist, tab-completion filter, plugin-hide).",
                        PermissionDefault.OP,
                        COMMANDCONTROL));
    }
}
