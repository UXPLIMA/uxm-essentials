package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.MutedCommandPolicy;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Closes the mute-bypass through chat-adjacent commands: when a muted player runs a command on the
 * configured {@code muted-blocked-commands} list (e.g. {@code /me}, {@code /msg}, {@code /mail}), the
 * dispatch is cancelled before the command runs and the player is told they are muted. The messaging context
 * already blocks the use cases this plugin owns; this listener is the wider net that also catches third-party
 * and vanilla commands an operator names.
 *
 * <p>Runs at {@link EventPriority#HIGH} so cooperating plugins at NORMAL still see an uncancelled event but
 * the dispatch is stopped before the vanilla/dispatcher handler. The mute state is read live from the
 * repository against the injected {@link Clock}, so an expired timed mute no longer blocks; a player holding
 * the moderation exempt node is never blocked. When the configured list is empty the listener short-circuits
 * before any mute lookup, so an operator who leaves the list blank pays nothing.
 */
@NullMarked
public final class MutedCommandListener implements Listener {

    private final ModerationRepository repository;
    private final MutedCommandPolicy policy;
    private final ModerationGuard guard;
    private final Messages messages;
    private final MessageSink sink;
    private final Clock clock;

    public MutedCommandListener(
            ModerationRepository repository,
            MutedCommandPolicy policy,
            ModerationGuard guard,
            Messages messages,
            MessageSink sink,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        if (!repository.loadMute(who).isActiveAt(clock.instant())) {
            return;
        }
        event.setCancelled(true);
        sink.deliver(who, messages.resolve(who, ModerationMessageKey.MUTED_COMMAND_BLOCKED, Map.of()));
    }

    private static String label(String rawMessage) {
        String body = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }
}
