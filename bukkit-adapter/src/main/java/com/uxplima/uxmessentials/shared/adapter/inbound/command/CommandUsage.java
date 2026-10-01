package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The one usage line: {@code /pay <player> <amount> [currency] • Transfer funds to another player.}
 *
 * <p>The description is the catalogue's {@code describe.<command>} line in the reader's language, the root of a
 * nested command describing it, and the English the code passes stands in only where no catalogue has a line. Eight
 * command families used to write this line themselves with the English in the code, so a reader of another language
 * read English and the line missed the lettering the catalogue gives every other one.
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
        // role, <value>, was read as that colour and vanished from the line. Escaped against the theme's roles too,
        // because MiniMessage has never heard of them.
        MiniMessage words = MiniMessage.miniMessage();
        PlayerRef reader =
                sender instanceof Player player ? BukkitRefs.toRef(player) : PlayerRef.system(sender.getName());
        String described = CommandDescriptions.of(messages, reader, root(command), english);
        Map<String, String> placeholders = Map.of(
                "command", words.escapeTags(command, StyleTags.resolver()),
                "usage", words.escapeTags(usage, StyleTags.resolver()),
                "description", words.escapeTags(described, StyleTags.resolver()));
        String rendered = messages.resolve(reader, SharedMessageKey.COMMAND_USAGE, placeholders);
        sender.sendMessage(words.deserialize(rendered, StyleTags.resolver()));
    }

    private static String root(String command) {
        int space = command.indexOf(' ');
        return space < 0 ? command : command.substring(0, space);
    }
}
