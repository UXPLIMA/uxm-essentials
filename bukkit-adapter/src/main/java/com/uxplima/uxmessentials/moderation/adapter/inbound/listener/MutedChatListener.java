package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a muted player out of public chat, and tells them why their line went nowhere.
 *
 * <p>Nothing did. The messaging commands asked the mute policy and the {@link MutedCommandListener} stopped the
 * configured commands, while the chat line itself reached everybody: a player muted for spamming on a live server
 * went on talking. The mute is read through the same policy {@code /msg} asks, and a player holding the moderation
 * exempt node is never stopped, as {@link MutedCommandListener} does.
 *
 * <p>Runs at {@link EventPriority#LOW}, before a chat formatter or a bridge at NORMAL reads the line. The event is
 * asynchronous, so the policy's database read stays off the tick.
 */
@NullMarked
public final class MutedChatListener implements Listener {

    private final MutePolicy mutes;
    private final Predicate<PlayerRef> exempt;
    private final Messages messages;
    private final MessageSink sink;

    public MutedChatListener(MutePolicy mutes, Predicate<PlayerRef> exempt, Messages messages, MessageSink sink) {
        this.mutes = Objects.requireNonNull(mutes, "mutes");
        this.exempt = Objects.requireNonNull(exempt, "exempt");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PlayerRef speaker = BukkitRefs.toRef(event.getPlayer());
        if (exempt.test(speaker) || !mutes.isMuted(speaker)) {
            return;
        }
        event.setCancelled(true);
        sink.deliver(speaker, messages.resolve(speaker, ModerationMessageKey.MUTED_CHAT_BLOCKED, Map.of()));
    }
}
