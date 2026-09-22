package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.uxplima.uxmessentials.moderation.adapter.outbound.BukkitSanctions;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Pins a frozen player in place and tells them why: a {@link PlayerMoveEvent} that changes the block position
 * of a frozen player is cancelled, so they can look around but not walk. Reads the session-scoped frozen set
 * the {@link BukkitSanctions} adapter owns through a plain predicate (a relog clears the freeze, since the set
 * is in-memory, so this listener naturally stops blocking once the set no longer holds the player).
 *
 * <p>The block-position check means head movement (yaw/pitch only) is not cancelled, cancelling every micro
 * move would be both jarring and needless event churn on the hot move path.
 *
 * <p>The notice is throttled and the interval is the operator's, from
 * {@code modules/moderation/config.conf}. A move event fires many times a second while a key is held, so an
 * unthrottled notice would be the flood rather than the answer; zero turns it off and freezes the player in
 * silence, which is what this listener did for its whole life while {@code moderation.freeze.blocked} sat
 * unsent in twelve catalogues.
 */
@NullMarked
public final class FreezeMoveListener implements Listener {

    private final Predicate<UUID> frozen;
    private final Messages messages;
    private final MessageSink sink;
    private final Clock clock;
    private final Duration noticeInterval;

    /** When each frozen player was last told, so a held key is one line rather than fifty. */
    private final ConcurrentHashMap<UUID, Instant> told = new ConcurrentHashMap<>();

    public FreezeMoveListener(
            Predicate<UUID> frozen, Messages messages, MessageSink sink, Clock clock, Duration noticeInterval) {
        this.frozen = Objects.requireNonNull(frozen, "frozen");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.noticeInterval = Objects.requireNonNull(noticeInterval, "noticeInterval");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID who = event.getPlayer().getUniqueId();
        if (!frozen.test(who)) {
            // Not frozen any more: drop whatever this listener remembered about telling them.
            told.remove(who);
            return;
        }
        if (!movedBlock(event)) {
            return;
        }
        event.setCancelled(true);
        tell(event, who);
    }

    /** Tell {@code who} why they did not move, unless they were told inside the throttle window. */
    private void tell(PlayerMoveEvent event, UUID who) {
        if (noticeInterval.isZero()) {
            return;
        }
        Instant now = clock.instant();
        Instant last = told.get(who);
        if (last != null && now.isBefore(last.plus(noticeInterval))) {
            return;
        }
        told.put(who, now);
        PlayerRef viewer = BukkitRefs.toRef(event.getPlayer());
        sink.deliver(viewer, messages.resolve(viewer, ModerationMessageKey.FREEZE_BLOCKED, Map.of()));
    }

    private static boolean movedBlock(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
