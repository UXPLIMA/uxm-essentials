package com.uxplima.uxmessentials.security.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;

/**
 * Remove a player's PIN factor ({@code /pin remove <pin>}), but only after they prove the PIN they are removing. The
 * mirror of {@link DisableTotp}, and deliberately just as narrow: the current PIN is the only accepted proof, and an
 * authenticator factor the player also holds is left alone. Neither factor can be used to strip the other.
 *
 * <p>A correct guess removes a protection, so this shares the {@link AttemptLimiter} with the join freeze, the op
 * re-auth and {@code /2fa disable}: a locked-out account is refused outright and every wrong PIN counts against the
 * same durable, account-scoped budget. Without that, {@code /pin remove} would be an unmetered oracle for guessing a
 * four-digit PIN.
 */
public final class RemovePin {

    private final TwoFactorRepository repository;
    private final AttemptLimiter limiter;

    public RemovePin(TwoFactorRepository repository, AttemptLimiter limiter) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    /** Remove the player's PIN if {@code pin} proves it at {@code now}, leaving any authenticator factor untouched. */
    public PinRemoveResult remove(UUID playerId, String pin, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(now, "now");
        if (limiter.isLockedOut(playerId, now)) {
            return PinRemoveResult.LOCKED_OUT;
        }
        TwoFactorRegistration registration = repository.find(playerId).orElse(null);
        if (registration == null || !registration.pinSet()) {
            return PinRemoveResult.NOT_SET;
        }
        if (!repository.verifyPin(playerId, pin)) {
            return limiter.recordFailure(playerId, now).lockedOut()
                    ? PinRemoveResult.LOCKED_OUT
                    : PinRemoveResult.INVALID_PIN;
        }
        limiter.recordSuccess(playerId);
        repository.clearPin(playerId);
        return PinRemoveResult.REMOVED;
    }
}
