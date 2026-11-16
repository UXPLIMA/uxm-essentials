package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.port.Warmups.CancelCause;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.PendingTeleport;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
 * and the handle's own state machine resolves the cancel-vs-fire race to a single loser. Every stopped entry
 * (moved, superseded, quit) is taken out of the map atomically ({@code put}, {@code remove} or the mapping that
 * returns null hands it to exactly one caller) and cancelled after, outside the map's lock, so its callback can
 * start another teleport, which re-enters the map for the same player, without corrupting it.
 *
 * <p>A warmup belongs to the session that began it, which a ticket tracks: {@link #beginning} hands one out before
 * the setup, {@link #quit} voids every ticket of the player before it drops their entry, and {@link #stillHere}
 * spends the ticket after the warmup is armed. Whichever order the two threads take, either the quit finds the
 * armed entry or the setup finds its ticket voided. A quit also leaves a tombstone that only the player's next
 * {@link #joined} clears, so a setup that starts while the quit is still being handled (later quit listeners run with
 * the player still listed online) is handed a ticket that is void from the start. A setup holding a voided ticket
 * neither supersedes nor arms anything, so one that outlives a fast relog cannot stop the new session's warmup.
 * Completion forgets only its own handle, so an earlier warmup that fires just as it is replaced cannot drop
 * the entry of the warmup that replaced it.
 */
@NullMarked
public final class WarmupTracker {

    private final ConcurrentHashMap<UUID, Entry> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<Object>> beginning = new ConcurrentHashMap<>();
    private final Set<UUID> gone = ConcurrentHashMap.newKeySet();

    /**
     * Arm a warmup for {@code who} anchored at {@code origin}, holding {@code handle} for cancellation and
     * {@code completesAt} for the {@code teleport_warmup_remaining} placeholder read. Only while {@code ticket} still
     * holds: a warmup whose player quit since its setup began is not armed, so it cannot replace a warmup the player
     * started after a relog. The check and the arm are one step against {@link #quit}, which voids the ticket under the
     * same key. Answers whether the warmup was armed.
     */
    public boolean arm(PlayerRef who, Object ticket, PendingTeleport warmup, WarmupHandle handle, Instant completesAt) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(warmup, "warmup");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(completesAt, "completesAt");
        if (handle.isComplete()) {
            return false; // a zero-duration warmup already fired; nothing to track or cancel
        }
        AtomicBoolean armed = new AtomicBoolean();
        AtomicReference<@Nullable Entry> previous = new AtomicReference<>();
        beginning.computeIfPresent(who.uuid(), (id, tickets) -> {
            if (tickets.contains(ticket)) {
                armed.set(true);
                previous.set(pending.put(id, new Entry(warmup, handle, completesAt)));
            }
            return tickets;
        });
        replace(previous.get(), handle);
        return armed.get();
    }

    private static void replace(@Nullable Entry previous, WarmupHandle handle) {
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
     * {@link #supersede}, only while {@code ticket} still holds, so a setup from a session that has ended leaves the
     * warmup of the session after it alone.
     */
    public void supersede(UUID who, Object ticket) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ticket, "ticket");
        AtomicReference<@Nullable Entry> previous = new AtomicReference<>();
        beginning.computeIfPresent(who, (id, tickets) -> {
            if (tickets.contains(ticket)) {
                previous.set(pending.remove(id));
            }
            return tickets;
        });
        Entry stopped = previous.get();
        if (stopped != null) {
            stopped.handle().supersede();
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

    /**
     * Cancel {@code who}'s armed warmup because they left the server. Dropping the entry alone is not enough: the
     * countdown reaches the player by uuid, so after a fast relog it would still land them, a charge taken for it kept.
     * As with a supersede, the entry leaves the map first and is cancelled outside its lock.
     */
    public void quit(UUID who) {
        Objects.requireNonNull(who, "who");
        // The tombstone and the voided tickets come first, so a setup that arms after the entry is dropped below, or
        // starts only now, still sees that its session ended.
        gone.add(who);
        beginning.remove(who);
        Entry previous = pending.remove(who);
        if (previous != null) {
            previous.handle().cancel(CancelCause.QUIT);
        }
    }

    /**
     * Note that a warmup for {@code who} is being set up, before anything is read about them. The returned ticket
     * is spent with {@link #stillHere} once the warmup is armed, or once the setup gave up.
     */
    public Object beginning(UUID who) {
        Objects.requireNonNull(who, "who");
        Object ticket = new Object();
        beginning.compute(who, (id, tickets) -> {
            if (gone.contains(id)) {
                // The player quit and has not joined again: the ticket is never held, so it is void from the start.
                return tickets;
            }
            Set<Object> held = tickets != null ? tickets : new HashSet<>();
            held.add(ticket);
            return held;
        });
        return ticket;
    }

    /** Clear the tombstone {@link #quit} left, as {@code who} joins: warmups they begin from now on are theirs. */
    public void joined(UUID who) {
        gone.remove(Objects.requireNonNull(who, "who"));
    }

    /**
     * Spend {@code ticket}: true when {@code who} has not quit since {@link #beginning} handed it out. On false the
     * caller cancels the warmup it set up, which the quit could not reach.
     */
    public boolean stillHere(UUID who, Object ticket) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ticket, "ticket");
        AtomicBoolean held = new AtomicBoolean();
        beginning.computeIfPresent(who, (id, tickets) -> {
            held.set(tickets.remove(ticket));
            return tickets.isEmpty() ? null : tickets;
        });
        return held.get();
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
        beginning.clear();
        gone.clear();
    }

    private void reconcile(UUID who, Function<Entry, PendingTeleport.Outcome> step) {
        // The decision is made atomically inside the mapping, which removes a cancelled warmup (returns null) and
        // so hands it to this caller alone. The handle is stopped after, outside the lock: its callback may start
        // another teleport for the same player, and re-entering the map from inside its own mapping corrupts it.
        AtomicReference<@Nullable Stopped> stopped = new AtomicReference<>();
        pending.computeIfPresent(who, (id, entry) -> {
            PendingTeleport.Outcome outcome = step.apply(entry);
            WarmupCancelReason reason = outcome.cancelReason();
            if (reason != null) {
                stopped.set(new Stopped(entry.handle(), reason));
                return null;
            }
            return new Entry(outcome.state(), entry.handle(), entry.completesAt());
        });
        Stopped due = stopped.get();
        if (due != null) {
            stop(due.handle(), due.reason());
        }
    }

    /** Stop {@code handle} for {@code reason}, so the player is told what they did rather than that they moved. */
    private static void stop(WarmupHandle handle, WarmupCancelReason reason) {
        switch (reason) {
            case MOVED -> handle.cancel(CancelCause.MOVED);
            case ROTATED -> handle.cancel(CancelCause.ROTATED);
            case DAMAGED -> handle.cancel(CancelCause.DAMAGED);
            case INTERACTED -> handle.cancel(CancelCause.INTERACTED);
            case QUIT -> handle.cancel(CancelCause.QUIT);
            case ABORTED -> handle.cancel(CancelCause.ABORTED);
            case SUPERSEDED -> handle.supersede();
        }
    }

    private record Entry(PendingTeleport warmup, WarmupHandle handle, Instant completesAt) {}

    private record Stopped(WarmupHandle handle, WarmupCancelReason reason) {}
}
