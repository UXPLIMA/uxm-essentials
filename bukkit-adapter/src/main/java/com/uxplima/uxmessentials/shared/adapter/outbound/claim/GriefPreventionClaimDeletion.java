package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimRegion;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimExpirationEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges GriefPrevention's claim-removal events onto the {@link ClaimRegion} sink. Both an operator/owner
 * deletion ({@link ClaimDeletedEvent}) and an inactivity expiry ({@link ClaimExpirationEvent}) unclaim the
 * land, so both are forwarded; each carries the removed {@link Claim} through its shared {@code ClaimEvent}
 * base. A GriefPrevention claim is a single axis-aligned rectangle, so its two boundary corners give the exact
 * footprint with no over-approximation.
 *
 * <p>Registered only when GriefPrevention is present (its wiring guards on the plugin manager), so the
 * {@code me.ryanhamshire.GriefPrevention.*} handler parameters are always loadable by the time this listener
 * is installed. The handler body is nonetheless guarded: a listener that threw would break the plugin's event
 * dispatch, so a malformed event degrades to a logged skip. {@link EventPriority#MONITOR} because this only
 * observes an already-decided removal and never vetoes it.
 */
@NullMarked
public final class GriefPreventionClaimDeletion implements Listener {

    private final Consumer<ClaimRegion> sink;
    private final Logger log;

    public GriefPreventionClaimDeletion(Consumer<ClaimRegion> sink, Logger log) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.log = Objects.requireNonNull(log, "log");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimDeleted(ClaimDeletedEvent event) {
        Objects.requireNonNull(event, "event");
        emit(event.getClaim());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimExpired(ClaimExpirationEvent event) {
        Objects.requireNonNull(event, "event");
        emit(event.getClaim());
    }

    private void emit(Claim claim) {
        try {
            Location lesser = claim.getLesserBoundaryCorner();
            Location greater = claim.getGreaterBoundaryCorner();
            World world = lesser.getWorld();
            if (world == null) {
                // A claim whose world has been unloaded can no longer be located; skip rather than guess.
                return;
            }
            WorldRef ref = new WorldRef(world.getUID(), world.getName());
            int minX = Math.min(lesser.getBlockX(), greater.getBlockX());
            int minZ = Math.min(lesser.getBlockZ(), greater.getBlockZ());
            int maxX = Math.max(lesser.getBlockX(), greater.getBlockX());
            int maxZ = Math.max(lesser.getBlockZ(), greater.getBlockZ());
            sink.accept(new ClaimRegion(ref, minX, minZ, maxX, maxZ));
        } catch (RuntimeException e) {
            log.warn("event=claim_deletion_bridge_failed provider=griefprevention reason={}", e);
        }
    }
}
