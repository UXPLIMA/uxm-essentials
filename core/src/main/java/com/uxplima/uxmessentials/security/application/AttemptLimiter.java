package com.uxplima.uxmessentials.security.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
 * <p>An attempt is reserved before the proof is checked: {@link #begin} counts it as a failure first and refuses to
 * let it be checked once the budget is spent, so attempts made in parallel (several keypad submissions, two backends at
 * once) can never check more proofs than the budget allows. {@link #succeeded} forgets the count again, and
 * {@link #failed} turns the reserved failure into a lockout when it was the last one allowed. A lockout also drops
 * any proof the player could carry to another backend. {@link #check} does all of that for one proof, with one proof
 * in flight per player on this server, and gives the reserved attempt back when the proof could not be checked at all.
 *
 * <p>The count and the lockout live in the {@link AttemptStore}, which every backend on the database shares, so every
 * method but {@link #knownLockedOut} does I/O and runs off the tick. The pure retry/lock decision is delegated to
 * {@link LockoutPolicy}. The lockouts this server set or read are also kept in memory, for {@link #knownLockedOut}: a
 * caller that must answer on its own thread reads that, and the store stays the record.
 */
public final class AttemptLimiter {

    private final AttemptStore store;
    private final Consumer<UUID> revokeSession;
    private final LockoutPolicy policy;
    private final Duration lockout;

    /** UUID to the end of a lockout this server set or read, for the answers that may not wait on the store. */
    private final ConcurrentHashMap<UUID, Instant> known = new ConcurrentHashMap<>();

    /** The players with a proof being checked on this server right now. */
    private final Set<UUID> checking = ConcurrentHashMap.newKeySet();

    /**
     * A limiter over {@code store}. {@code revokeSession} drops the proof a player could carry to another backend; it is
     * called on every lockout, and may queue the drop behind that player's other session writes.
     */
    public AttemptLimiter(AttemptStore store, Consumer<UUID> revokeSession, LockoutPolicy policy, Duration lockout) {
        this.store = Objects.requireNonNull(store, "store");
        this.revokeSession = Objects.requireNonNull(revokeSession, "revokeSession");
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
            // The cooldown lapsed: drop it so the next attempt starts a fresh budget. Only a lockout that has ended is
            // cleared, so one another backend set meanwhile stays.
            store.clearExpired(playerId, now);
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

    /**
     * Check one proof of {@code playerId} against the budget: reserve the attempt, run {@code proof} (true when the
     * proof is right), and count the answer. A player has one proof in flight on this server at a time; another is
     * {@link Verdict#BUSY} and not checked. When {@code proof} throws, the reserved attempt is given back, since nothing
     * was proved either way, and the failure is passed on. Does I/O: run it off the tick.
     */
    public Checked check(UUID playerId, Instant now, BooleanSupplier proof) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(proof, "proof");
        if (!checking.add(playerId)) {
            return Checked.BUSY;
        }
        try {
            Attempt attempt = begin(playerId, now);
            if (attempt.refused()) {
                return Checked.LOCKED_OUT;
            }
            boolean right;
            try {
                right = proof.getAsBoolean();
            } catch (RuntimeException failure) {
                giveBack(playerId, failure);
                throw failure;
            }
            if (!right) {
                Outcome outcome = failed(playerId, attempt, now);
                return outcome.lockedOut() ? Checked.LOCKED_OUT : new Checked(Verdict.WRONG, outcome.remaining());
            }
            return succeeded(playerId, now) ? Checked.PASSED : Checked.LOCKED_OUT;
        } finally {
            checking.remove(playerId);
        }
    }

    /** Give back the attempt reserved for a proof that could not be checked; a failure here rides on {@code cause}. */
    private void giveBack(UUID playerId, RuntimeException cause) {
        try {
            store.refundFailure(playerId);
        } catch (RuntimeException refundFailed) {
            cause.addSuppressed(refundFailed);
        }
    }

    /**
     * Reserve one attempt for {@code playerId} before their proof is checked. The attempt counts as a failure from
     * now on; it is refused, and the proof must not be checked, while the account is locked out or once the budget is
     * spent. Call {@link #succeeded} or {@link #failed} once the proof is checked.
     */
    public Attempt begin(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        if (isLockedOut(playerId, now)) {
            return Attempt.denied();
        }
        int count = store.addFailure(playerId);
        if (count > policy.maxAttempts()) {
            // Attempts in parallel spent the budget; the one that spent it locks, and this one is not checked.
            lock(playerId, now);
            return Attempt.denied();
        }
        if (lockedNow(playerId, now)) {
            // A parallel attempt locked the account between the first look and this one's count.
            return Attempt.denied();
        }
        return new Attempt(false, count);
    }

    /**
     * The reserved attempt proved the factor: the count starts again from zero. False when a lockout was set meanwhile
     * by an attempt made in parallel, in which case the caller must not let the player through.
     */
    public boolean succeeded(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        store.clearFailures(playerId);
        return !isLockedOut(playerId, now);
    }

    /** The reserved attempt was wrong; it stays counted, and locks the account when it was the last one allowed. */
    public Outcome failed(UUID playerId, Attempt attempt, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(now, "now");
        if (attempt.refused() || policy.evaluate(attempt.count()) == LockoutPolicy.AttemptOutcome.LOCKED_OUT) {
            lock(playerId, now);
            return Outcome.locked();
        }
        if (lockedNow(playerId, now)) {
            // Locked meanwhile by an attempt made in parallel: answered as a pass would be, so the two tell nothing.
            return Outcome.locked();
        }
        return Outcome.retry(policy.remaining(attempt.count()));
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

    /** Whether a lockout is active in the store right now; unlike {@link #isLockedOut} it clears nothing. */
    private boolean lockedNow(UUID playerId, Instant now) {
        Optional<Instant> until = store.lockedUntil(playerId);
        if (until.isPresent() && now.isBefore(until.get())) {
            known.put(playerId, until.get());
            return true;
        }
        return false;
    }

    /** The budget rolls into the timed lockout; the store starts the count again so the window governs re-entry. */
    private void lock(UUID playerId, Instant now) {
        Instant until = now.plus(lockout);
        store.lock(playerId, until);
        known.put(playerId, until);
        // A locked-out account carries no proof to another backend either.
        revokeSession.accept(playerId);
    }

    /**
     * One reserved attempt: refused (do not check the proof) or allowed, with the failure count it holds.
     *
     * @param refused whether the account is locked out or the budget was spent, so the proof is not to be checked
     * @param count the failures counted with this one, zero when refused
     */
    public record Attempt(boolean refused, int count) {

        static Attempt denied() {
            return new Attempt(true, 0);
        }
    }

    /** What {@link #check} found. */
    public enum Verdict {
        /** The proof was right. */
        PASSED,
        /** The proof was wrong; attempts remain. */
        WRONG,
        /** The account is locked out, now or already; the proof counts for nothing. */
        LOCKED_OUT,
        /** Another proof of this player is being checked here; this one was not. */
        BUSY
    }

    /**
     * The result of {@link #check}.
     *
     * @param verdict what the check found
     * @param remaining the attempts left after a {@link Verdict#WRONG} proof, zero otherwise
     */
    public record Checked(Verdict verdict, int remaining) {

        public static final Checked PASSED = new Checked(Verdict.PASSED, 0);
        public static final Checked LOCKED_OUT = new Checked(Verdict.LOCKED_OUT, 0);
        public static final Checked BUSY = new Checked(Verdict.BUSY, 0);

        public Checked {
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    /** The result of {@link #failed}: either locked out now, or still allowed with the attempts remaining. */
    public record Outcome(boolean lockedOut, int remaining) {

        static Outcome locked() {
            return new Outcome(true, 0);
        }

        static Outcome retry(int remaining) {
            return new Outcome(false, remaining);
        }
    }
}
