package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;

/**
 * The moderation context's command surface as platform-neutral {@link CommandSpec}s: each pairs a literal with its
 * permission node and a factory that produces the kernel-side {@link BrigadierCommand} description. The inbound adapter
 * realises the Brigadier node from the started module's context on the next {@code COMMANDS} fire. Collected here so
 * the command/permission pairing is one greppable table.
 *
 * <p>Every command is permission-gated and every state-mutating one is audit-logged in its use case;
 * {@code /tempmute} is the explicit-duration alias of {@code /mute}, {@code /kickall} shares the kick node,
 * {@code /warns} and {@code /unwarn} share the warn node, {@code /jails}, {@code /jailedplayers} and
 * {@code /setjail} share the jail node (as does {@code /jail del}, a subcommand of {@code /jail} rather than a
 * separate literal), {@code /tempbanip} and {@code /unbanip} share the banip node, {@code /unfreeze} shares the
 * freeze node, and {@code /checkban} and {@code /checkmute} share the check node, matching the node table.
 */
final class ModerationCommandSurface {

    private ModerationCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("mute", EnforcementPermissions.MODERATION_MUTE, "Mute a player"),
                spec("tempmute", EnforcementPermissions.MODERATION_MUTE, "Mute a player for a duration"),
                spec("unmute", EnforcementPermissions.MODERATION_UNMUTE, "Lift a player's mute"),
                spec("jail", EnforcementPermissions.MODERATION_JAIL, "Confine a player to a jail"),
                spec("unjail", EnforcementPermissions.MODERATION_UNJAIL, "Release a jailed player"),
                spec("togglejail", EnforcementPermissions.MODERATION_TOGGLEJAIL, "Toggle a player's jail"),
                spec("jails", EnforcementPermissions.MODERATION_JAIL, "List the configured jails"),
                spec("jailedplayers", EnforcementPermissions.MODERATION_JAIL, "List the players currently jailed"),
                spec("setjail", EnforcementPermissions.MODERATION_JAIL, "Define a jail at your location"),
                spec("tempban", EnforcementPermissions.MODERATION_TEMPBAN, "Ban a player for a duration"),
                spec("ban", EnforcementPermissions.MODERATION_BAN, "Permanently ban a player"),
                spec("punish", EnforcementPermissions.MODERATION_TEMPLATES, "Apply a configured punishment template"),
                spec("unban", EnforcementPermissions.MODERATION_BAN, "Lift a player's permanent ban"),
                spec("banlist", EnforcementPermissions.MODERATION_BANLIST, "Review currently banned players"),
                spec("mutelist", EnforcementPermissions.MODERATION_MUTELIST, "Review currently muted players"),
                spec("banhistory", EnforcementPermissions.MODERATION_BAN, "Review a player's ban history"),
                spec("mutehistory", EnforcementPermissions.MODERATION_MUTE, "Review a player's mute history"),
                spec("history", EnforcementPermissions.MODERATION_HISTORY, "Review a player's full sanction history"),
                spec(
                        "staffhistory",
                        EnforcementPermissions.MODERATION_STAFFHISTORY,
                        "Review the sanctions a staff member issued"),
                spec(
                        "staffrollback",
                        EnforcementPermissions.MODERATION_STAFFROLLBACK,
                        "Revoke a staff member's still-active sanctions"),
                spec("modstats", EnforcementPermissions.MODERATION_STATS, "Show staff punishment analytics"),
                spec("checkban", EnforcementPermissions.MODERATION_CHECK, "Check whether a player is banned"),
                spec("checkmute", EnforcementPermissions.MODERATION_CHECK, "Check whether a player is muted"),
                spec("kick", EnforcementPermissions.MODERATION_KICK, "Kick a player"),
                spec("kickall", EnforcementPermissions.MODERATION_KICK, "Kick all non-exempt players"),
                spec("warn", EnforcementPermissions.MODERATION_WARN, "Warn a player"),
                spec("tempwarn", EnforcementPermissions.MODERATION_WARN, "Warn a player for a duration"),
                spec("warns", EnforcementPermissions.MODERATION_WARN, "Review a player's warnings"),
                spec("unwarn", EnforcementPermissions.MODERATION_WARN, "Clear a player's warnings"),
                spec("sanction", EnforcementPermissions.MODERATION_SANCTION, "Show a player's punishment summary"),
                spec("banip", EnforcementPermissions.MODERATION_BANIP, "Ban a player or IP by address"),
                spec("tempbanip", EnforcementPermissions.MODERATION_BANIP, "Ban an IP for a duration"),
                spec("unbanip", EnforcementPermissions.MODERATION_BANIP, "Lift an IP ban"),
                spec("freeze", EnforcementPermissions.MODERATION_FREEZE, "Freeze a player in place"),
                spec("unfreeze", EnforcementPermissions.MODERATION_FREEZE, "Release a frozen player"),
                spec("seen", EnforcementPermissions.MODERATION_SEEN, "Show a player's last-seen"),
                spec("seenip", EnforcementPermissions.MODERATION_SEEN, "Show a player's last IP and alts"),
                spec("alts", EnforcementPermissions.MODERATION_SEEN, "List accounts sharing a player's IP"),
                spec("commandspy", EnforcementPermissions.MODERATION_COMMANDSPY, "Watch the commands players run"),
                spec("lockdown", EnforcementPermissions.MODERATION_LOCKDOWN, "Lock the server to bypass holders only"),
                spec("sudo", EnforcementPermissions.MODERATION_SUDO, "Run a command as another player"));
    }

    private static CommandSpec spec(String literal, String permission, String description) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> new ModerationCommand(literal, description);
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one moderation command, literal and help text, no Brigadier type. */
    private record ModerationCommand(String literal, String description) implements BrigadierCommand {}
}
