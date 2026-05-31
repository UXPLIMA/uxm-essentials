package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators the communication Brigadier commands hold: the {@link Messages} catalog, used only for the
 * players-only rejection — every other reply flows through a use case's {@code MessageSink} (the toggle
 * confirmation) or the info sender (an info page). Concrete command classes extend this so each stays focused on
 * building its node and mapping its source to one call. Both surfaces act on the invoking player only.
 */
@NullMarked
abstract class CommunicationCommandSupport {

    final Messages messages;

    CommunicationCommandSupport(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(messages.resolve(consoleRef(sender), SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of())));
        return null;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    private static PlayerRef consoleRef(CommandSender sender) {
        return new PlayerRef(new UUID(0L, 0L), sender.getName());
    }
}
