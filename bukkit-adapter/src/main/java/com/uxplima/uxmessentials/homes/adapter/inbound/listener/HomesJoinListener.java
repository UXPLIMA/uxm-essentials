package com.uxplima.uxmessentials.homes.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Warms the joining player's slot {@code HomeSet} into the repository cache so the first {@code /home} opens
 * without a cold database read. Opening the grid loads the owner's homes off the tick thread; this listener
 * does that one load up front on join, off the join thread via the {@link Scheduler} port's async seam (the
 * read hits SQLite), so a warm cache makes the later open instant. A player who never opens {@code /home}
 * still pays only one cheap cached read whose entry expires on its own TTL.
 */
@NullMarked
public final class HomesJoinListener implements Listener {

    private final HomeRepository repository;
    private final Scheduler scheduler;

    public HomesJoinListener(HomeRepository repository, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        scheduler.async(() -> repository.load(who));
    }
}
