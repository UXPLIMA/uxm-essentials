package com.uxplima.uxmessentials.poses.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSeatPort;
import org.jspecify.annotations.NullMarked;

/**
 * Reaps tagged seat entities when the ground they sit on unloads. A chunk or world can unload with a seat still on
 * it (a crash mid-pose, an admin unloading a world); left alone that seat would reload as an orphan. Both handlers
 * run on the owning thread the unload event fires on, so the removal is already region-correct without a hop.
 */
@NullMarked
public final class PoseCleanupListener implements Listener {

    private final BukkitSeatPort seats;

    public PoseCleanupListener(BukkitSeatPort seats) {
        this.seats = Objects.requireNonNull(seats, "seats");
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        seats.removeSeatsIn(event.getChunk());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        seats.removeSeatsIn(event.getWorld());
    }
}
