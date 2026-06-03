package com.uxplima.uxmessentials.vote.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import org.jspecify.annotations.NullMarked;

/**
 * Pays out the rewards a player accrued while offline the moment they join: it drains and dispatches their
 * queued reward batches through {@link ApplyQueuedRewards}, so an offline vote is never lost. The drain
 * reads the database, so it runs off the join thread via the {@link Scheduler} port's async seam; the
 * reward dispatch itself hops to the global region inside the dispatcher. A player with nothing queued is a
 * cheap no-op (the repository's existence probe short-circuits before any drain).
 */
@NullMarked
public final class VoteJoinListener implements Listener {

    private final ApplyQueuedRewards applyQueuedRewards;
    private final Scheduler scheduler;

    public VoteJoinListener(ApplyQueuedRewards applyQueuedRewards, Scheduler scheduler) {
        this.applyQueuedRewards = Objects.requireNonNull(applyQueuedRewards, "applyQueuedRewards");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);
        scheduler.async(() -> applyQueuedRewards.applyFor(who));
    }
}
