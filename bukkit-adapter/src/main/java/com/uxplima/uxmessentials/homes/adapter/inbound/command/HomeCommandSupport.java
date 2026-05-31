package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every homes Brigadier command holds: the constructed {@link HomeServices} and the
 * {@link Messages} catalog (the latter only for the players-only rejection a console may see — all
 * player-facing feedback flows through the use cases' {@code MessageSink}). Concrete command classes
 * extend this so each stays focused on building its node and mapping one argument to one use-case call.
 */
@NullMarked
abstract class HomeCommandSupport {

    /** The catalog key for a console invoking a player-only command. */
    static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    final HomeServices services;
    final Messages messages;

    HomeCommandSupport(HomeServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(messages.resolve(consoleRef(sender), PLAYERS_ONLY, java.util.Map.of())));
        return null;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /**
     * The player's current {@link com.uxplima.uxmessentials.shared.domain.Position}. Paper marks
     * {@code Player#getLocation()} nullable (null only for an entity with no world, which a connected
     * player never is), so the non-null assertion lives here once rather than at every command.
     */
    static com.uxplima.uxmessentials.shared.domain.Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }

    private static PlayerRef consoleRef(CommandSender sender) {
        return new PlayerRef(new java.util.UUID(0L, 0L), sender.getName());
    }
}
