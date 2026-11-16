package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.PendingTeleport;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import org.jspecify.annotations.NullMarked;

/**
 * Holds the in-flight warmups the move/damage listeners cancel, owning the <b>move-cancels-warmup</b>
 * invariant for the teleport context. When a command begins a warmup it registers the player's origin
 * block, the configured cancel toggles, and the live {@link WarmupHandle} here; the
 * {@code PlayerMoveEvent} / damage listeners reconcile each event against the stored
 * {@link PendingTeleport} (a pure decision) and flip the handle when a cancel fires.
 *
 * <p>A player has at most one warmup. Arming a new one supersedes whatever was still armed, through the handle's
 * own once-only cancel, so the earlier teleport never lands and its cancel callback runs exactly once. Leaving
 * the earlier one running would put it out of reach of the move listener, which only sees the latest entry.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code pending} is a {@link ConcurrentHashMap} keyed by player
 * uuid mutated via {@code computeIfPresent}; the move listener (sync, region thread) and the warmup
 * completion (which {@link #forget(UUID, WarmupHandle)} drops the entry) never observe a half-updated snapshot,
 * and the handle's own state machine resolves the cancel-vs-fire race to a single loser. A superseded entry is
 * taken out of the map atomically ({@code put} or {@code remove} hands it to exactly one caller) and cancelled
 * after, outside the map's lock, so its callback can start another teleport without re-entering the map.
 * Completion forgets only its own handle, so an earlier warmup that fires just as it is replaced cannot drop
 * the entry of the warmup that replaced it.
 */
@NullMarked
public final class WarmupTracker {

    private final ConcurrentHashMap<UUID, Entry> pending = new ConcurrentHashMap<>();

    /**
     * Arm a warmup for {@code who} anchored at {@code origin}, holding {@code handle} for cancellation and
     * {@code completesAt} for the {@code teleport_warmup_remaining} placeholder read.
     */
    public void arm(PlayerRef who, PendingTeleport warmup, WarmupHandle handle, Instant completesAt) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warmup, "warmup");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(completesAt, "completesAt");
        if (handle.isComplete()) {
            return; // a zero-duration warmup already fired; nothing to track or cancel
        }
        Entry previous = pending.put(who.uuid(), new Entry(warmup, handle, completesAt));
        if (previous != null && previous.handle() != handle) {
            // Two warmups begun at once on different threads: whichever arms last supersedes the other here.
            previous.handle().supersede();
        }
    }

    /**
     * Stop {@code who}'s armed warmup, if any, because they are beginning another. The caller runs this before the
     * new warmup starts, so a new teleport that completes at once is not followed by the earlier one landing.
     */
    public void supersede(UUID who) {
        Entry previous = pending.remove(Objects.requireNonNull(who, "who"));
        if (previous != null) {
            previous.handle().supersede();
        }
    }

    /**
     * When {@code who}'s in-flight warmup completes, or empty when no warmup is armed. The placeholder seam
     * subtracts the clock to render the remaining wait; a completed or cancelled warmup is already forgotten.
     */
    public Optional<Instant> completesAt(UUID who) {
        Objects.requireNonNull(who, "who");
        Entry entry = pending.get(who);
        return entry == null ? Optional.empty() : Optional.of(entry.completesAt());
    }

    /** Whether {@code who} has a warmup armed, so a listener can skip everyone else before building anything. */
    public boolean isTracking(UUID who) {
        return pending.containsKey(Objects.requireNonNull(who, "who"));
    }

    /** Reconcile a movement; cancels and clears the warmup when the player left the origin block. */
    public void onMove(UUID who, Position to) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(to, "to");
        reconcile(who, entry -> entry.warmup().onMovement(to));
    }

    /** Reconcile a non-move cancel axis (damage); cancels when the toggles arm that reason. */
    public void onAction(UUID who, WarmupCancelReason reason) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(reason, "reason");
        reconcile(who, entry -> entry.warmup().onAction(reason));
    }

    /** Drop the player's warmup because the player logged out. */
    public void forget(UUID who) {
        pending.remove(Objects.requireNonNull(who, "who"));
    }

    /** Drop {@code handle} once it has completed; a newer warmup armed for the same player stays tracked. */
    public void forget(UUID who, WarmupHandle handle) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(handle, "handle");
        pending.computeIfPresent(who, (id, entry) -> entry.handle() == handle ? null : entry);
    }

    /** Drop every tracked warmup on module stop. */
    public void clear() {
        pending.clear();
    }

    private void reconcile(UUID who, java.util.function.Function<Entry, PendingTeleport.Outcome> step) {
        // computeIfPresent runs the decision atomically; a cancelled warmup is removed (returns null)
        // and its handle is flipped inside the mapping so move-vs-fire resolves to one loser.
        pending.computeIfPresent(who, (id, entry) -> {
            PendingTeleport.Outcome outcome = step.apply(entry);
            if (outcome.didCancel()) {
                entry.handle().cancel();
                return null;
            }
            return new Entry(outcome.state(), entry.handle(), entry.completesAt());
        });
    }

    private record Entry(PendingTeleport warmup, WarmupHandle handle, Instant completesAt) {}
}
