package com.uxplima.uxmessentials.security.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.PinPolicy;

/**
 * Set a player's <b>first</b> PIN factor ({@code /pin set <pin>}): validate the plaintext against the
 * {@link PinPolicy}, and only if it passes hand it to the repository to be hashed and stored. The use case never sees
 * the stored hash and holds the plaintext for exactly as long as the policy check and the hand-off take; the one-way
 * hashing happens at the store boundary. A refusal returns the typed reason so the command can tell the player what
 * to fix.
 *
 * <p>A player who already holds a PIN is refused here and sent to {@link ChangePin}. Setting a first PIN protects
 * nothing yet, so it needs no proof; silently overwriting a live one would let anyone at an unlocked session take
 * over the account's second factor, which is exactly what the factor exists to prevent.
 */
public final class SetPin {

    private final TwoFactorRepository repository;
    private final PinPolicy pinPolicy;

    public SetPin(TwoFactorRepository repository, PinPolicy pinPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pinPolicy = Objects.requireNonNull(pinPolicy, "pinPolicy");
    }

    /** Validate and, if it passes the policy and no PIN is set yet, hash-and-store {@code rawPin} as the PIN factor. */
    public PinSetResult set(UUID playerId, String rawPin) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rawPin, "rawPin");
        if (repository.find(playerId).map(TwoFactorRegistration::pinSet).orElse(false)) {
            return PinSetResult.ALREADY_SET;
        }
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
