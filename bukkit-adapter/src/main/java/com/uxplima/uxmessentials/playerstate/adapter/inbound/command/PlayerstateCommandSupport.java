package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every playerstate Brigadier command holds: the constructed {@link PlayerStateServices}
 * and the {@link Messages} catalog (the latter only for the players-only, unknown-player, and
 * no-permission-for-others rejections — all success feedback flows through the use cases' {@code MessageSink}).
 * Concrete command classes extend this so each stays focused on building its node and mapping its arguments to
 * one use-case call.
 *
 * <p>The {@code .others} target form is gated here by the single shared {@code uxmessentials.playerstate.others}
 * node: {@link #resolveTarget} resolves a named player only when the sender holds it, returning the sender
 * themselves when no name is given. This keeps the per-command nodes to the base verb and the cross-cutting
 * others node, matching docs/permissions.md.
 */
@NullMarked
abstract class PlayerstateCommandSupport {

    /** The cross-cutting node that lets a command target a player other than the sender. */
    static final String OTHERS_PERMISSION = "uxmessentials.playerstate.others";

    private static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;
    private static final MessageKey UNKNOWN_PLAYER = SharedMessageKey.COMMAND_UNKNOWN_PLAYER;
    private static final MessageKey NO_PERMISSION = SharedMessageKey.COMMAND_NO_PERMISSION;

    final PlayerStateServices services;
    final Messages messages;
    final CommandFeedback feedback;

    PlayerstateCommandSupport(PlayerStateServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.feedback = new CommandFeedback(messages);
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        reply(sender, PLAYERS_ONLY, Map.of());
        return null;
    }

    /**
     * Resolve the optional {@code player} argument to a target. When the argument is absent the sender is the
     * target; when present, the named player is resolved only if the sender holds {@link #OTHERS_PERMISSION}
     * and is online — otherwise the matching rejection is sent and an empty result returned.
     */
    final Optional<PlayerRef> resolveTarget(CommandContext<CommandSourceStack> ctx, Player sender) {
        if (!hasArgument(ctx, "player")) {
            return Optional.of(BukkitRefs.toRef(sender));
        }
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            reply(sender, NO_PERMISSION, Map.of());
            return Optional.empty();
        }
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            reply(sender, UNKNOWN_PLAYER, Map.of("player", name));
        }
        return target;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    private boolean hasArgument(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            ctx.getArgument(name, String.class);
            return true;
        } catch (IllegalArgumentException absent) {
            return false;
        }
    }

    private void reply(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        feedback.send(sender, key, placeholders);
    }
}
