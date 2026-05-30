package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every teleport Brigadier command holds: the constructed {@link TeleportServices}
 * and the {@link Messages} catalog (the latter only for the players-only rejection a console may see —
 * all player-facing feedback flows through the use cases' {@code MessageSink}). Concrete command classes
 * extend this so each stays focused on building its node and mapping one argument to one use-case call.
 */
@NullMarked
abstract class TeleportCommandSupport {

    final TeleportServices services;
    final Messages messages;

    TeleportCommandSupport(TeleportServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        return TeleportSenders.requirePlayer(ctx, messages);
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return TeleportSenders.refOf(player);
    }

    /** Resolve a named, currently-online player to a {@link PlayerRef} and notify the actor on failure. */
    final Optional<PlayerRef> onlineTarget(PlayerRef actor, String name) {
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            services.notifier()
                    .send(actor, TeleportError.TARGET_OFFLINE.messageKey(), java.util.Map.of("player", name));
        }
        return target;
    }
}
