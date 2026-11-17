package com.uxplima.uxmessentials.security.adapter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.VerifyResult;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Checks a PIN or code submitted at a keypad against the shared attempt budget through
 * {@link AttemptLimiter#check}: the attempt is reserved before the proof is checked, a player has one check in flight
 * at a time, and a proof that checks out while a parallel attempt locked the account counts as the lockout, not as a
 * pass. A check that fails for want of the database or the key file gives its attempt back and says so, so an outage
 * never spends an honest player's budget.
 */
@NullMarked
final class ProofChecks {

    private final Prover verify;
    private final AttemptLimiter limiter;
    private final Logger log;
    private final Clock clock;

    ProofChecks(VerifyTwoFactor verify, AttemptLimiter limiter, Logger log, Clock clock) {
        this(
                player -> {
                    VerifyTwoFactor.Prepared prepared = verify.prepare(player);
                    return prepared::verify;
                },
                limiter,
                log,
                clock);
    }

    ProofChecks(Prover verify, AttemptLimiter limiter, Logger log, Clock clock) {
        this.verify = Objects.requireNonNull(verify, "verify");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Check {@code candidate} for {@code player}. Does database work: run it off the tick. */
    Checked check(UUID player, String candidate) {
        Instant now = clock.instant();
        VerifyResult[] shown = {VerifyResult.INVALID};
        AttemptLimiter.Checked checked;
        try {
            checked = limiter.check(player, now, () -> {
                Prover.Check check = verify.prepare(player);
                return () -> {
                    try {
                        VerifyResult result = check.verify(candidate, now);
                        shown[0] = result;
                        return result != VerifyResult.INVALID;
                    } catch (RuntimeException failure) {
                        // After a comparison: counted as not proved, never told apart from a wrong proof.
                        log.error("event=security_proof_unrecorded player=" + player, failure);
                        throw failure;
                    }
                };
            });
        } catch (RuntimeException failure) {
            log.error("event=security_proof_unchecked player=" + player, failure);
            return Checked.UNAVAILABLE;
        }
        return switch (checked.verdict()) {
            case PASSED -> new Checked(Status.PASSED, shown[0], 0);
            case WRONG -> new Checked(Status.WRONG, VerifyResult.INVALID, checked.remaining());
            case LOCKED_NOW -> Checked.LOCKED_NOW;
            case ALREADY_LOCKED -> Checked.ALREADY_LOCKED;
            case BUSY -> Checked.BUSY;
        };
    }

    /** What checks a proof: the second factor itself, read first and then compared. */
    @FunctionalInterface
    interface Prover {

        /** Read the player's factors; may fail, before any candidate is looked at. */
        Check prepare(UUID player);

        /** The comparison of one candidate against the factors read. */
        @FunctionalInterface
        interface Check {

            VerifyResult verify(String candidate, Instant now);
        }
    }

    /** How one check ended. */
    enum Status {
        /** The proof was right, or there was nothing to prove. */
        PASSED,
        /** The proof was wrong; attempts remain. */
        WRONG,
        /** This proof locked the account out. */
        LOCKED_NOW,
        /** The account was already locked out. */
        ALREADY_LOCKED,
        /** Another proof of the player's was being checked; this one was not. */
        BUSY,
        /** The proof could not be checked; its attempt was given back. */
        UNAVAILABLE
    }

    /**
     * What one check found.
     *
     * @param status how it ended
     * @param result what a passed proof showed: {@link VerifyResult#SUCCESS}, or {@link VerifyResult#NOT_ENROLLED} for
     *     a player with nothing to prove; {@link VerifyResult#INVALID} otherwise
     * @param remaining the attempts left after a wrong proof
     */
    record Checked(Status status, VerifyResult result, int remaining) {

        static final Checked LOCKED_NOW = new Checked(Status.LOCKED_NOW, VerifyResult.INVALID, 0);
        static final Checked ALREADY_LOCKED = new Checked(Status.ALREADY_LOCKED, VerifyResult.INVALID, 0);
        static final Checked BUSY = new Checked(Status.BUSY, VerifyResult.INVALID, 0);
        static final Checked UNAVAILABLE = new Checked(Status.UNAVAILABLE, VerifyResult.INVALID, 0);

        Checked {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(result, "result");
        }
    }
}
