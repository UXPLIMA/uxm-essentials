package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every messaging Brigadier command holds: the constructed {@link MessagingServices},
 * the {@link Messages} catalog, and the {@link MessageSink} for the console-side and target-resolution
 * rejections a command renders directly (every in-conversation line flows through the use cases'
 * {@code MessageDelivery}). Concrete command classes extend this so each stays focused on building its node
 * and mapping its arguments to one use-case call.
 *
 * <p>{@link #onlineVisibleTarget} is the vanish-aware target resolution {@code /msg} uses: a name that does
 * not resolve to an online player the sender can see is reported as offline, so a vanished target's presence
 * is never leaked — the same {@code canSee} seam the teleport context applies to {@code /tpa}.
 */
@NullMarked
abstract class MessagingCommandSupport {

    /** The catalog key for a console invoking a player-only command. */
    static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    /** The shared catalog key for a name that resolves to no player. */
    static final MessageKey UNKNOWN_PLAYER = SharedMessageKey.COMMAND_UNKNOWN_PLAYER;

    final MessagingServices services;
    final Messages messages;
    final MessageSink sink;
    final CommandFeedback feedback;

    MessagingCommandSupport(MessagingServices services, Messages messages, MessageSink sink) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.feedback = new CommandFeedback(messages);
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, PLAYERS_ONLY, Map.of());
        return null;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** Build a {@link MessageBody} from raw text, or {@code null} (after no feedback) when it is blank/overlong. */
    static @Nullable MessageBody body(String raw) {
        try {
            return MessageBody.of(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /**
     * Resolve {@code name} to an online player {@code sender} can see, notifying the sender (target-offline)
     * on failure. A vanished target the sender cannot see is reported as offline, never as present.
     */
    final Optional<PlayerRef> onlineVisibleTarget(PlayerRef sender, String name) {
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty() || services.vanish().isHiddenFrom(sender, target.get())) {
            notify(sender, MessagingMessageKey.MSG_TARGET_OFFLINE, Map.of("player", name));
            return Optional.empty();
        }
        return target;
    }

    /** Render {@code key} for {@code viewer} with {@code placeholders} and deliver it through the sink. */
    final void notify(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        sink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }
}
