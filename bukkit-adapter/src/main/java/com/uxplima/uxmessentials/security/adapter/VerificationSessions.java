package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The session-scoped freeze flag of the join-verification flow: who is currently frozen and awaiting verification. It
 * is transient in-memory state owned solely by the security adapter — the freeze listeners read {@link #isPending(UUID)}
 * to decide whether to cancel a frozen player's action. The durable, account-scoped failure counter and lockout window
 * live separately in the shared {@link com.uxplima.uxmessentials.security.application.AttemptLimiter}, so that a relog
 * clears the freeze but never the accumulated attempt budget.
 *
 * <p>The set is a {@link ConcurrentHashMap}-backed key set because the join flow's async worker, the keypad's verify
 * worker, and the event threads all touch it; every mutation is a single atomic operation. On join the player is frozen
 * optimistically before the async enrolment lookup runs, and cleared again once that lookup proves them not-enrolled or
 * on a trusted device — so the default is "frozen until proven safe". Everything is dropped on {@link #clearAll()} at
 * module stop, so a disable leaves no residual freeze.
 */
@NullMarked
public final class VerificationSessions {

    /** Presence of a UUID means the player is frozen and awaiting verification. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    /** Mark {@code playerId} frozen and awaiting verification; idempotent so an optimistic freeze can be re-affirmed. */
    public void begin(UUID playerId) {
        pending.add(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Whether {@code playerId} is currently frozen awaiting verification. */
    public boolean isPending(UUID playerId) {
        return pending.contains(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Clear {@code playerId}'s pending freeze — on a successful verification, a cleared optimistic freeze, or a quit. */
    public void clear(UUID playerId) {
        pending.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Drop every pending freeze, called on module stop so no verification freeze survives a disable. */
    public void clearAll() {
        pending.clear();
    }
}
