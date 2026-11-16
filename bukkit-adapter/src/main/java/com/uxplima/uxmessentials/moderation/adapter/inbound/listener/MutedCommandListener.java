package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.messaging.application.port.MuteAnswer;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.domain.MessagingMessageKey;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.MutedCommandPolicy;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Closes the mute-bypass through chat-adjacent commands: when a muted player runs a command on the
 * configured {@code muted-blocked-commands} list (e.g. {@code /me}, {@code /msg}, {@code /mail}), the
 * dispatch is cancelled before the command runs and the player is told they are muted. A namespaced form
 * ({@code /minecraft:msg}) or another name of a listed command is caught too; {@link MutedCommandPolicy} says how.
 * The messaging context already blocks the use cases this plugin owns; this listener is the wider net that also
 * catches third-party and vanilla commands an operator names.
 *
 * <p>Runs at {@link EventPriority#HIGH} so cooperating plugins at NORMAL still see an uncancelled event but
 * the dispatch is stopped before the vanilla/dispatcher handler. The mute is the one kept in memory for every
 * connected player, so the server thread never waits on the database here, and an expired timed mute no longer
 * blocks. A player holding the moderation exempt node is never blocked. When the configured list is empty the
 * listener short-circuits before any mute lookup, so an operator who leaves the list blank pays nothing. A mute that
 * is not known yet (or could not be read) blocks the listed command too, and the player is told to try again.
 */
@NullMarked
public final class MutedCommandListener implements Listener {

    private final MutePolicy mutes;
    private final MutedCommandPolicy policy;
    private final ModerationGuard guard;
    private final Messages messages;
    private final MessageSink sink;

    public MutedCommandListener(
            MutePolicy mutes, MutedCommandPolicy policy, ModerationGuard guard, Messages messages, MessageSink sink) {
        this.mutes = Objects.requireNonNull(mutes, "mutes");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (policy.isEmpty() || !policy.isBlocked(label(event.getMessage()))) {
            return;
        }
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);
        if (guard.isExempt(who)) {
            return;
        }
        MuteAnswer mute = mutes.check(who);
        if (mute == MuteAnswer.NOT_MUTED) {
            return;
        }
        event.setCancelled(true);
        MessageKey why = mute == MuteAnswer.MUTED
                ? ModerationMessageKey.MUTED_COMMAND_BLOCKED
                : MessagingMessageKey.MSG_MUTE_UNCHECKED;
        sink.deliver(who, messages.resolve(who, why, Map.of()));
    }

    private static String label(String rawMessage) {
        String body = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }
}
