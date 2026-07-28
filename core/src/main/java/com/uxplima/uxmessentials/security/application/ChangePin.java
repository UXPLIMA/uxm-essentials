package com.uxplima.uxmessentials.security.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.PinPolicy;

/**
 * Replace a player's PIN ({@code /pin change <old> <new>}): prove the current PIN, validate the replacement against
 * the {@link PinPolicy}, and only then store it. This is the only way to change a PIN that is already set, which is
 * what makes {@link SetPin} safe to leave unproven: setting a first PIN protects nothing yet, but overwriting a live
 * one hands the account's second factor to whoever typed the command.
 *
 * <p>The old PIN is checked before the new one is validated, so a wrong current PIN never reveals whether the
 * replacement would have been accepted. Like every other proof surface, this shares the {@link AttemptLimiter}: a
 * locked-out account is refused and each wrong PIN spends from the same durable, account-scoped budget.
 */
public final class ChangePin {

    private final TwoFactorRepository repository;
    private final AttemptLimiter limiter;
    private final PinPolicy pinPolicy;

    public ChangePin(TwoFactorRepository repository, AttemptLimiter limiter, PinPolicy pinPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.pinPolicy = Objects.requireNonNull(pinPolicy, "pinPolicy");
    }

    /** Replace the player's PIN with {@code newPin} when {@code oldPin} proves the current one at {@code now}. */
    public PinChangeResult change(UUID playerId, String oldPin, String newPin, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(oldPin, "oldPin");
        Objects.requireNonNull(newPin, "newPin");
        Objects.requireNonNull(now, "now");
        if (limiter.isLockedOut(playerId, now)) {
            return PinChangeResult.LOCKED_OUT;
        }
        TwoFactorRegistration registration = repository.find(playerId).orElse(null);
        if (registration == null || !registration.pinSet()) {
            return PinChangeResult.NOT_SET;
        }
        if (!repository.verifyPin(playerId, oldPin)) {
            return limiter.recordFailure(playerId, now).lockedOut()
                    ? PinChangeResult.LOCKED_OUT
                    : PinChangeResult.INVALID_PIN;
        }
        limiter.recordSuccess(playerId);
        return switch (pinPolicy.validate(newPin)) {
            case OK -> {
                repository.setPin(playerId, newPin);
                yield PinChangeResult.CHANGED;
            }
            case TOO_SHORT -> PinChangeResult.TOO_SHORT;
            case TOO_LONG -> PinChangeResult.TOO_LONG;
            case NOT_NUMERIC -> PinChangeResult.NOT_NUMERIC;
            case BLOCKED -> PinChangeResult.BLOCKED;
        };
    }
}
