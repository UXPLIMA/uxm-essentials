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
 * <p>A player has at most one custom command warmup, the same rule teleports follow. Arming a new one supersedes
 * whatever was still armed, through the handle's own once-only cancel, so the earlier command never runs or charges
 * and its cancel side runs exactly once. Leaving it running would put it out of reach of the move listener, which
 * only sees the latest entry.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code pending} is a {@link ConcurrentHashMap} keyed by player uuid,
 * mutated only through {@code put}, {@code remove} and {@code computeIfPresent}. The move listener (a region
 * thread) and the warmup completion (which calls {@link #forget(UUID, WarmupHandle)}) therefore resolve a
 * cancel-versus-fire race to one loser rather than observing a half-updated entry. A superseded entry is taken out
 * of the map atomically ({@code put} or {@code remove} hands it to exactly one caller) and stopped after, outside
 * the map's lock, so its callback can start another command without re-entering the map. Completion and cancel
 * forget only their own handle, so an earlier warmup that ends just as it is replaced cannot drop the entry of the
 * warmup that replaced it.
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
        Entry previous = pending.put(who.uuid(), new Entry(origin, handle));
        if (previous != null && previous.handle() != handle) {
            // Two warmups begun at once on different threads: whichever arms last supersedes the other here.
            previous.handle().supersede();
        }
    }

    /**
     * Stop {@code who}'s armed warmup, if any, because they are beginning another. The caller runs this before the
     * new warmup starts, so a new run that skips its warmup is not followed by the earlier one running too.
     */
    public void supersede(UUID who) {
        Entry previous = pending.remove(Objects.requireNonNull(who, "who"));
        if (previous != null) {
            previous.handle().supersede();
        }
    }

    /** Drop the player's warmup because the player logged out. */
    public void forget(UUID who) {
        pending.remove(Objects.requireNonNull(who, "who"));
    }

    /** Drop {@code handle} once it has ended; a newer warmup armed for the same player stays tracked. */
    public void forget(UUID who, WarmupHandle handle) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(handle, "handle");
        pending.computeIfPresent(who, (id, entry) -> entry.handle() == handle ? null : entry);
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
