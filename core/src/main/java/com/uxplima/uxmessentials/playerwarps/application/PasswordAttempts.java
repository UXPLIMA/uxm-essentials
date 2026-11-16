package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import org.jspecify.annotations.Nullable;

/**
 * How many wrong passwords one player may try on one player warp before they must wait. Up to {@code maxAttempts}
 * wrong passwords within {@code lockout} of the first are allowed; the one that reaches the limit locks the player
 * out of that warp's password for {@code lockout}. A right password, or a window that passes without reaching the
 * limit, starts the count again.
 *
 * <p>No permission tier sizes or lifts this limit, so a tier granted for some other wait can never remove it by
 * accident: only the password bypass, which skips the password altogether, gets past it. A {@code lockout} of zero
 * turns the limit off, which an operator can only do on purpose in the config.
 *
 * <p>The count lives in this server's memory: it starts again on a restart, and each server of a network counts the
 * attempts typed on it.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Uses of a warp run on the async lane, so each player and warp's count is
 * updated through {@link ConcurrentHashMap#compute}, one update at a time.
 */
public final class PasswordAttempts {

    /** The shipped limit: three wrong passwords within a minute lock the warp's password for a minute. */
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

    /** How long {@code player} must still wait before trying {@code warp}'s password again, if they must. */
    public Optional<Duration> lockedFor(UUID player, PlayerWarpId warp) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warp, "warp");
        Count count = counts.get(new Key(player, warp));
        @Nullable Instant lockedUntil = count == null ? null : count.lockedUntil();
        Instant now = clock.instant();
        if (lockedUntil == null || !now.isBefore(lockedUntil)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, lockedUntil));
    }

    /** Count a wrong password {@code player} typed for {@code warp}. */
    public void failed(UUID player, PlayerWarpId warp) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warp, "warp");
        if (lockout.isZero()) {
            return;
        }
        Instant now = clock.instant();
        sweepIfLarge(now);
        counts.compute(new Key(player, warp), (key, count) -> {
            Count running = count == null || count.endedBy(now, lockout) ? new Count(0, now, null) : count;
            int failures = running.failures() + 1;
            @Nullable Instant lockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;
            return new Count(failures, running.since(), lockedUntil);
        });
    }

    /** Forget {@code player}'s wrong passwords for {@code warp}, after they typed the right one. */
    public void succeeded(UUID player, PlayerWarpId warp) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warp, "warp");
        counts.remove(new Key(player, warp));
    }

    private void sweepIfLarge(Instant now) {
        if (counts.size() > SWEEP_ABOVE) {
            counts.values().removeIf(count -> count.endedBy(now, lockout));
        }
    }

    private record Key(UUID player, PlayerWarpId warp) {}

    /**
     * The wrong passwords counted since {@code since}, and when the lockout they earned ends, if they earned one.
     */
    private record Count(
            int failures, Instant since, @Nullable Instant lockedUntil) {

        /** Whether this count no longer holds anything back: its window and any lockout are over. */
        boolean endedBy(Instant now, Duration window) {
            Instant until = lockedUntil;
            if (until != null) {
                return !now.isBefore(until);
            }
            return !now.isBefore(since.plus(window));
        }
    }
}
