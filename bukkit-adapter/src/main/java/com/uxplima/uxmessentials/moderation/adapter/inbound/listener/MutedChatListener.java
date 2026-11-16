package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.messaging.application.port.MuteAnswer;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.domain.MessagingMessageKey;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a muted player out of public chat, and tells them why their line went nowhere.
 *
 * <p>Nothing did. The messaging commands asked the mute policy and the {@link MutedCommandListener} stopped the
 * configured commands, while the chat line itself reached everybody: a player muted for spamming on a live server
 * went on talking. The mute is read through the same policy {@code /msg} asks, and a player holding the moderation
 * exempt node is never stopped, as {@link MutedCommandListener} does.
 *
 * <p>Runs at {@link EventPriority#LOW}, before a chat formatter or a bridge at NORMAL reads the line. A line typed by
 * a player arrives on the chat thread, where the mute is read from the database so a mute set on any server counts at
 * once. A line a plugin makes a player say ({@code Player#chat}, a menu's chat action) fires the same event on the
 * server thread; that one is answered from the mute kept in memory, never the database. The mute is asked first, so
 * the exempt node is only read for a player who is muted.
 *
 * <p>A mute that could not be read or is not known yet stops the line too, and the speaker is told to try again:
 * letting a muted player's line out is the worse mistake, and the next line asks again.
 */
@NullMarked
public final class MutedChatListener implements Listener {

    private final MutePolicy fresh;
    private final MutePolicy kept;
    private final Predicate<PlayerRef> exempt;
    private final Messages messages;
    private final MessageSink sink;

    /**
     * @param fresh the policy that reads the database, asked only on the chat thread
     * @param kept the policy that answers from memory, asked on the server thread
     */
    public MutedChatListener(
            MutePolicy fresh, MutePolicy kept, Predicate<PlayerRef> exempt, Messages messages, MessageSink sink) {
        this.fresh = Objects.requireNonNull(fresh, "fresh");
        this.kept = Objects.requireNonNull(kept, "kept");
        this.exempt = Objects.requireNonNull(exempt, "exempt");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PlayerRef speaker = BukkitRefs.toRef(event.getPlayer());
        MuteAnswer mute = (event.isAsynchronous() ? fresh : kept).check(speaker);
        if (mute == MuteAnswer.NOT_MUTED || exempt.test(speaker)) {
            return;
        }
        event.setCancelled(true);
        MessageKey why = mute == MuteAnswer.MUTED
                ? ModerationMessageKey.MUTED_CHAT_BLOCKED
                : MessagingMessageKey.MSG_MUTE_UNCHECKED;
        sink.deliver(speaker, messages.resolve(speaker, why, Map.of()));
    }
}
