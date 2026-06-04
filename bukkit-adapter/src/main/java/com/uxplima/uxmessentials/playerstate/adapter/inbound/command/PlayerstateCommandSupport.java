package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
     * target; when present (a name or an {@code @a}/{@code @p}/{@code @s}/{@code @r} selector), the resolved
     * player is returned only if the sender holds {@link #OTHERS_PERMISSION} — otherwise the no-permission
     * rejection is sent. A selector that matches no online player yields the unknown-player rejection. The
     * argument is a {@link io.papermc.paper.command.brigadier.argument.ArgumentTypes#player()} selector, so a
     * present value is always an online player; the first match is taken for a single-target verb.
     */
    final Optional<PlayerRef> resolveTarget(CommandContext<CommandSourceStack> ctx, Player sender) {
        Optional<PlayerSelectorArgumentResolver> resolver = selector(ctx);
        if (resolver.isEmpty()) {
            return Optional.of(BukkitRefs.toRef(sender));
        }
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            reply(sender, NO_PERMISSION, Map.of());
            return Optional.empty();
        }
        return resolveSelector(resolver.get(), ctx, sender);
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    private Optional<PlayerRef> resolveSelector(
            PlayerSelectorArgumentResolver resolver, CommandContext<CommandSourceStack> ctx, Player sender) {
        try {
            List<Player> resolved = resolver.resolve(ctx.getSource());
            if (resolved.isEmpty()) {
                reply(sender, UNKNOWN_PLAYER, Map.of("player", typedTarget(ctx)));
                return Optional.empty();
            }
            return Optional.of(BukkitRefs.toRef(resolved.get(0)));
        } catch (CommandSyntaxException unmatched) {
            // A name with no online player, or a selector that matched nothing — surfaced to the sender as the
            // same unknown-player rejection the name path used, never as a raw Brigadier parse error.
            reply(sender, UNKNOWN_PLAYER, Map.of("player", typedTarget(ctx)));
            return Optional.empty();
        }
    }

    /** The raw text the sender typed for the {@code player} argument, for the unknown-player rejection. */
    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }

    private Optional<PlayerSelectorArgumentResolver> selector(CommandContext<CommandSourceStack> ctx) {
        try {
            return Optional.of(ctx.getArgument("player", PlayerSelectorArgumentResolver.class));
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }

    private void reply(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        feedback.send(sender, key, placeholders);
    }
}
