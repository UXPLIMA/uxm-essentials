package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A command's description in the reader's language: the {@code describe.<command id>} line of their catalogue, or the
 * code's English where the catalogue has none.
 *
 * <p>Every description used to be the English a command class returns, so a Turkish player's /help and every usage
 * line were English. The code's English stays, because Paper registers it as the command's own description and a
 * command an operator adds has no catalogue line; the catalogue line is what a player reads.
 */
@NullMarked
public final class CommandDescriptions {

    /** The prefix of a description line: {@code describe.home} describes {@code /home}. */
    public static final String PREFIX = "describe.";

    private CommandDescriptions() {}

    /** The description of {@code commandId} for {@code viewer}, or {@code english} when no catalogue has a line. */
    public static String of(Messages messages, PlayerRef viewer, String commandId, String english) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(english, "english");
        String key = PREFIX + commandId;
        String line = messages.resolve(viewer, new DescriptionKey(key), Map.of());
        return line.isBlank() || line.equals(key) ? english : line;
    }

    /** A description line's key. The catalogue answers a key it does not hold with the key itself. */
    private record DescriptionKey(String key) implements MessageKey {}
}
