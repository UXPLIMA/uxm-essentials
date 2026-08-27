package com.uxplima.uxmessentials.customcommands.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Owns the move-cancels-warmup invariant for custom commands. A definition that declares a warmup arms an entry
 * here when the countdown starts; leaving the origin block cancels it, and completing or quitting drops it.
 *
 * <p>The teleport context owns the same invariant for teleports through its own tracker. This one is deliberately
 * separate and much smaller: it has no cancel toggles to consult and no damage axis, because a custom command's
 * warmup has exactly one rule, and a dependency edge from this context to teleport would buy nothing.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code pending} is a {@link ConcurrentHashMap} keyed by player uuid,
 * mutated only through {@code put}, {@code remove} and {@code computeIfPresent}. The move listener (a region
 * thread) and the warmup completion (which calls {@link #forget}) therefore resolve a cancel-versus-fire race to
 * one loser rather than observing a half-updated entry.
 */
@NullMarked
public final class CommandWarmupTracker implements Listener {

    private final ConcurrentHashMap<UUID, Entry> pending = new ConcurrentHashMap<>();

    /**
     * Arm {@code handle} for {@code who}, anchored at the block they stood on. A handle that has already completed
     * is not tracked at all, so a zero-second or bypassed warmup is immune to move-cancel by construction.
     */
    public void arm(PlayerRef who, Position origin, WarmupHandle handle) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(handle, "handle");
        if (handle.isComplete()) {
            return;
        }
        pending.put(who.uuid(), new Entry(origin, handle));
    }

    /** Drop the player's warmup once it has completed, been cancelled, or the player logged out. */
    public void forget(UUID who) {
        pending.remove(Objects.requireNonNull(who, "who"));
    }

    /** Drop every tracked warmup on module stop, so a reload never leaves a countdown watching for a move. */
    public void clear() {
        pending.clear();
    }

    /** How many warmups are armed right now; the seam the tests read rather than reaching into the map. */
    public int tracked() {
        return pending.size();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (pending.isEmpty()) {
            return;
        }
        onMove(event.getPlayer().getUniqueId(), BukkitRefs.toPosition(event.getTo()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (pending.isEmpty()) {
            return;
        }
        forget(event.getPlayer().getUniqueId());
    }

    /** Reconcile one movement: a player who left the origin block loses the warmup, a head turn does not. */
    public void onMove(UUID who, Position to) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(to, "to");
        pending.computeIfPresent(who, (id, entry) -> {
            if (sameBlock(entry.origin(), to)) {
                return entry;
            }
            entry.handle().cancel();
            return null;
        });
    }

    /** Two positions are the same block when their world and their floored coordinates all match. */
    private static boolean sameBlock(Position origin, Position to) {
        return origin.world().equals(to.world())
                && Math.floor(origin.x()) == Math.floor(to.x())
                && Math.floor(origin.y()) == Math.floor(to.y())
                && Math.floor(origin.z()) == Math.floor(to.z());
    }

    private record Entry(Position origin, WarmupHandle handle) {}
}
