package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.economy.application.DeliverPendingCredits;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Pays a joining player the credits queued while they were offline. The queue is a database table, so the delivery
 * runs on the async lane; a physical credit is moved onto the player's own thread by the provider.
 */
@NullMarked
public final class PendingTransactionListener implements Listener {

    // Long enough for the joining player to be fully set up before a physical credit lands in their inventory.
    private static final Duration SETTLE_DELAY = Duration.ofMillis(500);

    private final DeliverPendingCredits credits;
    private final Scheduler scheduler;

    public PendingTransactionListener(DeliverPendingCredits credits, Scheduler scheduler) {
        this.credits = Objects.requireNonNull(credits, "credits");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerRef ref = BukkitRefs.toRef(event.getPlayer());
        scheduler.asyncAfter(SETTLE_DELAY, () -> credits.deliver(ref));
    }
}
