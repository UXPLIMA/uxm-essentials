package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The dual activity listener feeding the AFK clock (docs/02-concurrency.md §6.9): a player who moves or chats
 * is active, so their idle clock resets and an AFK player returns on the first sign of life.
 *
 * <ul>
 *   <li><b>Move</b> — {@link PlayerMoveEvent} fires on the player's own region thread (sync). It is filtered to
 *       a real position change (a block hop, not a head turn) so the hot path skips the common look-around
 *       event, then records activity directly — the use case only touches the {@code ConcurrentHashMap} via
 *       {@code compute}, valid on this thread.
 *   <li><b>Chat</b> — {@link AsyncChatEvent} fires off the main thread, so the handler treats the Bukkit API as
 *       off-limits and bridges back to the player's region thread through the {@link Scheduler} port before
 *       recording activity (CLAUDE.md: every async listener bridges back via the scheduler).
 * </ul>
 *
 * <p>This is the §6.4 sync-listener / async-reader split done correctly: the listeners write the activity stamp
 * (the move write region-local, the chat write bridged onto the owning thread) and the async AFK sweep only
 * reads.
 */
@NullMarked
public final class PresenceActivityListener implements Listener {

    private final ClearAfkOnActivity clearAfk;
    private final Scheduler scheduler;

    public PresenceActivityListener(ClearAfkOnActivity clearAfk, Scheduler scheduler) {
        this.clearAfk = Objects.requireNonNull(clearAfk, "clearAfk");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (movedBlock(event.getFrom(), event.getTo())) {
            clearAfk.recordActivity(BukkitRefs.toRef(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        scheduler.onEntity(who, () -> clearAfk.recordActivity(who));
    }

    private static boolean movedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
