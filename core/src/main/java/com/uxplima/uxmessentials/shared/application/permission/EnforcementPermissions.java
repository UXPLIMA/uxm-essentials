package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that police a server. Data, not logic: one row per node, read by {@link
 * PermissionCatalog} and through it by the server registration, the reference page and the in-game listing.
 */
final class EnforcementPermissions {

    private static final ModuleId MODERATION = ModuleId.of("moderation");
    private static final ModuleId SECURITY = ModuleId.of("security");
    private static final ModuleId STAFF = ModuleId.of("staff");
    private static final ModuleId INVROLLBACK = ModuleId.of("invrollback");
    private static final ModuleId VANISH = ModuleId.of("vanish");
    private static final ModuleId COMMANDCONTROL = ModuleId.of("commandcontrol");

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
                        "uxmessentials.moderation.<sanction>.maxduration.<seconds>",
                        "The longest ban, mute or jail you may hand out, in seconds; the largest tier held wins.",
                        PermissionDefault.OP,
                        PermissionShape.TIER,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.ban",
                        "/ban <player> [reason] and /unban <player>: permanent UUID ban and its lift; /banhistory <player> reviews a player's full ban/unban history.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.banip",
                        "/banip <player|ip> [reason] / /unbanip <ip>: IP ban with stored-IP alt detection.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.banlist",
                        "/banlist to review the players currently banned.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.freeze",
                        "/freeze <player> / /unfreeze <player>: pin a player in place pending review.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.jail",
                        "/jail <player> <jail> [duration] [reason]; /jails lists configured jails; /jailedplayers lists who is jailed; /setjail <name> defines a jail at your location; /jail del <name> removes a defined jail.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.kick",
                        "/kick <player> [reason]; /kickall [reason] to clear non-exempt players.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.lockdown",
                        "/lockdown [on|off]: refuse every login except holders of the lockdown bypass; the flag survives restart.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.lockdown.bypass",
                        "Join the server while it is locked down (/lockdown). Held by staff who must stay reachable during a lockdown.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.mute",
                        "/mute <player> [duration] [reason] (/tempmute is the explicit duration alias); /mutehistory <player> reviews a player's full mute/unmute history.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.mutelist",
                        "/mutelist to review the players currently muted.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.sanction",
                        "/sanction <player>: aggregated read-only punishment summary: current mute, jail, ban state and active warning count.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.sudo",
                        "/sudo <player> <command>: run a command as another player.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.tempban",
                        "/tempban <player> <duration> [reason].",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.templates",
                        "/punish <player> <template>: apply a configured punishment template (a preset reason + optional duration) as a ban or tempban.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.togglejail",
                        "/togglejail <player> [jail] [reason]: release the target if jailed, otherwise jail them in the named jail (or the first configured jail).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.unjail", "/unjail <player>.", PermissionDefault.OP, MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.unmute", "/unmute <player>.", PermissionDefault.OP, MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.warn",
                        "/warn <player> [reason], /tempwarn <player> <duration> [reason], /warns <player> and /unwarn <player>: issue (standing or timed), review and clear warning history.",
                        PermissionDefault.OP,
                        MODERATION));
    }

    /** Reading the record, and the exemptions from it. */
    private static List<PermissionSpec> moderationReview() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.moderation.broadcast.receive",
                        "Receive the staff sanction broadcast: the one-line announcement a non-silent /ban /mute /kick /warn emits. The -s flag (or broadcast.silent-by-default) suppresses it. Duration tiers ride numbered nodes: uxmessentials.moderation.ban.maxduration.<seconds> and uxmessentials.moderation.mute.maxduration.<seconds> cap how long a ban/mute that holder may issue (highest held wins; no node = unlimited).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.check",
                        "/checkban <player> and /checkmute <player>: report whether a player is currently banned or muted.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.commandspy",
                        "/commandspy (/cspy) to watch the commands other players run.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.exempt",
                        "Cannot be muted/jailed/tempbanned/kicked/warned/IP-banned/frozen by lower staff.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.gui",
                        "/mod opens the moderation management GUI (active punishments + per-player history).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.history",
                        "/history <player>: review a player's full disciplinary record (ban/mute/warn/kick) newest-first.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.seen",
                        "/seen, /seenip and /alts <player>: last-seen / last-IP lookup, surfaces alts.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.staffhistory",
                        "/staffhistory <staff>: review the sanctions a staff member has issued, newest-first.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.staffrollback",
                        "/staffrollback <staff> [limit]: revoke a staff member's still-active sanctions (un-ban/un-mute/clear-warns the targets they sanctioned).",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.moderation.stats",
                        "/modstats [staff] [days]: staff punishment analytics: a most-active-staff leaderboard or a single staff member's breakdown, optionally over the last N days.",
                        PermissionDefault.OP,
                        MODERATION),
                PermissionSpec.of(
                        "uxmessentials.module.moderation",
                        "Hot-reload / inspect the moderation module (bans, mutes, jails, warnings and history).",
                        PermissionDefault.OP,
                        MODERATION));
    }

    private static List<PermissionSpec> security() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.security",
                        "Hot-reload / inspect the security module (2FA, op-protection, IP/alt guard).",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.2fa",
                        "/2fa: set up, confirm or disable an authenticator second factor on your own account.",
                        PermissionDefault.TRUE,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.admin",
                        "/security: inspect and manage another player's second factors.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.alts",
                        "/ipalts: list the accounts that share an IP with a player.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.alts.notify",
                        "Receive the staff notice when a join shares an IP with other accounts or reports a flagged client.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.bypass",
                        "Exempt from the two-factor join verification and op-command re-auth checks.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.clientinfo",
                        "/clientinfo: show the client brand a player reported.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.force",
                        "/security force <player>: force a player to re-verify their second factor on their next action or join.",
                        PermissionDefault.OP,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.pin",
                        "/pin: set, change or remove a numeric PIN second factor on your own account.",
                        PermissionDefault.TRUE,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.pin.required",
                        "Must set a PIN before playing.",
                        PermissionDefault.FALSE,
                        SECURITY),
                PermissionSpec.of(
                        "uxmessentials.security.reset",
                        "/security reset <player> [totp|pin|all]: clear a factor a player can no longer prove.",
                        PermissionDefault.OP,
                        SECURITY));
    }

    private static List<PermissionSpec> staff() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.staff",
                        "Hot-reload / inspect the staff module (staff mode, its loadout and staff chat).",
                        PermissionDefault.OP,
                        STAFF),
                PermissionSpec.of(
                        "uxmessentials.staff.chat",
                        "/staffchat (alias /sc) to send and receive lines on the staff-only chat channel.",
                        PermissionDefault.OP,
                        STAFF),
                PermissionSpec.of(
                        "uxmessentials.staff.list",
                        "/stafflist to open the online-staff GUI (vanish-aware) and click a head to teleport to that staff member.",
                        PermissionDefault.OP,
                        STAFF),
                PermissionSpec.of(
                        "uxmessentials.staff.mode",
                        "/staffmode [player] to flip into staff mode: your real loadout is saved and swapped for the gadget hotbar (and you vanish); leaving restores it. The EXAMINE gadget opens a player's inventory.",
                        PermissionDefault.OP,
                        STAFF));
    }

    private static List<PermissionSpec> invrollback() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.invrollback.export",
                        "/invrestore export <player> <index> packs a snapshot into shulker boxes and gives them to you.",
                        PermissionDefault.OP,
                        INVROLLBACK),
                PermissionSpec.of(
                        "uxmessentials.invrollback.restore",
                        "/invrestore <player> opens the inventory-snapshot restore GUI for a player.",
                        PermissionDefault.OP,
                        INVROLLBACK),
                PermissionSpec.of(
                        "uxmessentials.invrollback.teleport",
                        "/invrestore tp <player> <index> teleports you to where the snapshot was captured.",
                        PermissionDefault.OP,
                        INVROLLBACK),
                PermissionSpec.of(
                        "uxmessentials.module.invrollback",
                        "Hot-reload / inspect the invrollback module (inventory snapshots and restore).",
                        PermissionDefault.OP,
                        INVROLLBACK));
    }

    private static List<PermissionSpec> vanish() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.vanish",
                        "Hot-reload / inspect the vanish module.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        "uxmessentials.vanish.list",
                        "/vanish list to see the hidden players you are permitted to see (scoped to your see level).",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        "uxmessentials.vanish.others",
                        "/vanish <player> to toggle another player's vanish.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        "uxmessentials.vanish.persist",
                        "Remain vanished across a relog instead of reappearing on join.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        "uxmessentials.vanish.see",
                        "See other vanished players (staff-among-staff visibility) and target them with /tp.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.family(
                        "uxmessentials.vanish.see.level<n>",
                        "How deeply you see: a viewer sees a vanished player when their see level reaches that "
                                + "player's use level; the largest level held wins and plain .see is level 1.",
                        PermissionDefault.OP,
                        PermissionShape.QUOTA,
                        VANISH),
                PermissionSpec.family(
                        "uxmessentials.vanish.use.level<n>",
                        "How deeply you vanish: only a viewer whose see level reaches this level finds you; the "
                                + "largest level held wins and plain .use is level 1.",
                        PermissionDefault.OP,
                        PermissionShape.QUOTA,
                        VANISH),
                PermissionSpec.of(
                        "uxmessentials.vanish.silent",
                        "/vanish -s to vanish or reappear without the fake join/quit broadcast.",
                        PermissionDefault.OP,
                        VANISH),
                PermissionSpec.of(
                        "uxmessentials.vanish.use",
                        "/vanish to become invisible to other players; suppresses fake join/quit.",
                        PermissionDefault.OP,
                        VANISH));
    }

    private static List<PermissionSpec> commandcontrol() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.commandcontrol.bypass",
                        "Exempt from the command whitelist/blacklist gate and the tab-completion / plugin-hide scrub.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        "uxmessentials.commandcontrol.channelhide.bypass",
                        "Exempt from the plugin-channel hider - the full channel-registration list is sent to this player.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        "uxmessentials.commandcontrol.spam.bypass",
                        "Exempt from the command-spam rate limiter - commands are never counted and no spam action fires.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        "uxmessentials.commandcontrol.viewplugins",
                        "See the plugin-listing / help commands (/plugins, /pl, /help, ...) hidden by the plugin-hide feature.",
                        PermissionDefault.OP,
                        COMMANDCONTROL),
                PermissionSpec.of(
                        "uxmessentials.module.commandcontrol",
                        "Hot-reload / inspect the commandcontrol module (command whitelist, tab-completion filter, plugin-hide).",
                        PermissionDefault.OP,
                        COMMANDCONTROL));
    }
}
