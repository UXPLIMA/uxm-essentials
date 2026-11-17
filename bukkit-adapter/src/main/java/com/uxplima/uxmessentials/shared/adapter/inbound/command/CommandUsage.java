package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The one usage line: {@code /pay <player> <amount> [currency] • Transfer funds to another player.}
 *
 * <p>The description is the catalogue's {@code describe.<command>} line in the reader's language, the root of a
 * nested command describing it, and the English the code passes stands in only where no catalogue has a line. A
 * command family that wrote this line itself with the English in the code would show a reader of another language
 * English, without the lettering the catalogue gives every other line.
 */
@NullMarked
public final class CommandUsage {

    private CommandUsage() {}

    /**
     * Send the usage line for {@code command} to {@code sender}.
     *
     * @param command the command as typed, its root first: {@code pay} or {@code hologram create}
     * @param usage the arguments after it: {@code <player> <amount> [currency]}
     * @param english the description when no catalogue has a line for the command
     */
    public static void send(Messages messages, CommandSender sender, String command, String usage, String english) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(english, "english");
        // The three values are text and never markup: the usage is syntax, and an argument named after a colour
        // role, <value>, would be read as that colour and vanish from the line. The line escapes them as it fills
        // them in, against the theme's roles too.
        PlayerRef reader =
                sender instanceof Player player ? BukkitRefs.toRef(player) : PlayerRef.system(sender.getName());
        String described = CommandDescriptions.of(messages, reader, root(command), english);
        Map<String, String> placeholders = Map.of(
                "command", command,
                "usage", usage,
                "description", described);
        String rendered = messages.resolve(reader, SharedMessageKey.COMMAND_USAGE, placeholders);
        sender.sendMessage(StyledText.render(rendered));
    }

    private static String root(String command) {
        int space = command.indexOf(' ');
        return space < 0 ? command : command.substring(0, space);
    }
}
