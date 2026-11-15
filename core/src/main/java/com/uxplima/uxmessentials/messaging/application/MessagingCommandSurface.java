package com.uxplima.uxmessentials.messaging.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;

/**
 * The messaging context's command surface as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The messaging inbound adapter realises the Brigadier
 * node from the started module's context on the next {@code COMMANDS} fire. Collected here so
 * {@code MessagingModule} stays small and the command/permission pairing is one greppable table.
 *
 * <p>Only {@code /msg} {@code /reply} {@code /mail} are the private-channel surface; {@code /msgtoggle}
 * {@code /ignore} {@code /unignore} {@code /ignorelist} {@code /socialspy} {@code /mailclear} {@code /helpop}
 * round it out. There is no public-chat command: that is out of charter.
 */
final class MessagingCommandSurface {

    private MessagingCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("msg", SocialPermissions.MSG_USE, command("msg", "Send a private message")),
                spec("reply", SocialPermissions.MSG_REPLY, command("reply", "Reply to your last conversation")),
                spec("mail", SocialPermissions.MAIL_USE, command("mail", "Read, send or clear your mail")),
                spec(
                        "msgtoggle",
                        SocialPermissions.MSG_TOGGLE,
                        command("msgtoggle", "Refuse incoming private messages")),
                spec("rtoggle", SocialPermissions.MSG_TOGGLE, command("rtoggle", "Toggle whether you receive replies")),
                spec("ignore", SocialPermissions.MSG_IGNORE, command("ignore", "Ignore a player")),
                spec("unignore", SocialPermissions.MSG_IGNORE, command("unignore", "Stop ignoring a player")),
                spec("ignorelist", SocialPermissions.MSG_IGNORE, command("ignorelist", "List the players you ignore")),
                spec(
                        "socialspy",
                        SocialPermissions.MSG_SOCIALSPY,
                        command("socialspy", "Observe private messages, globally or for one player")),
                spec("mailclear", SocialPermissions.MAIL_USE, command("mailclear", "Clear your mailbox")),
                spec("helpop", SocialPermissions.HELPOP_USE, command("helpop", "Open a staff support request")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand command(String literal, String description) {
        return new MessagingCommand(literal, description);
    }

    /** The kernel-side description of one messaging command, literal and help text, no Brigadier type. */
    private record MessagingCommand(String literal, String description) implements BrigadierCommand {}
}
