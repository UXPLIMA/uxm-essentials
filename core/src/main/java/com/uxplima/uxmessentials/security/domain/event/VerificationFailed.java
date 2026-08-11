package com.uxplima.uxmessentials.security.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A submitted value did not prove anything, and the account still has attempts left.
 *
 * <p>The attempt that spends the last one publishes {@link AccountLockedOut} instead, so the two never both fire
 * for one submission.
 *
 * @param player the player who submitted it
 * @param remainingAttempts how many tries are left before the account is locked out
 */
public record VerificationFailed(PlayerRef player, int remainingAttempts) implements SecurityEvent {

    public VerificationFailed {
        Objects.requireNonNull(player, "player");
        if (remainingAttempts < 0) {
            throw new IllegalArgumentException("remainingAttempts must not be negative: " + remainingAttempts);
        }
    }
}
