package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.port.ExemptionSightings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Records in the shared database whether a player here holds the punishment exemption, so another backend can see it
 * once they are offline: when they join, when they leave, and when their permissions change while they are here. The
 * node is read live on the player's own thread; the write runs on the async lane.
 */
@NullMarked
public final class ExemptionWatch implements Listener {

    private final ExemptionSightings sightings;
    private final String server;
    private final Scheduler scheduler;
    private final Logger log;
    private final Clock clock;

    /** What was last recorded for each player here, so a permission change that changes nothing writes nothing. */
    private final Map<UUID, Boolean> recorded = new ConcurrentHashMap<>();

    public ExemptionWatch(ExemptionSightings sightings, String server, Scheduler scheduler, Logger log, Clock clock) {
        this.sightings = Objects.requireNonNull(sightings, "sightings");
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        record(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        record(player);
        recorded.remove(player.getUniqueId());
    }

    /** {@code player}'s permissions changed: record the node again if its answer did. Call it on their thread. */
    public void changed(Player player) {
        boolean held = player.hasPermission(ModerationGuard.EXEMPT_NODE);
        Boolean before = recorded.get(player.getUniqueId());
        if (before == null || before != held) {
            write(player.getUniqueId(), held);
        }
    }

    private void record(Player player) {
        write(player.getUniqueId(), player.hasPermission(ModerationGuard.EXEMPT_NODE));
    }

    private void write(UUID player, boolean held) {
        recorded.put(player, held);
        Instant at = clock.instant();
        scheduler.async(() -> {
            try {
                sightings.record(player, server, held, at);
            } catch (RuntimeException failure) {
                log.error("event=moderation_exemption_unrecorded player=" + player, failure);
            }
        });
    }
}
