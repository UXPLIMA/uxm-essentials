package com.uxplima.uxmessentials.security.adapter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The session-scoped state of the join-verification freeze: who is currently frozen and awaiting verification (with a
 * running count of their failed attempts), and who is serving a post-lockout cooldown. It is transient in-memory state
 * owned solely by the security adapter — the freeze listeners read {@link #isPending(UUID)} to decide whether to
 * cancel a frozen player's action, and the join flow reads {@link #isLockedOut(UUID, Instant)} to kick a returning
 * locked-out player.
 *
 * <p>Both maps are {@link ConcurrentHashMap} because the join flow's async worker, the keypad's verify worker, and the
 * event threads all touch them; every mutation is a single atomic map operation. A relog clears the pending freeze
 * (the join flow re-decides from scratch), and the lockout cooldown deliberately survives a relog until its instant
 * passes — it is what a rejoin during the cooldown is kicked against. Everything is dropped on {@link #clearAll()} at
 * module stop, so a disable leaves no residual freeze.
 */
@NullMarked
public final class VerificationSessions {

    /** UUID → failures so far this pending session; presence of the key means the player is frozen. */
    private final ConcurrentHashMap<UUID, Integer> pending = new ConcurrentHashMap<>();

    /** UUID → the instant a lockout cooldown expires; a rejoin before then is kicked. */
    private final ConcurrentHashMap<UUID, Instant> lockedUntil = new ConcurrentHashMap<>();

    /** Mark {@code playerId} frozen and awaiting verification with a fresh (zero) failure count. */
    public void begin(UUID playerId) {
        pending.put(Objects.requireNonNull(playerId, "playerId"), 0);
    }

    /** Whether {@code playerId} is currently frozen awaiting verification. */
    public boolean isPending(UUID playerId) {
        return pending.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Record a failed attempt for {@code playerId} and return the new running failure count (0 if not pending). */
    public int recordFailure(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Integer updated = pending.computeIfPresent(playerId, (id, failures) -> failures + 1);
        return updated == null ? 0 : updated;
    }

    /** Clear {@code playerId}'s pending freeze — on a successful verification or a disconnect. */
    public void clear(UUID playerId) {
        pending.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Put {@code playerId} on the lockout cooldown until {@code until} and drop their pending freeze. */
    public void lock(UUID playerId, Instant until) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(until, "until");
        pending.remove(playerId);
        lockedUntil.put(playerId, until);
    }

    /** Whether {@code playerId} is still within a lockout cooldown as of {@code now}. */
    public boolean isLockedOut(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        Instant until = lockedUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (!now.isBefore(until)) {
            // The cooldown lapsed — drop it so the map does not accumulate stale entries.
            lockedUntil.remove(playerId, until);
            return false;
        }
        return true;
    }

    /** Drop every pending freeze and lockout, called on module stop so no verification state survives a disable. */
    public void clearAll() {
        pending.clear();
        lockedUntil.clear();
    }
}
