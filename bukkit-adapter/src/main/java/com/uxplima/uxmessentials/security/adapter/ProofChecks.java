package com.uxplima.uxmessentials.security.adapter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.VerifyResult;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import org.jspecify.annotations.NullMarked;

/**
 * Checks a submitted PIN or code against the shared attempt budget: the attempt is reserved before the proof is
 * checked, and a player has one check in flight at a time, so several submissions sent at once cannot check more
 * proofs than the budget allows. A proof that checks out while a parallel attempt locked the account counts as the
 * lockout, not as a pass.
 */
@NullMarked
final class ProofChecks {

    private final Prover verify;
    private final AttemptLimiter limiter;
    private final Clock clock;
    private final Set<UUID> checking = ConcurrentHashMap.newKeySet();

    ProofChecks(VerifyTwoFactor verify, AttemptLimiter limiter, Clock clock) {
        this(Objects.requireNonNull(verify, "verify")::verify, limiter, clock);
    }

    ProofChecks(Prover verify, AttemptLimiter limiter, Clock clock) {
        this.verify = Objects.requireNonNull(verify, "verify");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Claim the one check {@code player} may have in flight; false while another is still being checked. */
    boolean claim(UUID player) {
        return checking.add(Objects.requireNonNull(player, "player"));
    }

    /**
     * Check {@code candidate} for {@code player}, who claimed the check, and release the claim. Does database work:
     * run it off the tick.
     */
    Checked check(UUID player, String candidate) {
        try {
            Instant now = clock.instant();
            AttemptLimiter.Attempt attempt = limiter.begin(player, now);
            if (attempt.refused()) {
                return Checked.LOCKED_OUT;
            }
            VerifyResult result = verify.verify(player, candidate, now);
            if (result == VerifyResult.INVALID) {
                AttemptLimiter.Outcome outcome = limiter.failed(player, attempt, now);
                return new Checked(VerifyResult.INVALID, outcome);
            }
            return limiter.succeeded(player, now)
                    ? new Checked(result, new AttemptLimiter.Outcome(false, 0))
                    : Checked.LOCKED_OUT;
        } finally {
            checking.remove(player);
        }
    }

    /** Forget a claim whose check never ran (the scheduler refused it). */
    void release(UUID player) {
        checking.remove(Objects.requireNonNull(player, "player"));
    }

    /** What checks a proof: the second factor itself. */
    @FunctionalInterface
    interface Prover {

        VerifyResult verify(UUID player, String candidate, Instant now);
    }

    /**
     * What one check found.
     *
     * @param result what the proof showed; {@link VerifyResult#INVALID} for a check refused by a lockout
     * @param outcome the budget after it: locked out, or the attempts left
     */
    record Checked(VerifyResult result, AttemptLimiter.Outcome outcome) {

        static final Checked LOCKED_OUT = new Checked(VerifyResult.INVALID, new AttemptLimiter.Outcome(true, 0));
    }
}
