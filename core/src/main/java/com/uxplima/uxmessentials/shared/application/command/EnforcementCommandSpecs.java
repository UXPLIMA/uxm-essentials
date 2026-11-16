package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;

/** Moderation, staff tools, account security, vanish and inventory rollback. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class EnforcementCommandSpecs {

    private static final ModuleId MODERATION = ModuleId.of("moderation");
    private static final ModuleId STAFF = ModuleId.of("staff");
    private static final ModuleId SECURITY = ModuleId.of("security");
    private static final ModuleId VANISH = ModuleId.of("vanish");
    private static final ModuleId INVROLLBACK = ModuleId.of("invrollback");

    private EnforcementCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(MODERATION, "alts", EnforcementPermissions.MODERATION_SEEN),
                CommandSpec.of(MODERATION, "ban", EnforcementPermissions.MODERATION_BAN),
                CommandSpec.of(MODERATION, "banhistory", EnforcementPermissions.MODERATION_BAN),
                CommandSpec.of(MODERATION, "banip", EnforcementPermissions.MODERATION_BANIP),
                CommandSpec.of(MODERATION, "banlist", EnforcementPermissions.MODERATION_BANLIST),
                CommandSpec.of(MODERATION, "checkban", EnforcementPermissions.MODERATION_CHECK),
                CommandSpec.of(MODERATION, "checkmute", EnforcementPermissions.MODERATION_CHECK),
                CommandSpec.of(MODERATION, "commandspy", EnforcementPermissions.MODERATION_COMMANDSPY, "cspy"),
                CommandSpec.of(MODERATION, "freeze", EnforcementPermissions.MODERATION_FREEZE),
                CommandSpec.of(MODERATION, "history", EnforcementPermissions.MODERATION_HISTORY),
                CommandSpec.of(MODERATION, "jail", EnforcementPermissions.MODERATION_JAIL),
                CommandSpec.of(MODERATION, "jailedplayers", EnforcementPermissions.MODERATION_JAIL),
                CommandSpec.of(MODERATION, "jails", EnforcementPermissions.MODERATION_JAIL),
                CommandSpec.of(MODERATION, "kick", EnforcementPermissions.MODERATION_KICK),
                CommandSpec.of(MODERATION, "kickall", EnforcementPermissions.MODERATION_KICK),
                CommandSpec.of(MODERATION, "lockdown", EnforcementPermissions.MODERATION_LOCKDOWN),
                CommandSpec.of(MODERATION, "mod", EnforcementPermissions.MODERATION_GUI),
                CommandSpec.of(MODERATION, "modstats", EnforcementPermissions.MODERATION_STATS),
                CommandSpec.of(MODERATION, "mute", EnforcementPermissions.MODERATION_MUTE),
                CommandSpec.of(MODERATION, "mutehistory", EnforcementPermissions.MODERATION_MUTE),
                CommandSpec.of(MODERATION, "mutelist", EnforcementPermissions.MODERATION_MUTELIST),
                CommandSpec.of(MODERATION, "punish", EnforcementPermissions.MODERATION_TEMPLATES),
                CommandSpec.of(MODERATION, "sanction", EnforcementPermissions.MODERATION_SANCTION),
                CommandSpec.of(MODERATION, "seen", EnforcementPermissions.MODERATION_SEEN),
                CommandSpec.of(MODERATION, "seenip", EnforcementPermissions.MODERATION_SEEN),
                CommandSpec.of(MODERATION, "setjail", EnforcementPermissions.MODERATION_JAIL),
                CommandSpec.of(MODERATION, "staffhistory", EnforcementPermissions.MODERATION_STAFFHISTORY),
                CommandSpec.of(MODERATION, "staffrollback", EnforcementPermissions.MODERATION_STAFFROLLBACK),
                CommandSpec.of(MODERATION, "sudo", EnforcementPermissions.MODERATION_SUDO),
                CommandSpec.of(MODERATION, "tempban", EnforcementPermissions.MODERATION_TEMPBAN),
                CommandSpec.of(MODERATION, "tempbanip", EnforcementPermissions.MODERATION_BANIP),
                CommandSpec.of(MODERATION, "tempmute", EnforcementPermissions.MODERATION_MUTE),
                CommandSpec.of(MODERATION, "tempwarn", EnforcementPermissions.MODERATION_WARN),
                CommandSpec.of(MODERATION, "togglejail", EnforcementPermissions.MODERATION_TOGGLEJAIL),
                CommandSpec.of(MODERATION, "unban", EnforcementPermissions.MODERATION_BAN),
                CommandSpec.of(MODERATION, "unbanip", EnforcementPermissions.MODERATION_BANIP),
                CommandSpec.of(MODERATION, "unfreeze", EnforcementPermissions.MODERATION_FREEZE),
                CommandSpec.of(MODERATION, "unjail", EnforcementPermissions.MODERATION_UNJAIL),
                CommandSpec.of(MODERATION, "unmute", EnforcementPermissions.MODERATION_UNMUTE),
                CommandSpec.of(MODERATION, "unwarn", EnforcementPermissions.MODERATION_WARN),
                CommandSpec.of(MODERATION, "warn", EnforcementPermissions.MODERATION_WARN),
                CommandSpec.of(MODERATION, "warns", EnforcementPermissions.MODERATION_WARN),
                CommandSpec.of(STAFF, "staffchat", EnforcementPermissions.STAFF_CHAT, "sc"),
                CommandSpec.of(STAFF, "stafflist", EnforcementPermissions.STAFF_LIST),
                CommandSpec.of(STAFF, "staffmode", EnforcementPermissions.STAFF_MODE),
                CommandSpec.of(SECURITY, "clientinfo", EnforcementPermissions.SECURITY_CLIENTINFO),
                CommandSpec.of(SECURITY, "ipalts", EnforcementPermissions.SECURITY_ALTS),
                CommandSpec.of(SECURITY, "pin", EnforcementPermissions.SECURITY_PIN),
                CommandSpec.of(SECURITY, "security", EnforcementPermissions.SECURITY_ADMIN),
                CommandSpec.named(SECURITY, "twofactor", "2fa", EnforcementPermissions.SECURITY_2FA, "twofactor"),
                CommandSpec.of(VANISH, "vanish", EnforcementPermissions.VANISH_USE, "v"),
                CommandSpec.of(INVROLLBACK, "invrestore", EnforcementPermissions.INVROLLBACK_RESTORE));
    }
}
