package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

/**
 * How many passwords one player may try on one player warp before they must wait. Up to {@code maxAttempts} tries
 * within {@code lockout} of the first are allowed; the try that reaches the limit locks the player out of that warp's
 * password for {@code lockout}. A right password, or a window that passes without reaching the limit, starts the count
 * again.
 *
 * <p>A try is counted before its password is checked, in one atomic step that also refuses a locked player, so tries
 * sent at the same moment cannot all slip through while the slow hash check runs. A try that turns out right gives its
 * count back through {@link #succeeded}.
 *
 * <p>No permission tier sizes or lifts this limit, so a tier granted for some other wait can never remove it by
 * accident: only the password bypass, which skips the password altogether, gets past it. A {@code lockout} of zero
 * turns the limit off, which an operator can only do on purpose in the config.
 *
 * <p>The count is keyed by the warp's {@code stableKey}, never by an id a later warp may reuse, and lives in this
 * server's memory: it starts again on a restart, and each server of a network counts the tries typed on it.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Uses of a warp run on the async lane, so each player and warp's count is
 * updated through {@link ConcurrentHashMap#compute}, one update at a time.
 */
public final class PasswordAttempts {

    /** The shipped limit: three tries within a minute lock the warp's password for a minute. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** The shipped window and lockout. */
    public static final Duration DEFAULT_LOCKOUT = Duration.ofSeconds(60);

    /** How many counts may be kept before the ones that ended are swept out. */
    private static final int SWEEP_ABOVE = 1024;

    private final int maxAttempts;
    private final Duration lockout;
    private final Clock clock;
    private final ConcurrentHashMap<Key, Count> counts = new ConcurrentHashMap<>();

    public PasswordAttempts(int maxAttempts, Duration lockout, Clock clock) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("at least one password attempt must be allowed: " + maxAttempts);
        }
        Objects.requireNonNull(lockout, "lockout");
        if (lockout.isNegative()) {
            throw new IllegalArgumentException("the password lockout must not be negative: " + lockout);
        }
        this.maxAttempts = maxAttempts;
        this.lockout = lockout;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The shipped limit, on {@code clock}. */
    public static PasswordAttempts defaults(Clock clock) {
        return new PasswordAttempts(DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCKOUT, clock);
    }

    /**
     * Count one try {@code player} is about to make on the warp {@code warpKey} names, before the password is checked.
     * Empty when the try may go ahead; otherwise how long the player is still locked out, and nothing is counted.
     */
    public Optional<Duration> reserve(UUID player, String warpKey) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warpKey, "warpKey");
        if (lockout.isZero()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        sweepIfLarge(now);
        AtomicReference<@Nullable Duration> locked = new AtomicReference<>();
        counts.compute(new Key(player, warpKey), (key, count) -> {
            @Nullable Instant until = count == null ? null : count.lockedUntil();
            if (count != null && until != null && now.isBefore(until)) {
                locked.set(Duration.between(now, until));
                return count;
            }
            Count running = count == null || count.endedBy(now, lockout) ? new Count(0, now, null) : count;
            int tries = running.tries() + 1;
            return new Count(tries, running.since(), tries >= maxAttempts ? now.plus(lockout) : null);
        });
        return Optional.ofNullable(locked.get());
    }

    /** Forget {@code player}'s tries on the warp {@code warpKey} names, after they typed the right password. */
    public void succeeded(UUID player, String warpKey) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warpKey, "warpKey");
        counts.remove(new Key(player, warpKey));
    }

    private void sweepIfLarge(Instant now) {
        if (counts.size() > SWEEP_ABOVE) {
            counts.values().removeIf(count -> count.endedBy(now, lockout));
        }
    }

    private record Key(UUID player, String warp) {}

    /** The tries counted since {@code since}, and when the lockout they earned ends, if they earned one. */
    private record Count(int tries, Instant since, @Nullable Instant lockedUntil) {

        /** Whether this count no longer holds anything back: its window and any lockout are over. */
        boolean endedBy(Instant now, Duration window) {
            @Nullable Instant until = lockedUntil;
            if (until != null) {
                return !now.isBefore(until);
            }
            return !now.isBefore(since.plus(window));
        }
    }
}
