package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every moderation Brigadier command holds: the constructed {@link ModerationServices},
 * the {@link Messages} catalog and the {@link MessageSink} for the resolution rejections a command renders
 * directly. Concrete command classes extend this so each stays focused on building its node and mapping its
 * arguments to one use-case call.
 *
 * <p>{@link #actor} maps the sender to a {@link PlayerRef} — a console actor gets a stable nil-UUID ref so an
 * offline {@code /jail}/{@code /banip} from console still attributes an issuer. {@link #targetByName} resolves
 * a target online-first, then from the profile cache so an offline target can still be muted/jailed/banned
 * (the offline-jail/offline-banip paths). The optional reason is the greedy trailing argument.
 */
@NullMarked
abstract class ModerationCommandSupport {

    static final MessageKey UNKNOWN_PLAYER = ModerationMessageKey.UNKNOWN_TARGET;

    final ModerationServices services;
    final Messages messages;
    final MessageSink sink;

    ModerationCommandSupport(ModerationServices services, Messages messages, MessageSink sink) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** The actor: the live player, or a stable system ref for a console/command-block sender. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        return sender instanceof Player player
                ? new PlayerRef(player.getUniqueId(), player.getName())
                : new PlayerRef(new UUID(0L, 0L), sender.getName());
    }

    /** The target by name, online or known offline, or empty (after a feedback line) when never seen. */
    final Optional<PlayerRef> targetByName(CommandContext<CommandSourceStack> ctx, String name) {
        Optional<PlayerRef> target = services.targets().resolve(name);
        if (target.isEmpty()) {
            notify(ctx, UNKNOWN_PLAYER, Map.of("player", name));
        }
        return target;
    }

    /** The greedy trailing reason argument, or empty when the command was invoked without one. */
    static Optional<String> optionalReason(CommandContext<CommandSourceStack> ctx) {
        try {
            String raw = ctx.getArgument("reason", String.class);
            return raw.isBlank() ? Optional.empty() : Optional.of(raw.trim());
        } catch (IllegalArgumentException noReason) {
            return Optional.empty();
        }
    }

    /** Send {@code key} to the command sender, rendered in their locale. */
    final void notify(CommandContext<CommandSourceStack> ctx, MessageKey key, Map<String, String> placeholders) {
        sink.deliver(actor(ctx), messages.resolve(actor(ctx), key, placeholders));
    }

    /** The live player behind {@code target}, if connected — for a command that must act on a session. */
    static @Nullable Player onlinePlayer(CommandContext<CommandSourceStack> ctx, PlayerRef target) {
        return ctx.getSource().getSender().getServer().getPlayer(target.uuid());
    }
}
