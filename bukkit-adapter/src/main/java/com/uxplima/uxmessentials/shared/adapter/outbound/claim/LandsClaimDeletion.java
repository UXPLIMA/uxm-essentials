package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimRegion;
import me.angeschossen.lands.api.events.ChunkDeleteEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges Lands' per-chunk unclaim event onto the {@link ClaimRegion} sink, emitting one exact 16×16 region
 * per unclaimed chunk.
 *
 * <p>The bridge listens on {@link ChunkDeleteEvent}, not {@code LandDeleteEvent}, on purpose. A Lands
 * {@code Land} can span many chunks scattered across possibly several worlds, so a land's bounding box would
 * cover the gaps between its chunks and falsely match a warp standing on unclaimed land inside that hull. The
 * per-chunk event is exact: a single chunk is one contiguous 16×16 block square, converted here from chunk
 * coordinates ({@code x<<4 .. (x<<4)+15}). The trade-off is coverage — if a Lands build ever bulk-deletes a
 * land without firing a {@link ChunkDeleteEvent} per chunk, those chunks are not reported. That gap is
 * accepted: the cascade is best-effort (see {@code ClaimDeletionEvents}), so a missed chunk merely leaves a
 * warp active in now-unclaimed land, which is safe (fail-open).
 *
 * <p>The event's {@code UnclaimType} could distinguish why the chunk was released, but every unclaim removes
 * the land's cover, so a cascade treats them alike and ignores it.
 *
 * <p>Registered only when Lands is present (its wiring guards on the plugin manager), so the
 * {@code me.angeschossen.lands.*} handler parameter is always loadable by the time this listener is installed.
 * The handler body is guarded so a malformed event degrades to a logged skip rather than a throw into Lands'
 * event dispatch. {@link EventPriority#MONITOR} because this only observes an already-decided unclaim.
 */
@NullMarked
public final class LandsClaimDeletion implements Listener {

    private static final int CHUNK_BLOCKS = 16;

    private final Consumer<ClaimRegion> sink;
    private final Logger log;

    public LandsClaimDeletion(Consumer<ClaimRegion> sink, Logger log) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.log = Objects.requireNonNull(log, "log");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkDeleted(ChunkDeleteEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            World world = event.getWorld();
            if (world == null) {
                return;
            }
            WorldRef ref = new WorldRef(world.getUID(), world.getName());
            int minX = event.getX() << 4;
            int minZ = event.getZ() << 4;
            sink.accept(new ClaimRegion(ref, minX, minZ, minX + CHUNK_BLOCKS - 1, minZ + CHUNK_BLOCKS - 1));
        } catch (RuntimeException e) {
            log.warn("event=claim_deletion_bridge_failed provider=lands reason={}", e);
        }
    }
}
