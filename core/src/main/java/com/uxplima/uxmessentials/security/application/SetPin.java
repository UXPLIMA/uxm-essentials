package com.uxplima.uxmessentials.security.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.PinPolicy;

/**
 * Set a player's PIN factor ({@code /pin set <pin>}): validate the plaintext against the {@link PinPolicy}, and only
 * if it passes hand it to the repository to be hashed and stored. The use case never sees the stored hash and holds
 * the plaintext for exactly as long as the policy check and the hand-off take; the one-way hashing happens at the
 * store boundary. A refusal returns the typed reason so the command can tell the player what to fix.
 */
public final class SetPin {

    private final TwoFactorRepository repository;
    private final PinPolicy pinPolicy;

    public SetPin(TwoFactorRepository repository, PinPolicy pinPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pinPolicy = Objects.requireNonNull(pinPolicy, "pinPolicy");
    }

    /** Validate and, if it passes the policy, hash-and-store {@code rawPin} as the player's PIN factor. */
    public PinSetResult set(UUID playerId, String rawPin) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rawPin, "rawPin");
        return switch (pinPolicy.validate(rawPin)) {
            case OK -> {
                repository.setPin(playerId, rawPin);
                yield PinSetResult.SET;
            }
            case TOO_SHORT -> PinSetResult.TOO_SHORT;
            case TOO_LONG -> PinSetResult.TOO_LONG;
            case NOT_NUMERIC -> PinSetResult.NOT_NUMERIC;
        };
    }
}
