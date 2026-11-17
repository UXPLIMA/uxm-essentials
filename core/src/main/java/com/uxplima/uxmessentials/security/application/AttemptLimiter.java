package com.uxplima.uxmessentials.security.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.security.application.port.AttemptStore;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;

/**
 * The account-scoped brute-force limiter shared by every verification surface, the join freeze, op-command
 * re-authentication, {@code /2fa disable}, {@code /pin change} and {@code /pin remove}. It counts consecutive failed
 * proofs per account (keyed by UUID, never per session), so the running budget survives a disconnect, a rejoin and a
 * move to another backend: an attacker cannot reset their attempts by relogging or by hopping servers before the
 * lockout-triggering guess. When the count reaches the configured limit the account is put on a timed lockout during
 * which no surface on any backend accepts an attempt, and a successful proof (or an expired lockout) starts a fresh
 * budget.
 *
 * <p>The count and the lockout live in the {@link AttemptStore}, which every backend on the database shares, so every
 * method but {@link #knownLockedOut} does I/O and runs off the tick. The pure retry/lock decision is delegated to
 * {@link LockoutPolicy}.
 *
 * <p>The lockouts this server set or read are also kept in memory, for {@link #knownLockedOut}: a caller that must
 * answer on its own thread reads that, and the store stays the record.
 */
public final class AttemptLimiter {

    private final AttemptStore store;
    private final LockoutPolicy policy;
    private final Duration lockout;

    /** UUID to the end of a lockout this server set or read, for the answers that may not wait on the store. */
    private final ConcurrentHashMap<UUID, Instant> known = new ConcurrentHashMap<>();

    public AttemptLimiter(AttemptStore store, LockoutPolicy policy, Duration lockout) {
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.lockout = Objects.requireNonNull(lockout, "lockout");
        if (lockout.isNegative()) {
            throw new IllegalArgumentException("lockout must not be negative: " + lockout);
        }
    }

    /** Whether {@code playerId} is locked out as of {@code now}; an expired lockout is cleared. Runs off the tick. */
    public boolean isLockedOut(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        Optional<Instant> until = store.lockedUntil(playerId);
        if (until.isEmpty()) {
            known.remove(playerId);
            return false;
        }
        if (!now.isBefore(until.get())) {
            // The cooldown lapsed: drop it so the next attempt starts a fresh budget.
            store.clear(playerId, now);
            known.remove(playerId);
            return false;
        }
        known.put(playerId, until.get());
        return true;
    }

    /**
     * Whether {@code playerId} is locked out as far as this server knows, read from memory only: the lockouts it set
     * or read from the store. Safe on any thread; the shared answer is {@link #isLockedOut}.
     */
    public boolean knownLockedOut(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        Instant until = known.get(playerId);
        return until != null && now.isBefore(until);
    }

    /** Count a failed proof against {@code playerId} and report whether it tipped the account into a lockout. */
    public Outcome recordFailure(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        int count = store.addFailure(playerId);
        if (policy.evaluate(count) == LockoutPolicy.AttemptOutcome.LOCKED_OUT) {
            // The budget rolls into the timed lockout; the store starts the count again so the window (not the
            // count) governs re-entry.
            Instant until = now.plus(lockout);
            store.lock(playerId, until);
            known.put(playerId, until);
            return Outcome.locked();
        }
        return Outcome.retry(policy.remaining(count));
    }

    /** Forget {@code playerId}'s accumulated failures after a successful proof, so the next lapse starts from zero. */
    public void recordSuccess(UUID playerId) {
        store.clearFailures(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Lift an active lockout and the failure budget behind it for one account, on every backend.
     *
     * <p>What staff do for a player who locked themselves out. A lockout the operator chose to write to the ban
     * list is a ban as well, and lifting the window here does not lift that.
     *
     * @return whether a lockout was actually lifted, so a caller can tell that from a no-op
     */
    public boolean clearLockout(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        known.remove(playerId);
        return store.clear(playerId, now);
    }

    /** Forget what this server remembers, on module stop. The shared record stays: it belongs to every backend. */
    public void forgetKnown() {
        known.clear();
    }

    /** The result of {@link #recordFailure}: either locked out now, or still allowed with the attempts remaining. */
    public record Outcome(boolean lockedOut, int remaining) {

        static Outcome locked() {
            return new Outcome(true, 0);
        }

        static Outcome retry(int remaining) {
            return new Outcome(false, remaining);
        }
    }
}
